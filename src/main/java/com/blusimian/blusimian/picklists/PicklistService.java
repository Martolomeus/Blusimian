package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityFieldDictionary;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.picklists.dto.PicklistOptionRequest;
import com.blusimian.blusimian.picklists.dto.PicklistResponse;
import com.blusimian.blusimian.picklists.dto.ReplacePicklistRequest;
import com.blusimian.blusimian.picklists.exception.PicklistNotFoundException;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Admin side of picklists: list an entity's picklists, replace one list, seed a new custom
 * one. Fields come from EntityFieldDictionary, values from PicklistLookup and the repository.
 */
@Service
public class PicklistService {

    static final int MAX_VALUES = 500;
    static final int MAX_LABEL_LENGTH = 100;

    private final EntityFieldDictionary dictionary;
    private final CustomFieldService customFields;   // for lockEntityFields only
    private final PicklistLookup lookup;
    private final PicklistValueRepository repository;
    private final JdbcClient jdbc;

    public PicklistService(EntityFieldDictionary dictionary, CustomFieldService customFields,
                           PicklistLookup lookup, PicklistValueRepository repository, JdbcClient jdbc) {
        this.dictionary = dictionary;
        this.customFields = customFields;
        this.lookup = lookup;
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** Every picklist field of the entity (standard, then active custom) with its options. */
    @Transactional(readOnly = true)
    public List<PicklistResponse> listFor(EntityType entityType) {
        List<FieldDescriptor<?>> fields = picklistFields(entityType);
        if (fields.isEmpty()) {
            return List.of();
        }
        EntityPicklists options = lookup.forEntity(entityType);
        return fields.stream()
                .map(field -> new PicklistResponse(entityType, field.key(), field.label(), options.optionsFor(field.key())))
                .toList();
    }

    /** Replace one field's list with the request's, in its order. Serialised per entity with field creation. */
    @Transactional
    public PicklistResponse replace(EntityType entityType, String fieldKey, ReplacePicklistRequest request) {
        customFields.lockEntityFields(entityType);
        FieldDescriptor<?> field = picklistFields(entityType).stream()
                .filter(candidate -> candidate.key().equalsIgnoreCase(fieldKey))
                .findFirst()
                .orElseThrow(() -> new PicklistNotFoundException(entityType, fieldKey));
        List<PicklistValue> stored =
                repository.findByEntityTypeAndFieldKeyOrderBySortOrderAscCodeAsc(entityType, field.key());
        List<PicklistOption> options = write(entityType, field.key(), stored, request.options());
        return new PicklistResponse(entityType, field.key(), field.label(), options);
    }

    /** A new custom picklist's first values, under the same rules as a PUT. Runs in the field's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(EntityType entityType, String fieldKey, List<PicklistOptionRequest> options) {
        write(entityType, fieldKey, List.of(), options);
    }

    private List<FieldDescriptor<?>> picklistFields(EntityType entityType) {
        return dictionary.fieldsFor(entityType).stream()
                .filter(field -> field.dataType() == FieldDataType.PICKLIST)
                .toList();
    }

    private List<PicklistOption> write(EntityType entityType, String fieldKey, List<PicklistValue> stored,
                                       List<PicklistOptionRequest> options) {
        Map<String, PicklistValue> storedByCode = new HashMap<>();
        stored.forEach(value -> storedByCode.put(value.getCode(), value));

        List<ApiError.FieldError> errors = validate(storedByCode.keySet(), options);
        if (!errors.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR,
                    "Invalid picklist for " + entityType + "." + fieldKey, errors);
        }
        List<String> codes = codes(storedByCode.keySet(), options);

        List<PicklistValue> created = new ArrayList<>();
        List<PicklistOption> result = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String code = codes.get(i);
            String label = options.get(i).label().strip();
            boolean active = options.get(i).activeOrDefault();
            PicklistValue value = storedByCode.get(code);
            if (value == null) {
                created.add(new PicklistValue(entityType, fieldKey, code, label, i + 1, active));
            } else {
                value.relabel(label);
                value.moveTo(i + 1);
                if (active) {
                    value.activate();
                } else {
                    value.deactivate();
                }
            }
            result.add(new PicklistOption(code, label, active));
        }
        repository.saveAll(created);
        checkLabelsNow();
        return result;
    }

    /**
     * Flushes, then checks the deferred label unique now rather than at commit, so a clash
     * the Collator missed is still a 400 from here and not a failed commit.
     */
    private void checkLabelsNow() {
        repository.flush();
        try {
            jdbc.sql("set constraints app.uq_picklist_values_label immediate").update();
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Two labels are the same ignoring case",
                    List.of(new ApiError.FieldError("options", "Labels must be unique ignoring case")));
        }
    }

