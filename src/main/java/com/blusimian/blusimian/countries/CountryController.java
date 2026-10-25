package com.blusimian.blusimian.countries;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/countries -- the country list for pickers (e.g. the address field's
 * country select). Returns every ISO country for now.
 */
@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    @GetMapping
    public List<IsoCountry> list() {
        return service.list();
    }
}
