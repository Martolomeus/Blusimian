package com.blusimian.blusimian.contacts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * All database access for contacts. With Spring Data JPA this is just an
 * interface: Spring generates the implementation at runtime, so the SQL you used
 * to hand-write (findAll, findById, save, delete...) comes for free from
 * JpaRepository<Contact, UUID> -- the two type args are "what entity" and "what
 * key type".
 */
public interface ContactRepository extends JpaRepository<Contact, UUID> {
}
