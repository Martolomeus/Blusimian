package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.EntityType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HTTP layer for custom fields. Only translates between HTTP and service calls.
 *
 * GET /api/custom-fields/{entityType} returns the active definitions for that
 * entity so the frontend can render its form. {entityType} binds to the
 * EntityType enum via StringToEntityTypeConverter (so "company" works,
 * not just "COMPANY"); an unknown value fails conversion and Spring returns 400.
 */
@RestController
@RequestMapping("/api/custom-fields")
public class CustomFieldController {

    private final CustomFieldService service;

    public CustomFieldController(CustomFieldService service) {
        this.service = service;
    }

    // GET /api/custom-fields/company
    @GetMapping("/{entityType}")
    public List<CustomFieldDefinitionResponse> list(@PathVariable EntityType entityType) {
        return service.listActive(entityType).stream()
                .map(CustomFieldDefinitionResponse::from)
                .toList();
    }

    // POST /api/custom-fields/company -- create a definition for that entity.
    @PostMapping("/{entityType}")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomFieldDefinitionResponse create(@PathVariable EntityType entityType,
                                                @Valid @RequestBody CustomFieldDefinitionRequest request) {
        return CustomFieldDefinitionResponse.from(service.create(entityType, request));
    }

    // DELETE /api/custom-fields/{id} -- archives (soft delete), not a physical delete.
    // Disambiguated from GET /{entityType} by the HTTP method.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.archive(id);
    }
}
