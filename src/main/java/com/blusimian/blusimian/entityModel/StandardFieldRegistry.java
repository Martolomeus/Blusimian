package com.blusimian.blusimian.entityModel;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every StandardFieldProvider Spring discovers, indexed once by entityType(), for the
 * consumers that look standard fields up by type (the schema bundle, the reserved-key
 * check below). Two providers for the same type fail startup (toMap rejects duplicate keys).
 *
 * Entity services don't go through here: they inject their own concrete provider, which
 * keeps the entity type parameter for RecordMapper.
 */
@Component
public class StandardFieldRegistry {

    private final Map<EntityType, StandardFieldProvider<?>> providers;

    public StandardFieldRegistry(List<StandardFieldProvider<?>> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(StandardFieldProvider::entityType, Function.identity()));
    }

    /** The entity's standard fields in display order; empty if it has no provider. */
    public List<? extends FieldDescriptor<?>> fieldsFor(EntityType entityType) {
        StandardFieldProvider<?> provider = providers.get(entityType);
        return provider == null ? List.of() : provider.fields();
    }

    /**
     * The keys a new custom field may not take, enforcing the one-flat-keyspace rule: the
     * entity's built-in field keys plus the system meta keys (RecordMapper.META_KEYS), so
     * a custom field can't shadow either. Consumed by CustomFieldService on create.
     */
    public Set<String> reservedKeysFor(EntityType entityType) {
        Set<String> keys = new HashSet<>(RecordMapper.META_KEYS);
        fieldsFor(entityType).forEach(field -> keys.add(field.key()));
        return keys;
    }
}
