package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.companies.CompanyStandardFieldProvider;
import com.blusimian.blusimian.contacts.ContactStandardFieldProvider;
import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.deals.DealStandardFieldProvider;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Field key <-> permission columns, with the real providers and no database. */
class FieldPermissionCatalogTest {

    private static final CustomFieldDefinition LEAD_SOURCE = new CustomFieldDefinition(EntityType.CONTACT,
            "lead_source", "Lead Source", FieldDataType.TEXT, "cf_text_03", true, 0);

    private CustomFieldService customFields;
    private FieldPermissionCatalog catalog;

    @BeforeEach
    void setUp() {
        customFields = mock(CustomFieldService.class);
        when(customFields.listActive(any())).thenReturn(List.of());
        catalog = new FieldPermissionCatalog(new StandardFieldRegistry(List.of(
                new CompanyStandardFieldProvider(), new ContactStandardFieldProvider(), new DealStandardFieldProvider())),
                customFields);
    }

    private PermissionField field(EntityType type, String key) {
        return catalog.fieldsFor(type).stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void standardKeysBecomeSnakeCaseColumns() {
        assertThat(field(EntityType.CONTACT, "firstName"))
                .isEqualTo(new PermissionField("firstName", true, "first_name_read", "first_name_edit"));
        // keyed by field key, not by the record's column (address_street)
        assertThat(field(EntityType.CONTACT, "street").readColumn()).isEqualTo("street_read");
        assertThat(field(EntityType.CONTACT, "postalCode").editColumn()).isEqualTo("postal_code_edit");
        assertThat(field(EntityType.COMPANY, "phoneAlt").readColumn()).isEqualTo("phone_alt_read");
    }

    @Test
    void aCustomFieldBecomesItsSlotsColumns() {
        when(customFields.listActive(EntityType.CONTACT)).thenReturn(List.of(LEAD_SOURCE));

        assertThat(field(EntityType.CONTACT, "lead_source"))
                .isEqualTo(new PermissionField("lead_source", true, "cf_text_03_read", "cf_text_03_edit"));
    }

    @Test
    void customFieldsFollowStandardOnesAndOnlyActiveOnesExist() {
        // listActive is what leaves an archived field out
        when(customFields.listActive(EntityType.CONTACT)).thenReturn(List.of(LEAD_SOURCE));

        assertThat(catalog.fieldsFor(EntityType.CONTACT)).extracting(PermissionField::key)
                .hasSize(11).startsWith("salutation").endsWith("lead_source");
        when(customFields.listActive(EntityType.CONTACT)).thenReturn(List.of());
        assertThat(catalog.fieldsFor(EntityType.CONTACT)).extracting(PermissionField::key)
                .doesNotContain("lead_source");
    }

    @Test
    void theExpectedColumnsCoverEveryProviderFieldAndSlot() {
        assertThat(catalog.expectedColumns(EntityType.CONTACT)).hasSize(230)
                .contains("salutation_read", "country_code_edit", "cf_text_40_read", "cf_longtext_05_edit");
        assertThat(catalog.expectedColumns(EntityType.COMPANY)).hasSize(230);
        assertThat(catalog.expectedColumns(EntityType.DEAL)).hasSize(216);
    }

    @Test
    void aDocumentBecomesColumnsThroughTheCatalogNeverItsOwnSpelling() {
        List<PermissionField> fields = catalog.fieldsFor(EntityType.CONTACT);
        EntityPermissions permissions = new EntityPermissions(true, false, false, false,
                Map.of("FIRSTNAME", new FieldAccess(true, false), "foo; drop table", new FieldAccess(true, true)));

        assertThat(FieldPermissionCatalog.toColumns(permissions, fields))
                .containsOnlyKeys("can_read", "can_create", "can_edit", "can_delete",
                        "first_name_read", "first_name_edit")
                .containsEntry("first_name_read", true)
                .containsEntry("first_name_edit", false);
    }

    @Test
    void aRowBecomesADocumentUnderKeys() {
        when(customFields.listActive(EntityType.CONTACT)).thenReturn(List.of(LEAD_SOURCE));
        Map<String, Object> row = Map.of("can_read", true, "cf_text_03_read", true, "email_edit", false);

        EntityPermissions document = FieldPermissionCatalog.toPermissions(row, catalog.fieldsFor(EntityType.CONTACT));

        assertThat(document.canRead()).isTrue();
        assertThat(document.canDelete()).isFalse();
        assertThat(document.fields()).containsEntry("lead_source", new FieldAccess(true, false))
                .doesNotContainKey("cf_text_03");
    }
}
