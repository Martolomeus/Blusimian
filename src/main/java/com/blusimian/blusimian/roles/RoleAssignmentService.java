package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.platform.tenancy.TenantContext;
import com.blusimian.blusimian.roles.exception.LastAdministratorException;
import com.blusimian.blusimian.roles.exception.RoleNotFoundException;
import com.blusimian.blusimian.users.UserService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Who holds which role. A tenant has no console to recover from, so at least one enabled
 * user must hold Administrator. The future disable-user and delete-user paths must run
 * the same check.
 */
@Service
public class RoleAssignmentService {

    /** Advisory lock class for removing an Administrator; the key is the tenant. See CustomFieldService. */
    private static final int ROLE_ADMIN_LOCK_NAMESPACE = 0x0AD3;   // "ADMIN"

    private final RoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final UserService users;
    private final JdbcClient jdbc;

    RoleAssignmentService(RoleAssignmentRepository assignments, RoleRepository roles, UserService users,
                          JdbcClient jdbc) {
        this.assignments = assignments;
        this.roles = roles;
        this.users = users;
        this.jdbc = jdbc;
    }

    /** Every assignment in the tenant: one query, so the Users page has no N+1. */
    @Transactional(readOnly = true)
    public List<RoleAssignment> list() {
        return assignments.findAll();
    }

    /** Idempotent. Both are loaded first, so a guessed id is a 404: RLS hides other tenants' rows. */
    @Transactional
    public void assign(UUID userId, UUID roleId) {
        users.get(userId);
        findRole(roleId);
        assignments.insertIfAbsent(userId, roleId);
    }

    /** Idempotent. A user who doesn't hold the role loses nothing, so only a real removal is checked. */
    @Transactional
    public void unassign(UUID userId, UUID roleId) {
        users.get(userId);
        Role role = findRole(roleId);
        if (role.isAdmin() && assignments.existsByUserIdAndRoleId(userId, roleId)) {
            refuseIfLastAdministrator(userId, roleId);
        }
        assignments.deleteByUserAndRole(userId, roleId);
    }

    // The lock stops two Administrators removing each other, each seeing the other still there.
    private void refuseIfLastAdministrator(UUID userId, UUID roleId) {
        jdbc.sql("select pg_advisory_xact_lock(?, ?)")
                .param(ROLE_ADMIN_LOCK_NAMESPACE)
                .param(TenantContext.require().toString().hashCode())
                .query()
                .listOfRows();
        if (assignments.countOtherEnabledHolders(roleId, userId) == 0) {
            throw new LastAdministratorException();
        }
    }

    private Role findRole(UUID roleId) {
        return roles.findById(roleId).orElseThrow(RoleNotFoundException::new);
    }
}
