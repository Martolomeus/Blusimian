package com.blusimian.blusimian.companyContacts;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The HTTP layer for company<->contact links. Mirrors CompanyController: it only
 * translates between HTTP and method calls; no business logic lives here. Spring
 * serializes the returned CompanyContact/List<CompanyContact> to JSON.
 */
@RestController
@RequestMapping("/api/company-contacts")
public class CompanyContactController {

    private final CompanyContactService service;

    public CompanyContactController(CompanyContactService service) {
        this.service = service;
    }

    // GET /api/company-contacts                    -> all links
    // GET /api/company-contacts?companyId={id}      -> an account's contacts
    // GET /api/company-contacts?contactId={id}      -> a contact's accounts
    @GetMapping
    public List<CompanyContactResponse> list(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID contactId) {
        return service.list(companyId, contactId);
    }

    // GET /api/company-contacts/{id}
    @GetMapping("/{id}")
    public CompanyContactResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    // POST /api/company-contacts
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // 201 instead of the default 200
    public CompanyContactResponse create(@Valid @RequestBody CompanyContactRequest request) {
        return service.create(request);
    }

    // PUT /api/company-contacts/{id}
    @PutMapping("/{id}")
    public CompanyContactResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyContactRequest request) {
        return service.update(id, request);
    }

    // DELETE /api/company-contacts/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)   // 204, empty body
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
