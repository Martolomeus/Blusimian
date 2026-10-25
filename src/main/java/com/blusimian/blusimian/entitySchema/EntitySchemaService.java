package com.blusimian.blusimian.entitySchema;

import com.blusimian.blusimian.entityModel.EntityFieldDictionary;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityLayouts.EntityLayoutService;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionResponse;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entitySchema.dto.EntitySchemaResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaFieldResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaLayoutResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaPlacementResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaSectionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assembles the per-type metadata bundle (§7): standard fields + custom fields +
 * the layout resolved for the caller, composed at the edge while each feature stays modular. The
 * arrows point one-way down -- this service depends on entityModel and entityLayouts;
 * neither depends on it. Custom fields reach it as FieldDescriptors through
 * EntityFieldDictionary, like standard ones.
 *
 * This slice is only the bundle: the machinery it composes (field descriptors, the
 * provider registry, the record mapper) lives a layer down in entityModel, because
 * serving a record has nothing to do with serving its schema.
 *
 * The fields come from EntityFieldDictionary (standard, then active custom), the same
 * list the layout validator checks placements against; the layout from
 * EntityLayoutService. Both kinds arrive as FieldDescriptors and go through the same
 * toResponse, so the `fields` dictionary is uniform by construction rather than by two
 * mappings that have to be kept in step -- the layout resolves either by `key` (one flat
 * keyspace).
 *
 * The bundle is self-consistent: every placement's key exists in `fields`, spelled as it
 * is there. A placement is a citext key with no foreign key, so it can outlive its field
 * (an archived custom field) or differ from it in case; both are settled here, once,
 * instead of in every client.
 */
@Service
public class EntitySchemaService {

    private final EntityFieldDictionary dictionary;
    private final EntityLayoutService entityLayoutService;

    public EntitySchemaService(EntityFieldDictionary dictionary, EntityLayoutService entityLayoutService) {
        this.dictionary = dictionary;
        this.entityLayoutService = entityLayoutService;
    }

    @Transactional(readOnly = true)
    public EntitySchemaResponse getSchema(EntityType entityType) {
        // Standard then custom -- same descriptor shape, so the same mapping.
        List<SchemaFieldResponse> fields = dictionary.fieldsFor(entityType).stream()
                .map(EntitySchemaService::toResponse)
                .toList();

        // Lower-cased key -> the dictionary's spelling, to match placements against.
        Map<String, String> keys = new HashMap<>();
        fields.forEach(field -> keys.put(field.key().toLowerCase(Locale.ROOT), field.key()));

        // The caller's layout, if one is configured (otherwise the bundle has fields only).
        SchemaLayoutResponse layout = entityLayoutService.resolve(entityType)
                .map(resolved -> toLayout(resolved, keys))
                .orElse(null);

        return new EntitySchemaResponse(entityType, fields, layout);
    }

    private static SchemaLayoutResponse toLayout(EntityLayoutResponse layout, Map<String, String> keys) {
        return new SchemaLayoutResponse(layout.id(), layout.label(),
                layout.sections().stream()
                        .map(section -> toSection(section, keys))
                        .toList());
    }

    /** A section with only the placements whose field is in the dictionary, keyed as it is there. */
    private static SchemaSectionResponse toSection(EntityLayoutSectionResponse section, Map<String, String> keys) {
        List<SchemaPlacementResponse> placements = new ArrayList<>();
        section.fields().forEach(placement -> {
            String key = keys.get(placement.fieldKey().toLowerCase(Locale.ROOT));
            if (key != null) {
                placements.add(new SchemaPlacementResponse(key, placement.sortOrder()));
            }
        });
        return new SchemaSectionResponse(section.label(), section.columnCount(), section.sortOrder(), placements);
    }

    /** One descriptor as a dictionary entry. Identical for standard and custom by design. */
    private static SchemaFieldResponse toResponse(FieldDescriptor<?> field) {
        return new SchemaFieldResponse(field.key(), field.label(), field.dataType(),
                field.required(), field.maxLength(), field.referenceTo());
    }
}
