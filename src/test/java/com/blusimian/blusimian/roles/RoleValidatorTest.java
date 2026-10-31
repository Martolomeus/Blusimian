package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ApiError.FieldError;
import com.blusimian.blusimian.roles.dto.AppPermissions;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import com.blusimian.blusimian.roles.dto.RoleRequest;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.blusimian.blusimian.roles.RoleTestSupport.closed;
import static com.blusimian.blusimian.roles.RoleTestSupport.creating;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;

/** Each rule of the role document, on hand-made fields. */
class RoleValidatorTest {

    private static final PermissionField FIRST_NAME = PermissionField.of("firstName", true, "first_name");
    private static final PermissionField EMAIL = PermissionField.of("email", false, "email");
    private static final PermissionField LEAD_SOURCE = PermissionField.of("lead_source", true, "cf_text_03");

    private static final Map<EntityType, List<PermissionField>> FIELDS = Map.of(
            EntityType.CONTACT, List.of(FIRST_NAME, EMAIL, LEAD_SOURCE),
            EntityType.COMPANY, List.of(),
            EntityType.DEAL, List.of());

    private static List<FieldError> validate(RoleRequest request) {
        return RoleValidator.validate(request, FIELDS, Map.of());
    }

    private static RoleRequest contact(EntityPermissions contact) {
        return role("Sales", Map.of(EntityType.CONTACT, contact));
    }

    private static EntityPermissions reading(Map<String, FieldAccess> fields) {
        return new EntityPermissions(true, false, false, false, fields);
    }

    @Test
    void aBlankRoleIsValid() {
        assertThat(validate(role("Sales"))).isEmpty();
    }

    @Test
    void theNameIsRequiredAndCappedAsItWillBeStored() {
        assertThat(validate(role(null))).containsExactly(new FieldError("name", "Required"));
        assertThat(validate(role("   "))).containsExactly(new FieldError("name", "Required"));
        assertThat(validate(role("  " + "a".repeat(100) + "  "))).isEmpty();
        assertThat(validate(role("a".repeat(101))))
                .containsExactly(new FieldError("name", "At most 100 characters"));
    }

    @Test
    void everyEntityTypeMustBePresent() {
        Map<EntityType, EntityPermissions> entities = new EnumMap<>(EntityType.class);
        entities.put(EntityType.COMPANY, closed());
        entities.put(EntityType.DEAL, closed());

        assertThat(validate(new RoleRequest("Sales", AppPermissions.NONE, entities)))
                .containsExactly(new FieldError("entities", "Missing CONTACT"));
    }

    @Test
    void createEditAndDeleteNeedRead() {
        for (EntityPermissions permissions : List.of(
                new EntityPermissions(false, false, true, false, Map.of()),
                new EntityPermissions(false, false, false, true, Map.of()))) {
            assertThat(validate(contact(permissions)))
                    .containsExactly(new FieldError("entities.CONTACT.canRead", "Required by Create, Edit and Delete"));
        }
    }

    @Test
    void aKeyMustBeAFieldOfTheEntityInAnyCase() {
        assertThat(validate(contact(reading(Map.of("EMAIL", new FieldAccess(true, true)))))).isEmpty();
        assertThat(validate(contact(reading(Map.of("foo", new FieldAccess(true, false))))))
                .containsExactly(new FieldError("entities.CONTACT.fields.foo", "Unknown field 'foo'"));
    }

    @Test
    void editNeedsRead() {
        assertThat(validate(contact(reading(Map.of("email", new FieldAccess(false, true))))))
                .containsExactly(new FieldError("entities.CONTACT.fields.email", "Edit requires Read"));
    }

    @Test
    void withCreateARequiredFieldNeedsReadAndEdit() {
        assertThat(validate(contact(creating("firstName", "lead_source")))).isEmpty();
        assertThat(validate(contact(creating("firstName"))))
                .containsExactly(new FieldError("entities.CONTACT.fields.lead_source",
                        "Required field: needs Read and Edit while Create is on"));
    }

    @Test
    void withoutCreateARequiredFieldMayStayClosed() {
        assertThat(validate(contact(new EntityPermissions(true, false, true, true, Map.of())))).isEmpty();
    }

    @Test
    void theRequiredRuleLooksAtTheMergedResultNotTheRequest() {
        Map<EntityType, Map<String, Object>> stored = Map.of(EntityType.CONTACT, Map.of(
                "first_name_read", true, "first_name_edit", true,
                "cf_text_03_read", true, "cf_text_03_edit", true));

        // the document leaves both required fields out; their stored bits satisfy the rule
        assertThat(RoleValidator.validate(contact(creating()), FIELDS, stored)).isEmpty();
        // nothing stored: the same document turns Create on without them
        assertThat(RoleValidator.validate(contact(creating()), FIELDS, Map.of()))
                .extracting(FieldError::field)
                .containsExactly("entities.CONTACT.fields.firstName", "entities.CONTACT.fields.lead_source");
    }

    @Test
    void aRequestCanCloseWhatIsStored() {
        Map<EntityType, Map<String, Object>> stored = Map.of(EntityType.CONTACT, Map.of(
                "first_name_read", true, "first_name_edit", true,
                "cf_text_03_read", true, "cf_text_03_edit", true));
        EntityPermissions closing = new EntityPermissions(true, true, false, false,
                Map.of("firstName", new FieldAccess(false, false)));

        assertThat(RoleValidator.validate(contact(closing), FIELDS, stored))
                .extracting(FieldError::field).containsExactly("entities.CONTACT.fields.firstName");
    }

    @Test
    void everyProblemIsReportedTogether() {
        EntityPermissions contact = new EntityPermissions(false, true, false, false,
                Map.of("foo", new FieldAccess(true, true), "email", new FieldAccess(false, true)));

        assertThat(validate(role(" ", Map.of(EntityType.CONTACT, contact)))).extracting(FieldError::field)
                .containsExactlyInAnyOrder("name", "entities.CONTACT.canRead", "entities.CONTACT.fields.foo",
                        "entities.CONTACT.fields.email", "entities.CONTACT.fields.firstName",
                        "entities.CONTACT.fields.lead_source");
    }
}
