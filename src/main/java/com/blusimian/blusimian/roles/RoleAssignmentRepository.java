package com.blusimian.blusimian.roles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Database access for role assignments. Writes are single statements rather than an
 * entity's save and delete, so two identical requests at once both succeed.
 */
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    interface RoleHolders {
        UUID getRoleId();

        long getHolders();
    }

    @Query("select a.roleId as roleId, count(a) as holders from RoleAssignment a group by a.roleId")
    List<RoleHolders> countHoldersByRole();

    long countByRoleId(UUID roleId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("""
            select count(a) from RoleAssignment a
            join User u on u.id = a.userId
            where a.roleId = ?1 and u.enabled = true and a.userId <> ?2""")
    long countOtherEnabledHolders(UUID roleId, UUID userId);

    @Modifying
    @Query(value = "insert into app.role_assignments (user_id, role_id) values (?1, ?2) on conflict do nothing",
            nativeQuery = true)
    void insertIfAbsent(UUID userId, UUID roleId);

    @Modifying
    @Query("delete from RoleAssignment a where a.userId = ?1 and a.roleId = ?2")
    void deleteByUserAndRole(UUID userId, UUID roleId);
}
