package com.blusimian.blusimian.countries;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read access to the global ISO country list. Ordered by sort_order (for any
 * future "pin common countries to the top" need), then alphabetically by name.
 */
public interface IsoCountryRepository extends JpaRepository<IsoCountry, String> {

    List<IsoCountry> findAllByOrderBySortOrderAscNameAsc();
}
