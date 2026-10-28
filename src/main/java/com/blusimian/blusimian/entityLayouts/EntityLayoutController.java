package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityLayouts.dto.CreateEntityLayoutRequest;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutResponse;
import com.blusimian.blusimian.entityLayouts.dto.UpdateEntityLayoutRequest;
import com.blusimian.blusimian.entityModel.EntityType;
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

/**
 * HTTP layer for managing entity layouts -- the admin side behind the layout editor.
 * Only translates between HTTP and service calls.
 *
 * Record pages never call this: they get the one layout that applies to them from the
 * schema bundle (GET /api/entities/{entityType}/schema). There is no get-by-id either;
 * the list returns an entity's layouts as full trees, and the editor reads its layout from it.
 * A malformed {id} fails UUID conversion and Spring returns 400; an unknown one is a 404
 * from the service.
 */
@RestController
@RequestMapping("/api/entity-layouts")
public class EntityLayoutController {

    private final EntityLayoutService service;

    public EntityLayoutController(EntityLayoutService service) {
        this.service = service;
    }

    // GET /api/entity-layouts/{entityType} -- every layout of that entity, full trees.
    @GetMapping("/{entityType}")
    public List<EntityLayoutResponse> list(@PathVariable EntityType entityType) {
        return service.list(entityType);
    }

    // POST /api/entity-layouts -- a new, non-default layout.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityLayoutResponse create(@Valid @RequestBody CreateEntityLayoutRequest request) {
        return service.create(request);
    }

    // PUT /api/entity-layouts/{id} -- replace the label and every section.
    @PutMapping("/{id}")
    public EntityLayoutResponse replace(@PathVariable UUID id, @Valid @RequestBody UpdateEntityLayoutRequest request) {
        return service.replace(id, request);
    }

    // PUT /api/entity-layouts/{id}/default -- make it its entity's default (idempotent).
    @PutMapping("/{id}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(@PathVariable UUID id) {
        service.setDefault(id);
    }

    // DELETE /api/entity-layouts/{id} -- refused for the default layout.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
