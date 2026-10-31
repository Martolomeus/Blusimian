package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import com.blusimian.blusimian.roles.exception.LastAdministratorException;
import com.blusimian.blusimian.roles.exception.RoleInUseException;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import com.blusimian.blusimian.users.User;
import com.blusimian.blusimian.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static com.blusimian.blusimian.roles.RoleTestSupport.PASSWORD;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The last Administrator and held roles, against the dev database. */
@SpringBootTest
class RoleAssignmentIntegrationTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleAssignmentService assignments;
    @Autowired
    RoleService roles;
    @Autowired
    UserService users;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transaction;

    TenantId tenant;
    UUID administrator;
    UUID firstUser;

    @BeforeEach
    void provisionTenantForTest() {
        tenant = provisionTenant(provisioning);
        administrator = callAs(tenant, () -> roles.list()).stream()
                .filter(RoleSummaryResponse::isAdmin).findFirst().orElseThrow().id();
        firstUser = callAs(tenant, () -> users.list()).getFirst().getId();
    }

    private UUID newUser() {
        return callAs(tenant, () -> users.create("roles-" + UUID.randomUUID(), "Someone", PASSWORD)).getId();
    }

    private void assign(UUID userId, UUID roleId) {
        runAs(tenant, () -> assignments.assign(userId, roleId));
    }

    private void unassign(UUID userId, UUID roleId) {
        runAs(tenant, () -> assignments.unassign(userId, roleId));
    }

    private List<UUID> holdersOf(UUID roleId) {
        return callAs(tenant, () -> assignments.list()).stream()
                .filter(assignment -> assignment.getRoleId().equals(roleId))
                .map(RoleAssignment::getUserId)
                .toList();
    }

    private void disable(UUID userId) {
        runAs(tenant, () -> transaction.executeWithoutResult(status ->
                jdbc.sql("update app.users set enabled = false where id = ?").param(userId).update()));
    }

    @Test
    void removingTheLastAdministratorIsRefusedAndChangesNothing() {
        assertThatThrownBy(() -> unassign(firstUser, administrator))
                .isInstanceOf(LastAdministratorException.class)
                .hasMessage("At least one user must stay an Administrator.");

        assertThat(holdersOf(administrator)).containsExactly(firstUser);
    }

    @Test
    void removingOneOfTwoWorks() {
        UUID second = newUser();
        assign(second, administrator);

        unassign(firstUser, administrator);

        assertThat(holdersOf(administrator)).containsExactly(second);
    }

    @Test
    void aDisabledHolderDoesntCountAsTheOneLeft() {
        UUID disabled = newUser();
        assign(disabled, administrator);
        disable(disabled);

        assertThatThrownBy(() -> unassign(firstUser, administrator)).isInstanceOf(LastAdministratorException.class);
    }

    @Test
    void aDisabledHolderCanBeRemovedWhileAnEnabledOneRemains() {
        UUID disabled = newUser();
        assign(disabled, administrator);
        disable(disabled);

        unassign(disabled, administrator);

        assertThat(holdersOf(administrator)).containsExactly(firstUser);
    }

    @Test
    void unassigningAnAdministratorWhoDoesntHoldItIsANoOp() {
        // idempotent: the last-holder check is about a real removal
        assertThatCode(() -> unassign(newUser(), administrator)).doesNotThrowAnyException();

        assertThat(holdersOf(administrator)).containsExactly(firstUser);
    }

    @Test
    void twoConcurrentRemovalsLeaveOne() throws Exception {
        for (int round = 0; round < 8; round++) {
            UUID second = newUser();
            assign(second, administrator);
            UUID remaining = holdersOf(administrator).stream().filter(id -> !id.equals(second)).findFirst().orElseThrow();

            CountDownLatch start = new CountDownLatch(2);
            List<Throwable> failures = new ArrayList<>();
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                List<Future<?>> removals = new ArrayList<>();
                for (UUID userId : List.of(remaining, second)) {
                    removals.add(executor.submit(() -> {
                        start.countDown();
                        start.await();
                        unassign(userId, administrator);
                        return null;
                    }));
                }
                for (Future<?> removal : removals) {
                    try {
                        removal.get();
                    } catch (ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
            }

            assertThat(failures).singleElement().isInstanceOf(LastAdministratorException.class);
            assertThat(holdersOf(administrator)).hasSize(1);
        }
    }

    @Test
    void assigningAndUnassigningAreIdempotent() {
        UUID user = newUser();
        UUID standard = callAs(tenant, () -> roles.list()).stream()
                .filter(role -> !role.isAdmin()).findFirst().orElseThrow().id();

        assign(user, standard);
        assign(user, standard);
        assertThat(holdersOf(standard)).containsExactly(user);

        unassign(user, standard);
        unassign(user, standard);
        assertThat(holdersOf(standard)).isEmpty();
    }

    @Test
    void assigningTheSameRoleConcurrentlyLeavesOneRow() throws Exception {
        UUID user = newUser();
        UUID standard = callAs(tenant, () -> roles.create(role("Double click"))).id();

        CountDownLatch start = new CountDownLatch(4);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(executor.submit(() -> {
                    start.countDown();
                    start.await();
                    assign(user, standard);
                    return null;
                }));
            }
            for (Future<?> call : calls) {
                call.get();
            }
        }

        assertThat(holdersOf(standard)).containsExactly(user);
    }

    @Test
    void aHeldRoleCantBeDeleted() {
        RoleResponse sales = callAs(tenant, () -> roles.create(role("Sales")));
        assign(firstUser, sales.id());
        assign(newUser(), sales.id());

        assertThatThrownBy(() -> runAs(tenant, () -> roles.delete(sales.id())))
                .isInstanceOf(RoleInUseException.class)
                .hasMessage("2 users still have this role.");

        unassign(firstUser, sales.id());
        assertThatThrownBy(() -> runAs(tenant, () -> roles.delete(sales.id())))
                .hasMessage("1 user still has this role.");
    }

    @Test
    void theForeignKeyRefusesDeletingAHeldRoleBehindTheService() {
        // what a concurrent assignment would hit, after the service's count passed
        RoleResponse sales = callAs(tenant, () -> roles.create(role("Sales")));
        assign(firstUser, sales.id());

        assertThatThrownBy(() -> runAs(tenant, () -> transaction.executeWithoutResult(status ->
                jdbc.sql("delete from app.roles where id = ?").param(sales.id()).update())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_role_assignments_role_id");
    }

    @Test
    void deletingAUserRemovesTheirAssignments() {
        UUID user = newUser();
        RoleResponse sales = callAs(tenant, () -> roles.create(role("Sales")));
        assign(user, sales.id());

        runAs(tenant, () -> transaction.executeWithoutResult(status ->
                jdbc.sql("delete from app.users where id = ?").param(user).update()));

        assertThat(holdersOf(sales.id())).isEmpty();
    }

    @Test
    void theFirstUserIsTheProvisionedAdministrator() {
        User first = callAs(tenant, () -> users.get(firstUser));

        assertThat(first.getDisplayName()).isEqualTo("First user");
        assertThat(holdersOf(administrator)).containsExactly(firstUser);
    }
}
