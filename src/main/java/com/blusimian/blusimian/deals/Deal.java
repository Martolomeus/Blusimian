package com.blusimian.blusimian.deals;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A deal (opportunity) as it lives in app.deals -- a JPA @Entity mirroring Company:
 * mutable fields + a protected no-arg constructor so Hibernate can populate it. It is
 * never serialized by Jackson directly -- RecordMapper turns it into a flat field map
 * first, reading the getters through DealStandardFieldProvider.
 *
 * customerId references app.companies(id) at the database level (a FK), but is mapped
 * here as a plain UUID rather than a @ManyToOne Company: the record is flat, so a deal
 * reads as { ..., "customerId": "..." } (a REFERENCE field) without embedding the whole
 * company. Promote it to a real association later if a deal needs to navigate to its
 * company in Java.
 *
 * Tenant-defined custom field values live in the embedded CustomFieldSlots (the cf_*
 * column pool); @DynamicUpdate is load-bearing for the same reason as on Company.
 */
@Entity
@Table(name = "deals")
@DynamicUpdate
public class Deal implements RecordEntity {

    @Id
    @GeneratedValue                 // Hibernate generates a random UUID before insert
    private UUID id;

    @Column(name = "deal_name", nullable = false)
    private String dealName;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "customer_id")
    private UUID customerId;

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

    protected Deal() {
        // Required by JPA. Protected so the rest of the app doesn't use it.
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getDealName() {
        return dealName;
    }

    public void setDealName(String dealName) {
        this.dealName = dealName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    /** Never null: Hibernate leaves an all-null @Embedded field null on load (see Company). */
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
