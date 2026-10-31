package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldDefinitionRequest;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.customFields.exception.DuplicateCustomFieldKeyException;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import com.blusimian.blusimian.roles.dto.RoleRequest;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static com.blusimian.blusimian.roles.RoleTestSupport.creating;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D12 end to end: a role with Create can always fill in every required field. */
@SpringBootTest
class RequiredFieldRuleIntegrationTest {

    private static final EntityPermissions CREATING_CONTACTS = creating("firstName", "lastName");
    private static final FieldAccess OPEN = new FieldAccess(true, true);
    private static final FieldAccess CLOSED = new FieldAccess(false, false);

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleService roles;
    @Autowired
    CustomFieldService customFields;
    @Autowired
    EntityPermissionRepository permissions;
    @Autowired
    TransactionTemplate transaction;

    TenantId tenant;

    @BeforeEach
    void provisionTenantForTest() {
        tenant = provisionTenant(provisioning);
    }

    private CustomFieldDefinition contactField(String key, boolean required) {
        return callAs(tenant, () -> customFields.create(EntityType.CONTACT,
                new CustomFieldDefinitionRequest(key, key, FieldDataType.TEXT, required, 0)));
    }

    private RoleResponse createRole(RoleRequest request) {
        return callAs(tenant, () -> roles.create(request));
    }

    private FieldAccess access(RoleResponse role, String key) {
        return callAs(tenant, () -> roles.get(role.id())).entities().get(EntityType.CONTACT).fields().get(key);
    }

    @Test
    void aRequiredFieldReachesRolesWithCreateAndNoOthers() {
        RoleResponse creator = createRole(role("Creator", Map.of(EntityType.CONTACT, CREATING_CONTACTS)));
        RoleResponse reader = createRole(role("Reader", Map.of(EntityType.CONTACT,
                new EntityPermissions(true, false, false, false, Map.of()))));
        RoleResponse creatorOfDeals = createRole(role("Deals", Map.of(EntityType.DEAL, creating("dealName"))));

        contactField("vat", true);

        assertThat(access(creator, "vat")).isEqualTo(OPEN);
        assertThat(access(reader, "vat")).isEqualTo(CLOSED);
        assertThat(access(creatorOfDeals, "vat")).isEqualTo(CLOSED);
    }

    @Test
    void anOptionalFieldReachesNobody() {
        RoleResponse creator = createRole(role("Creator", Map.of(EntityType.CONTACT, CREATING_CONTACTS)));

        contactField("nickname", false);

        assertThat(access(creator, "nickname")).isEqualTo(CLOSED);
    }

    @Test
    void aFailedCreateRollsTheGrantBack() {
        RoleResponse creator = createRole(role("Creator", Map.of(EntityType.CONTACT, CREATING_CONTACTS)));
        CustomFieldDefinition optional = contactField("existing", false);

        // the same transaction: the listener's update, then the key clash that undoes it all
        assertThatThrownBy(() -> runAs(tenant, () -> transaction.executeWithoutResult(status -> {
            customFields.create(EntityType.CONTACT,
                    new CustomFieldDefinitionRequest("vat", "vat", FieldDataType.TEXT, true, 0));
            customFields.create(EntityType.CONTACT,
                    new CustomFieldDefinitionRequest("existing", "x", FieldDataType.TEXT, false, 0));
        }))).isInstanceOf(DuplicateCustomFieldKeyException.class);

        // the slot the failed field took: the next one to land there must start closed
        assertThat(optional.getColumnName()).isEqualTo("cf_text_01");
        Map<String, Object> row = callAs(tenant, () -> transaction.execute(status ->
                permissions.find(EntityType.CONTACT, creator.id())));
        assertThat(row.get("cf_text_02_read")).isEqualTo(false);
    }

    @Test
    void aStalePutKeepsTheGrantedBits() {
        RoleResponse creator = createRole(role("Creator", Map.of(EntityType.CONTACT, CREATING_CONTACTS)));
        // the editor's document, from before the field
        RoleRequest stale = role("Renamed", Map.of(EntityType.CONTACT, CREATING_CONTACTS));

        contactField("vat", true);
        callAs(tenant, () -> roles.replace(creator.id(), stale));

        assertThat(access(creator, "vat")).isEqualTo(OPEN);
    }

    @Test
    void aStalePutCantTurnCreateOnForARoleThatMissedTheGrant() {
        RoleResponse reader = createRole(role("Reader", Map.of(EntityType.CONTACT,
                new EntityPermissions(true, false, false, false, Map.of()))));

        contactField("vat", true);

        RoleRequest stale = role("Reader", Map.of(EntityType.CONTACT, CREATING_CONTACTS));
        assertThatThrownBy(() -> callAs(tenant, () -> roles.replace(reader.id(), stale)))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.details()).singleElement().satisfies(detail -> {
                            assertThat(detail.field()).isEqualTo("entities.CONTACT.fields.vat");
                            assertThat(detail.reason()).contains("Required field");
                        }));
        assertThat(callAs(tenant, () -> roles.get(reader.id())).entities().get(EntityType.CONTACT).canCreate()).isFalse();
    }

    @Test
    void aRequiredFieldCantBeClosedWhileCreateIsOn() {
        contactField("vat", true);
        RoleResponse creator = createRole(role("Creator", Map.of(EntityType.CONTACT,
                creating("firstName", "lastName", "vat"))));

        EntityPermissions closing = new EntityPermissions(true, true, false, false, Map.of("vat", CLOSED));
        assertThatThrownBy(() -> callAs(tenant, () ->
                roles.replace(creator.id(), role("Creator", Map.of(EntityType.CONTACT, closing)))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anArchivedRequiredFieldStopsBeingDemanded() {
        CustomFieldDefinition field = contactField("vat", true);
        runAs(tenant, () -> customFields.archive(field.getId()));

        assertThat(createRole(role("Creator", Map.of(EntityType.CONTACT, CREATING_CONTACTS))).id()).isNotNull();
    }

    @Test
    void aConcurrentRoleSaveAndFieldCreateLeaveTheRuleIntact() throws Exception {
        for (int round = 0; round < 8; round++) {
            String key = "vat_" + round;
            UUID roleId = createRole(role("Round " + round)).id();
            RoleRequest turnCreateOn = role("Round " + round, Map.of(EntityType.CONTACT, CREATING_CONTACTS));

            CountDownLatch start = new CountDownLatch(2);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> save = executor.submit(() -> {
                    start.countDown();
                    start.await();
                    return callAs(tenant, () -> roles.replace(roleId, turnCreateOn));
                });
                Future<?> field = executor.submit(() -> {
                    start.countDown();
                    start.await();
                    return contactField(key, true);
                });
                field.get();
                try {
                    save.get();
                } catch (ExecutionException e) {
                    // the field won: the save's stale document is refused, not half-applied
                    assertThat(e.getCause()).isInstanceOf(BadRequestException.class);
                }
            }

            // Either order is fine; a role that holds Create without the field is not.
            EntityPermissions contact = callAs(tenant, () -> roles.get(roleId)).entities().get(EntityType.CONTACT);
            if (contact.canCreate()) {
                assertThat(contact.fields().get(key)).isEqualTo(OPEN);
            }
        }
    }

    @Test
    void theLockCantBeTakenOutsideATransaction() {
        // released at once there, so it would protect nothing
        List<Throwable> caught = new ArrayList<>();
        try {
            runAs(tenant, () -> customFields.lockEntityFields(EntityType.CONTACT));
        } catch (RuntimeException e) {
            caught.add(e);
        }
        assertThat(caught).singleElement()
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
