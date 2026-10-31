package com.blusimian.blusimian.roles;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Database access for roles. Under RLS it sees the current tenant's roles only. */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findAllByOrderByNameAsc();
}
