package com.blusimian.blusimian.companies;

import com.blusimian.blusimian.entityModel.CustomFieldSlots;
import com.blusimian.blusimian.entityModel.RecordEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The company as it lives in the database -- now a JPA @Entity, mirroring Contact
 * (it used to be a record read/written by hand-written SQL through JdbcClient).
 * JPA can't map records: Hibernate needs a no-arg constructor and mutable fields
 * so it can instantiate and populate objects reflectively.
 *
 * The companies table has many more columns than this (billing/shipping address,
 * owner_id, ...). We only map the ones the app currently uses. That's fine:
 * spring.jpa.hibernate.ddl-auto=validate only checks that the columns we DO map
 * exist with matching types -- unmapped columns are left to their database
 * defaults. Map more fields here as features need them.
 *
 * Tenant-defined custom field values live in the embedded CustomFieldSlots (the
 * cf_* column pool), not in a jsonb blob.
 *
 * @DynamicUpdate is load-bearing, not a tuning flag. Hibernate's default UPDATE
 * sets EVERY mapped column from its in-memory snapshot, so a transaction that
 * loaded this row before a concurrent write committed would silently overwrite
 * that write with its own stale value -- the lost-update problem the column pool
 * exists to avoid, reintroduced at the ORM layer. With it, only the columns this
 * session actually changed appear in the SET clause, so concurrent writes to
 * different slots compose correctly.
 *
 * This record is never serialized by Jackson directly -- RecordMapper turns it
 * into a flat field map first.
 */
@Entity
@Table(name = "companies")
@DynamicUpdate
public class Company implements RecordEntity {

    @Id
    @GeneratedValue                 // Hibernate generates a random UUID before insert
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    private String type;

    private String industry;

    private String description;

    @Column(name = "employee_range")
    private String employeeRange;

    private String website;

    private String phone;

    @Column(name = "phone_alt")
    private String phoneAlt;

    @Column(name = "vat_number")
    private String vatNumber;

    // Tenant-defined custom field values, one per allocated slot in the cf_* pool.
    // Which slot belongs to which field is recorded in custom_field_definitions.
    @Embedded
    private CustomFieldSlots slots = new CustomFieldSlots();

    @CreationTimestamp              // set once, on insert
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp                // refreshed on every update
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
        // Required by JPA. Protected so the rest of the app doesn't use it.
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmployeeRange() {
        return employeeRange;
    }

    public void setEmployeeRange(String employeeRange) {
        this.employeeRange = employeeRange;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneAlt() {
        return phoneAlt;
    }

    public void setPhoneAlt(String phoneAlt) {
        this.phoneAlt = phoneAlt;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    /**
     * Hibernate leaves an @Embedded field null when every one of its columns is null,
     * which is the normal state of a company with no custom field values set. Create
     * it on demand so callers never have to null-check the pool.
     */
    @Override
    public CustomFieldSlots getSlots() {
        if (slots == null) {
            slots = new CustomFieldSlots();
        }
        return slots;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
