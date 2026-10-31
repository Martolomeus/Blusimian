package com.blusimian.blusimian.countries;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serves the global country list. Trivial today (it just returns every ISO
 * country), but kept as a service so the eventual "only the tenant's enabled
 * countries" logic has a home -- the GET /api/countries contract won't change.
 */
@Service
public class CountryService {

    private final IsoCountryRepository repository;

    public CountryService(IsoCountryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IsoCountry> list() {
        return repository.findAllByOrderBySortOrderAscNameAsc();
    }
}
