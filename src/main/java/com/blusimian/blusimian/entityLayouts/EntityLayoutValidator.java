package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionRequest;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.platform.web.ApiError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules a layout must meet to be written, checked against the entity's field
 * dictionary. Every problem is collected, so a client gets them all in one 400 rather
 * than one per attempt -- the same contract RecordMapper has for records.
 *
 * Only rules that protect something are enforced. Two layouts or two sections with the
 * same label are allowed, and so is a section with no fields (the read path already
 * skips it). The placement rules are what keeps a layout renderable: every key names a
 * field the entity has, no field appears twice, and every required field is somewhere
 * -- otherwise a record of the entity could not be created through the layout.
 *
 * Labels are checked as they will be stored, stripped of surrounding whitespace. Keys
 * are matched case-insensitively, like placements everywhere else (field_key's collation
 * is case-insensitive).
 *
 * The paths in `field` say where each problem is, for any API client; they are
 * informative, not a contract.
 */
public final class EntityLayoutValidator {

    public static final int MAX_SECTIONS = 15;
    public static final int MAX_LABEL_LENGTH = 255;

    private EntityLayoutValidator() {
    }

    /** Every problem with the layout, in request order; empty when it can be written. */
    public static List<ApiError.FieldError> validate(String label, List<EntityLayoutSectionRequest> sections,
                                                     List<? extends FieldDescriptor<?>> dictionary) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        checkLabel("label", label, errors);

        if (sections.isEmpty()) {
            errors.add(new ApiError.FieldError("sections", "At least one section"));
        } else if (sections.size() > MAX_SECTIONS) {
            errors.add(new ApiError.FieldError("sections", "At most " + MAX_SECTIONS + " sections"));
        }

        // Lower-cased key -> field, to match placements against.
        Map<String, FieldDescriptor<?>> fieldsByKey = new HashMap<>();
        dictionary.forEach(field -> fieldsByKey.put(field.key().toLowerCase(Locale.ROOT), field));

        Set<String> placed = new HashSet<>();   // in the dictionary's spelling
        for (int i = 0; i < sections.size(); i++) {
            EntityLayoutSectionRequest section = sections.get(i);
            String path = "sections[" + i + "]";
            checkLabel(path + ".label", section.label(), errors);
            if (section.columnCount() != 1 && section.columnCount() != 2) {
                errors.add(new ApiError.FieldError(path + ".columnCount", "Must be 1 or 2"));
            }

            for (int j = 0; j < section.fields().size(); j++) {
                String key = section.fields().get(j);
                String fieldPath = path + ".fields[" + j + "]";
                FieldDescriptor<?> field = fieldsByKey.get(key.toLowerCase(Locale.ROOT));
                if (field == null) {
                    errors.add(new ApiError.FieldError(fieldPath, "Unknown field '" + key + "'"));
                } else if (!placed.add(field.key())) {
                    errors.add(new ApiError.FieldError(fieldPath, "'" + field.key() + "' is placed more than once"));
                }
            }
        }

        for (FieldDescriptor<?> field : dictionary) {
            if (field.required() && !placed.contains(field.key())) {
                errors.add(new ApiError.FieldError("sections", "Required field '" + field.label() + "' is not placed"));
            }
        }
        return errors;
    }

    private static void checkLabel(String path, String label, List<ApiError.FieldError> errors) {
        if (label == null || label.isBlank()) {
            errors.add(new ApiError.FieldError(path, "Required"));
        } else if (label.strip().length() > MAX_LABEL_LENGTH) {
            errors.add(new ApiError.FieldError(path, "At most " + MAX_LABEL_LENGTH + " characters"));
        }
    }
}
