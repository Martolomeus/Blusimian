package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.roles.dto.RoleRequest;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HTTP layer for the caller's tenant's roles. */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    // GET /api/roles -- by name, with each role's holder count.
    @GetMapping
    public List<RoleSummaryResponse> list() {
        return service.list();
    }

    // GET /api/roles/{id} -- the role document.
    @GetMapping("/{id}")
    public RoleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    // POST /api/roles -- a field left out of the document is closed.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return service.create(request);
    }

    // PUT /api/roles/{id} -- a field left out of the document keeps its stored bits.
    @PutMapping("/{id}")
    public RoleResponse replace(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return service.replace(id, request);
    }

    // DELETE /api/roles/{id} -- refused for the Administrator and while anyone holds it.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