    /** Every rule of spec §10.2, all checked before anything is written. */
    static List<ApiError.FieldError> validate(Set<String> storedCodes, List<PicklistOptionRequest> options) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        if (options == null) {
            errors.add(new ApiError.FieldError("options", "Required"));
            return errors;
        }
        if (options.size() > MAX_VALUES) {
            errors.add(new ApiError.FieldError("options", "At most " + MAX_VALUES + " values"));
        }

        Map<String, Integer> labels = new TreeMap<>(labelCollator());
        Set<String> codes = new HashSet<>();
        boolean anyActive = false;
        for (int i = 0; i < options.size(); i++) {
            String path = "options[" + i + "]";
            PicklistOptionRequest option = options.get(i);
            if (option == null) {
                errors.add(new ApiError.FieldError(path, "Required"));
                continue;
            }
            String label = option.label() == null ? "" : option.label().strip();
            if (label.isEmpty()) {
                errors.add(new ApiError.FieldError(path + ".label", "Required"));
            } else if (label.length() > MAX_LABEL_LENGTH) {
                errors.add(new ApiError.FieldError(path + ".label", "Longer than " + MAX_LABEL_LENGTH + " characters"));
            } else {
                Integer first = labels.putIfAbsent(label, i);
                if (first != null) {
                    errors.add(new ApiError.FieldError(path + ".label", "Same label as options[" + first + "]"));
                }
            }
            if (hasCode(option)) {
                String code = option.code();
                if (code.length() > PicklistCodes.MAX_LENGTH) {
                    errors.add(new ApiError.FieldError(path + ".code",
                            "Longer than " + PicklistCodes.MAX_LENGTH + " characters"));
                } else if (!PicklistCodes.matchesPattern(code)) {
                    errors.add(new ApiError.FieldError(path + ".code",
                            "Not a valid code: lower-case letters and digits, joined by single underscores"));
                } else if (!codes.add(code)) {
                    errors.add(new ApiError.FieldError(path + ".code", "Appears more than once"));
                }
            }
            anyActive |= option.activeOrDefault();
        }
        if (!anyActive) {
            errors.add(new ApiError.FieldError("options", "At least one value must be active"));
        }
        new TreeSet<>(storedCodes).stream()
                .filter(code -> !codes.contains(code))
                .forEach(code -> errors.add(new ApiError.FieldError("options",
                        "'" + code + "' is missing: deactivate a value instead of removing it")));
        return errors;
    }

    /** Each option's code: its own, or derived from its label, avoiding every code already in play. */
    private static List<String> codes(Set<String> storedCodes, List<PicklistOptionRequest> options) {
        Set<String> taken = new LinkedHashSet<>(storedCodes);
        options.stream().filter(PicklistService::hasCode).forEach(option -> taken.add(option.code()));
        List<String> codes = new ArrayList<>();
        for (PicklistOptionRequest option : options) {
            String code = hasCode(option) ? option.code() : PicklistCodes.derive(option.label().strip(), taken);
            taken.add(code);
            codes.add(code);
        }
        return codes;
    }

    private static boolean hasCode(PicklistOptionRequest option) {
        return option.code() != null && !option.code().isBlank();
    }

    /** Case-insensitive, accent-sensitive: what the column's und-u-ks-level2 collation means. */
    private static Collator labelCollator() {
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.SECONDARY);
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        return collator;
    }
}
