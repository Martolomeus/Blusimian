package com.blusimian.blusimian.picklists;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One entity's picklist options, by field key (case-insensitive). Immutable. */
public final class EntityPicklists {

    public static final EntityPicklists EMPTY = new EntityPicklists(Map.of());

    private final Map<String, List<PicklistOption>> options = new HashMap<>();
    private final Map<String, Set<String>> activeCodes = new HashMap<>();

    /** Each field's options in display order. */
    public EntityPicklists(Map<String, List<PicklistOption>> optionsByFieldKey) {
        optionsByFieldKey.forEach((key, list) -> {
            String normalized = normalize(key);
            options.put(normalized, List.copyOf(list));
            Set<String> codes = new HashSet<>();
            list.stream().filter(PicklistOption::active).forEach(option -> codes.add(option.code()));
            activeCodes.put(normalized, codes);
        });
    }

    /** The field's options in display order, inactive included; empty if it has none. */
    public List<PicklistOption> optionsFor(String fieldKey) {
        return options.getOrDefault(normalize(fieldKey), List.of());
    }

    /** null, an active code, or the code the record already holds. Codes are case-sensitive. */
    public boolean allows(String fieldKey, Object value, Object current) {
        return value == null
                || value.equals(current)
                || activeCodes.getOrDefault(normalize(fieldKey), Set.of()).contains(value);
    }

    private static String normalize(String fieldKey) {
        return fieldKey.toLowerCase(Locale.ROOT);
    }
}
