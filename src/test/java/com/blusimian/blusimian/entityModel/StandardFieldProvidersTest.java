package com.blusimian.blusimian.entityModel;

import com.blusimian.blusimian.companies.CompanyStandardFieldProvider;
import com.blusimian.blusimian.contacts.ContactStandardFieldProvider;
import com.blusimian.blusimian.deals.DealStandardFieldProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract every record entity's provider has to meet. An EntityType with no provider
 * would still accept custom field definitions and serve a schema, but no endpoint could
 * read or write a record of it -- so adding a type here without wiring its slice fails.
 */
class StandardFieldProvidersTest {

    private final StandardFieldRegistry registry = new StandardFieldRegistry(List.of(
            new CompanyStandardFieldProvider(),
            new ContactStandardFieldProvider(),
            new DealStandardFieldProvider()));

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void everyEntityTypeHasAProviderWithUniqueNonReservedKeys(EntityType entityType) {
        List<String> keys = registry.fieldsFor(entityType).stream()
                .map(field -> field.key().toLowerCase(Locale.ROOT))
                .toList();

        assertThat(keys).isNotEmpty().doesNotHaveDuplicates();
        // keys are matched case-insensitively on write, so compare them that way
        assertThat(keys).doesNotContainAnyElementsOf(
                RecordMapper.META_KEYS.stream().map(key -> key.toLowerCase(Locale.ROOT)).toList());
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void referenceToIsSetExactlyOnReferenceFields(EntityType entityType) {
        assertThat(registry.fieldsFor(entityType)).allSatisfy(field ->
                assertThat(field.referenceTo() != null).isEqualTo(field.dataType() == FieldDataType.REFERENCE));
    }

    @Test
    void aReferenceTypeHasNoPoolSoItCannotBackACustomField() {
        assertThat(FieldDataType.REFERENCE.pool()).isNull();
    }
}
