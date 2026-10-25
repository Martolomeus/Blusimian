package com.blusimian.blusimian.deals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Database access for deals. With Spring Data JPA the implementation is generated at
 * runtime from JpaRepository<Deal, UUID> -- the two type args are "what entity" and
 * "what key type" -- so findAll/findById/save/delete come for free.
 */
public interface DealRepository extends JpaRepository<Deal, UUID> {
}
