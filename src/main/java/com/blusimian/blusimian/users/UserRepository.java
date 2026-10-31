package com.blusimian.blusimian.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Database access for users. Under RLS it sees the current tenant's users only. */
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findAllByOrderByUsernameAsc();
}
