package com.blusimian.blusimian.contacts;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The contact as it lives in the database -- now a JPA @Entity instead of a
 * record. JPA can't map records: Hibernate needs a no-arg constructor and
 * mutable fields so it can instantiate and populate objects reflectively. That
 * mutability is the price of dropping the hand-written SQL.
 *
 * This entity is never serialized by Jackson directly -- RecordMapper turns it into a
 * flat field map first, reading the getters through ContactStandardFieldProvider.
 * Tenant-defined custom field values live in the embedded CustomFieldSlots (the cf_*
 * column pool), and @DynamicUpdate is load-bearing for the same reason as on Company:
 * only the columns actually changed appear in the UPDATE, so concurrent writes to
 * different fields compose instead of clobbering each other.
 *
 * @Entity = "this class maps to a table" (the table name comes from @Table).
 * @Id     = the primary key.
 * The @Column(name = ...) annotations bridge camelCase fields to the snake_case
 * columns; without them Hibernate would expect columns named "firstname" etc.
 */
@Entity
@Table(name = "contacts")
@DynamicUpdate
public class Contact implements RecordEntity {

    @Id
    @GeneratedValue                 // Hibernate generates a random UUID before insert
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String salutation;

    // email is a Postgres citext (case-insensitive text). Keep it mapped as a
    // normal String (varchar binding, so reads/writes are plain text); the
    // columnDefinition tells Hibernate the real DB type so ddl-auto=validate
    // doesn't reject it as a varchar mismatch.
    @Column(columnDefinition = "citext")
    private String email;

    private String phone;

    // Postal address. The DB columns are address_*; the Java fields keep the
    // shorter names (street, city, ...) the record JSON uses as keys.
    @Column(name = "address_street")
    private String street;

    @Column(name = "address_city")
    private String city;

    @Column(name = "address_state")
    private String state;

    @Column(name = "address_postal_code")
    private String postalCode;

    // address_country is char(2) (an ISO country code). Map the JDBC type as CHAR
    // (not the String default of VARCHAR) so ddl-auto=validate matches the column;
    // CHAR still reads/writes as a normal String.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "address_country", length = 2)
    private String countryCode;

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

    protected Contact() {
        // Required by JPA. Protected so the rest of the app doesn't use it.
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSalutation() {
        return salutation;
    }

    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
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
