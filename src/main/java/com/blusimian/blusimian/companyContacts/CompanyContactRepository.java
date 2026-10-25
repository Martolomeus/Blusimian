package com.blusimian.blusimian.companyContacts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * All database access for company<->contact links. findAll/findById/save/delete
 * come from JpaRepository; the two finders below are derived queries -- Spring
 * reads the method name and writes the "where company_id = ?" / "where contact_id = ?"
 * SQL for you. Because company and contact are EAGER @ManyToOne, their rows are
 * loaded in the same query, so the account/contact names are ready to read.
 */
public interface CompanyContactRepository extends JpaRepository<CompanyContact, UUID> {

    List<CompanyContact> findByCompanyId(UUID companyId);

    List<CompanyContact> findByContactId(UUID contactId);
}
