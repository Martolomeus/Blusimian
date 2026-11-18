package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refuses to start when public.standard_picklist_values and the standard PICKLIST fields
 * disagree: the seed is SQL and the providers are Java, so nothing else ties them together.
 */
@Component
class StandardPicklistsVerifier implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(StandardPicklistsVerifier.class);

    private static final String SEED = "select entity_type, field_key, code from public.standard_picklist_values";

    record PicklistField(String entityType, String key, Integer maxLength) {
    }

    record SeedRow(String entityType, String fieldKey, String code) {
    }

    private final JdbcClient jdbc;
    private final StandardFieldRegistry standardFields;

    StandardPicklistsVerifier(JdbcClient jdbc, StandardFieldRegistry standardFields) {
        this.jdbc = jdbc;
        this.standardFields = standardFields;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<PicklistField> fields = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            standardFields.fieldsFor(type).stream()
                    .filter(field -> field.dataType() == FieldDataType.PICKLIST)
                    .forEach(field -> fields.add(new PicklistField(type.name(), field.key(), field.maxLength())));
        }
        List<SeedRow> seed = jdbc.sql(SEED)
                .query((rs, row) -> new SeedRow(rs.getString("entity_type"), rs.getString("field_key"),
                        rs.getString("code")))
                .list();

        List<String> problems = compare(fields, seed);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Standard picklist values don't match the standard PICKLIST fields:\n  - "
                    + String.join("\n  - ", problems));
        }
        log.info("Standard picklists verified");
    }

    /** Keys are compared exactly: the seed must spell a field as its provider does. */
    static List<String> compare(List<PicklistField> fields, List<SeedRow> seed) {
        Map<String, PicklistField> byName = fields.stream()
                .collect(Collectors.toMap(field -> name(field.entityType(), field.key()), Function.identity()));
        Set<String> seeded = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();

        for (SeedRow row : seed) {
            String name = name(row.entityType(), row.fieldKey());
            PicklistField field = byName.get(name);
            if (field == null) {
                unknown.add(name);
                continue;
            }
            seeded.add(name);
            if (field.maxLength() != null && row.code().length() > field.maxLength()) {
                problems.add("code " + row.code() + " of " + name + " is longer than the field's "
                        + field.maxLength() + " characters");
            }
        }
        byName.keySet().stream()
                .filter(name -> !seeded.contains(name))
                .sorted()
                .forEach(name -> problems.add(name + " is a PICKLIST field with no rows in public.standard_picklist_values"));
        unknown.forEach(name -> problems.add("public.standard_picklist_values has rows for " + name
                + ", which is not a standard PICKLIST field"));
        return problems;
    }

    private static String name(String entityType, String key) {
        return entityType + "." + key;
    }
}
