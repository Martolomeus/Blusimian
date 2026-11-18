package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.customFields.CustomFieldCreated;
import com.blusimian.blusimian.picklists.dto.PicklistOptionRequest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stores a new custom PICKLIST field's options. A refused option throws out of publishEvent, rolling the field back. */
@Component
class CustomFieldPicklists {

    private final PicklistService picklists;

    CustomFieldPicklists(PicklistService picklists) {
        this.picklists = picklists;
    }

    // Synchronous on purpose: it must share the field's transaction and tenant.
    @EventListener
    void on(CustomFieldCreated field) {
        if (field.options().isEmpty()) {
            return;
        }
        List<PicklistOptionRequest> options = field.options().stream()
                .map(option -> option == null ? null : new PicklistOptionRequest(option.code(), option.label(), true))
                .toList();
        picklists.seed(field.entityType(), field.fieldKey(), options);
    }
}
