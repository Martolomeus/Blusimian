package com.blusimian.blusimian.companies;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * All database access for companies. Now that Company is a JPA @Entity, this is
 * just an interface: Spring Data generates the implementation at runtime, so the
 * SQL we used to hand-write in JdbcClient (findAll, findById, save, delete...)
 * comes for free from JpaRepository<Company, UUID> -- the two type args are
 * "what entity" and "what key type".
 */
public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
