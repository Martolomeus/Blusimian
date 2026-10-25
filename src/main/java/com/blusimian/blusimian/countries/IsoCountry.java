package com.blusimian.blusimian.countries;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A global ISO 3166-1 country -- reference data shared by all tenants. It lives
 * in the "public" schema (not the tenant-scoped "app" schema), so the @Table
 * explicitly overrides the default schema configured for the app's entities.
 *
 * Read-only from the app's perspective: rows are seeded by the V1 migration, so
 * there are no setters.
 */
@Entity
@Table(schema = "public", name = "iso_countries")
public class IsoCountry {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)   // code is char(2); CHAR (not the String default VARCHAR) keeps validate happy
    @Column(length = 2)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected IsoCountry() {
        // Required by JPA.
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
