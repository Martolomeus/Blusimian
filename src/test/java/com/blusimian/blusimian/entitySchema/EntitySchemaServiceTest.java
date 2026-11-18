package com.blusimian.blusimian.entitySchema;

import com.blusimian.blusimian.companies.CompanyStandardFieldProvider;
import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityLayouts.EntityLayoutService;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionFieldResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionResponse;
import com.blusimian.blusimian.entityModel.EntityFieldDictionary;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.entitySchema.dto.EntitySchemaResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaFieldResponse;
import com.blusimian.blusimian.entitySchema.dto.SchemaPlacementResponse;
import com.blusimian.blusimian.picklists.EntityPicklists;
import com.blusimian.blusimian.picklists.PicklistLookup;
import com.blusimian.blusimian.picklists.PicklistOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the schema bundle's composition: the field dictionary plus the one
 * resolved layout, and the guarantee that every placement in it joins to a dictionary
 * entry. No Spring context and no database -- the dictionary is the real
 * EntityFieldDictionary over the real company provider and the real CustomFieldDescriptors
 * (on a mocked CustomFieldService), and the layout is handed in as the DTO
 * EntityLayoutService returns.
 */
class EntitySchemaServiceTest {

    private static final UUID LAYOUT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    private static final CustomFieldDefinition LEAD_SOURCE =
            new CustomFieldDefinition(EntityType.COMPANY, "lead_source", "Lead Source",
                    FieldDataType.PICKLIST, "cf_text_01", false, 10);

    private static final List<PicklistOption> INDUSTRIES = List.of(
            new PicklistOption("agriculture", "Agriculture", true),
            new PicklistOption("shipping", "Shipping", false));
    private static final List<PicklistOption> SOURCES = List.of(new PicklistOption("web", "Web", true));

    private EntityLayoutService layouts;
    private EntitySchemaService service;

    @BeforeEach
    void setUp() {
        CustomFieldService customFieldService = mock(CustomFieldService.class);
        when(customFieldService.listActive(any())).thenReturn(List.of(LEAD_SOURCE));
        layouts = mock(EntityLayoutService.class);
        PicklistLookup picklists = mock(PicklistLookup.class);
        // Spelled differently from the fields on purpose: keys match case-insensitively.
        when(picklists.forEntity(EntityType.COMPANY)).thenReturn(new EntityPicklists(Map.of(
                "Industry", INDUSTRIES, "LEAD_SOURCE", SOURCES)));
        service = new EntitySchemaService(
                new EntityFieldDictionary(
                        new StandardFieldRegistry(List.of(new CompanyStandardFieldProvider())),
                        new CustomFieldDescriptors(customFieldService)),
                layouts, picklists);
    }

    /** A one-section layout placing the given keys, in order. */
    private void givenLayoutPlacing(String... fieldKeys) {
        List<EntityLayoutSectionFieldResponse> placements = new ArrayList<>();
        for (int i = 0; i < fieldKeys.length; i++) {
            placements.add(new EntityLayoutSectionFieldResponse(UUID.randomUUID(), fieldKeys[i], (i + 1) * 10));
        }
        EntityLayoutSectionResponse section =
                new EntityLayoutSectionResponse(UUID.randomUUID(), "Overview", 2, 10, placements);
        when(layouts.resolve(EntityType.COMPANY)).thenReturn(Optional.of(
                new EntityLayoutResponse(LAYOUT_ID, EntityType.COMPANY, "Default", true, List.of(section))));
    }

    private List<String> placedKeys(EntitySchemaResponse schema) {
        return schema.layout().sections().getFirst().fields().stream()
                .map(SchemaPlacementResponse::key)
                .toList();
    }

    @Test
    void theResolvedLayoutIsServedWithItsIdLabelAndSections() {
        givenLayoutPlacing("name", "website");

        EntitySchemaResponse schema = service.getSchema(EntityType.COMPANY);

        assertThat(schema.layout().id()).isEqualTo(LAYOUT_ID);
        assertThat(schema.layout().label()).isEqualTo("Default");
        assertThat(schema.layout().sections()).singleElement().satisfies(section -> {
            assertThat(section.label()).isEqualTo("Overview");
            assertThat(section.columnCount()).isEqualTo(2);
        });
        assertThat(placedKeys(schema)).containsExactly("name", "website");
    }

    @Test
    void standardAndCustomFieldsShareOneDictionary() {
        givenLayoutPlacing("name", "lead_source");

        EntitySchemaResponse schema = service.getSchema(EntityType.COMPANY);

        assertThat(schema.fields()).extracting(SchemaFieldResponse::key)
                .startsWith("name", "legalName")
                .endsWith("lead_source");
        assertThat(placedKeys(schema)).containsExactly("name", "lead_source");
    }

    @Test
    void withoutALayoutTheBundleStillCarriesTheFields() {
        when(layouts.resolve(EntityType.COMPANY)).thenReturn(Optional.empty());

        EntitySchemaResponse schema = service.getSchema(EntityType.COMPANY);

        assertThat(schema.layout()).isNull();
        assertThat(schema.fields()).isNotEmpty();
    }

    @Test
    void aPlacementWhoseFieldIsGoneIsDropped() {
        // e.g. a custom field archived after it was placed: no FK removes the placement
        givenLayoutPlacing("name", "archived_field", "website");

        assertThat(placedKeys(service.getSchema(EntityType.COMPANY))).containsExactly("name", "website");
    }

    @Test
    void picklistFieldsCarryTheirOptionsInactiveIncludedAndOthersCarryNone() {
        when(layouts.resolve(EntityType.COMPANY)).thenReturn(Optional.empty());

        Map<String, SchemaFieldResponse> fields = new java.util.HashMap<>();
        service.getSchema(EntityType.COMPANY).fields().forEach(field -> fields.put(field.key(), field));

        assertThat(fields.get("industry").options()).isEqualTo(INDUSTRIES);
        assertThat(fields.get("lead_source").options()).isEqualTo(SOURCES);
        assertThat(fields.get("type").options()).isEmpty();   // a picklist with no rows
        assertThat(fields.get("name").options()).isNull();
    }

    @Test
    void aPlacementKeyIsSpelledAsInTheDictionary() {
        // field_key is case-insensitive, so a placement may differ from the field's key in case
        givenLayoutPlacing("legalname", "LEAD_SOURCE");

        assertThat(placedKeys(service.getSchema(EntityType.COMPANY))).containsExactly("legalName", "lead_source");
    }
}
