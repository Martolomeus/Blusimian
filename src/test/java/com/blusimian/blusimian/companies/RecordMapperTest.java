package com.blusimian.blusimian.companies;

import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.platform.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the heart of the record architecture: the merge-patch write path and
 * the flat read shape. No Spring context and no database -- RecordMapper's only
 * collaborators are the field sources and a JsonMapper.
 *
 * The custom side is wired through the real CustomFieldDescriptors over a mocked
 * CustomFieldService, so these tests still cover the definition -> slot -> descriptor
 * resolution rather than handing RecordMapper ready-made descriptors.
 *
 * Lives in the companies package so it can reach Company's protected no-arg constructor,
 * the same way JPA does.
 */
class RecordMapperTest {

    private static final CustomFieldDefinition LEAD_SOURCE =
            new CustomFieldDefinition(EntityType.COMPANY, "lead_source", "Lead Source",
                    FieldDataType.TEXT, "cf_text_01", false, 10);
    private static final CustomFieldDefinition RENEWAL =
            new CustomFieldDefinition(EntityType.COMPANY, "renewal_date", "Renewal Date",
                    FieldDataType.DATE, "cf_date_01", false, 20);
    private static final CustomFieldDefinition SEATS =
            new CustomFieldDefinition(EntityType.COMPANY, "seats", "Seats",
                    FieldDataType.NUMBER, "cf_num_01", false, 30);

    private RecordMapper mapper;
    private CompanyStandardFieldProvider provider;

    @BeforeEach
    void setUp() {
        CustomFieldService customFieldService = mock(CustomFieldService.class);
        List<CustomFieldDefinition> definitions = List.of(LEAD_SOURCE, RENEWAL, SEATS);
        when(customFieldService.listActive(any())).thenReturn(definitions);
        when(customFieldService.listAll(any())).thenReturn(definitions);
        mapper = new RecordMapper(new CustomFieldDescriptors(customFieldService),
                JsonMapper.builder().build());
        provider = new CompanyStandardFieldProvider();
    }

    private Company company(Map<String, Object> values) {
        Company company = new Company();
        mapper.apply(company, provider, values);
        return company;
    }

    private static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    void readShapeIsFlatAndCarriesBuiltInAndCustomFieldsSideBySide() {
        Company company = company(values("name", "Acme", "website", "https://acme.test",
                "lead_source", "Web", "renewal_date", "2026-03-01"));

        Map<String, Object> record = mapper.toRecord(company, provider);

        assertThat(record).containsEntry("name", "Acme")
                .containsEntry("website", "https://acme.test")
                .containsEntry("lead_source", "Web")
                .containsEntry("renewal_date", LocalDate.of(2026, 3, 1))
                .containsKeys("id", "createdAt", "updatedAt");
        // nothing nested: the custom values sit at the top level next to the built-ins
        assertThat(record.values()).noneMatch(v -> v instanceof Map);
    }

    @Test
    void partialUpdateTouchesOnlyTheKeysSent() {
        Company company = company(values("name", "Acme", "industry", "Software", "lead_source", "Web"));

        mapper.apply(company, provider, values("industry", "Hardware"));

        assertThat(company.getName()).isEqualTo("Acme");
        assertThat(company.getIndustry()).isEqualTo("Hardware");
        assertThat(company.getSlots().getCfText01()).isEqualTo("Web");
    }

    @Test
    void explicitNullClearsAFieldWhileAbsenceLeavesIt() {
        Company company = company(values("name", "Acme", "industry", "Software", "lead_source", "Web"));

        mapper.apply(company, provider, values("industry", null));

        assertThat(company.getIndustry()).isNull();
        assertThat(company.getSlots().getCfText01()).isEqualTo("Web");
    }

    @Test
    void aRecordReadFromTheApiCanBeWrittenStraightBack() {
        Company company = company(values("name", "Acme", "lead_source", "Web"));
        Map<String, Object> record = mapper.toRecord(company, provider);

        // id/createdAt/updatedAt are server-owned: ignored, not rejected
        assertThat(record).containsKeys("id", "createdAt", "updatedAt");
        mapper.apply(company, provider, record);

        assertThat(company.getName()).isEqualTo("Acme");
    }

    @Test
    void unknownFieldsAreRejectedWithAPerFieldError() {
        assertThatThrownBy(() -> company(values("name", "Acme", "not_a_field", "x")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).details())
                        .singleElement()
                        .satisfies(d -> assertThat(d.field()).isEqualTo("not_a_field")));
    }

    @Test
    void requiredIsCheckedAgainstTheMergedResultNotThePayload() {
        // absent on create -> missing
        assertThatThrownBy(() -> company(values("industry", "Software")))
                .isInstanceOf(BadRequestException.class);

        Company company = company(values("name", "Acme"));
        // absent on update -> fine, the stored value still satisfies it
        mapper.apply(company, provider, values("industry", "Software"));
        // explicitly blanked -> missing
        assertThatThrownBy(() -> mapper.apply(company, provider, values("name", "   ")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void customDateFieldRejectsAStringThatIsNotADate() {
        // Under the jsonb model this was stored verbatim: custom values had valueType
        // Object, so convert() waved them through unchecked.
        assertThatThrownBy(() -> company(values("name", "Acme", "renewal_date", "not-a-date")))
                .isInstanceOf(BadRequestException.class);

        Company ok = company(values("name", "Acme", "renewal_date", "2026-03-01"));
        assertThat(ok.getSlots().getCfDate01()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void customNumberFieldRejectsAStringAndConvertsARealNumber() {
        assertThatThrownBy(() -> company(values("name", "Acme", "seats", "twelve")))
                .isInstanceOf(BadRequestException.class);

        Company ok = company(values("name", "Acme", "seats", 12));
        assertThat(ok.getSlots().getCfNum01()).isEqualByComparingTo(BigDecimal.valueOf(12));
    }

    @Test
    void valuesLongerThanTheSlotAreRejectedRatherThanLeftToPostgres() {
        assertThatThrownBy(() -> company(values("name", "Acme", "lead_source", "x".repeat(256))))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).details())
                        .singleElement()
                        .satisfies(d -> assertThat(d.reason()).contains("255")));
    }

    @Test
    void nothingIsWrittenWhenAnyFieldIsInvalid() {
        Company company = company(values("name", "Acme", "industry", "Software"));

        assertThatThrownBy(() -> mapper.apply(company, provider,
                values("industry", "Hardware", "renewal_date", "not-a-date")))
                .isInstanceOf(BadRequestException.class);

        assertThat(company.getIndustry()).isEqualTo("Software");
    }
}
