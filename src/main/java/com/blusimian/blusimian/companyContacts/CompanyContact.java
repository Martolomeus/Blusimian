package com.blusimian.blusimian.companyContacts;

import com.blusimian.blusimian.companies.Company;
import com.blusimian.blusimian.contacts.Contact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The link row joining a company to a contact -- the join table app.company_contacts.
 *
 * Both sides are @ManyToOne so we can read their names straight off the link
 * (link.getCompany().getName(), link.getContact().getFirstName()). @ManyToOne is
 * EAGER by default, which is what we want here: a link only exists to connect the
 * two, so loading them together in the one query is correct -- no lazy surprises,
 * no extra fetch tuning.
 */
@Entity
@Table(name = "company_contacts")
public class CompanyContact {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyContact() {
        // Required by JPA.
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
