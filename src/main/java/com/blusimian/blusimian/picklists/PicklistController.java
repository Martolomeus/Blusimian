package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.picklists.dto.PicklistResponse;
import com.blusimian.blusimian.picklists.dto.ReplacePicklistRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Picklist values by entity and field key; value ids never leave the server. No POST or
 * DELETE: a picklist exists because its field does. Gated by manage_custom_fields once enforced.
 */
@RestController
@RequestMapping("/api/picklists")
public class PicklistController {

    private final PicklistService service;

    public PicklistController(PicklistService service) {
        this.service = service;
    }

    // GET /api/picklists/company
    @GetMapping("/{entityType}")
    public List<PicklistResponse> list(@PathVariable EntityType entityType) {
        return service.listFor(entityType);
    }

    // PUT /api/picklists/company/industry -- the whole list, in display order.
    @PutMapping("/{entityType}/{fieldKey}")
    public PicklistResponse replace(@PathVariable EntityType entityType, @PathVariable String fieldKey,
                                    @RequestBody ReplacePicklistRequest request) {
        return service.replace(entityType, fieldKey, request);
    }
}
