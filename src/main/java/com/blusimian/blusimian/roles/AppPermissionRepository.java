package com.blusimian.blusimian.roles;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Database access for app_permissions rows. Every role has exactly one. */
public interface AppPermissionRepository extends JpaRepository<AppPermission, UUID> {

    Optional<AppPermission> findByRoleId(UUID roleId);
}
