package com.blusimian.blusimian.contacts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The HTTP layer for contacts. It only translates between HTTP and service calls; no
 * business logic lives here.
 *
 * Same contract as CompanyController: the record is a flat field map (built-in + custom
 * fields at the top level next to id/createdAt/updatedAt), so the body in and out is a
 * Map -- the schema endpoint, not a static DTO, is the field contract. Writes use JSON
 * Merge Patch semantics (present sets, null clears, absent leaves), so PATCH is the
 * primary verb; PUT is kept as an alias for clients that send the full record (with
 * every field present, merge equals replace).
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    // GET /api/contacts
    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    // GET /api/contacts/{id}
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    // POST /api/contacts
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // 201 instead of the default 200
    public Map<String, Object> create(@RequestBody Map<String, Object> values) {
        return service.create(values);
    }

    // PUT/PATCH /api/contacts/{id} -- both merge (see class doc).
    @RequestMapping(value = "/{id}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody Map<String, Object> values) {
        return service.update(id, values);
    }

    // DELETE /api/contacts/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)   // 204, empty body
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
