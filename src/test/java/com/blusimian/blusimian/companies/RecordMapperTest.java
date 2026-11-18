package com.blusimian.blusimian.companies;

import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.picklists.EntityPicklists;
import com.blusimian.blusimian.picklists.PicklistLookup;
import com.blusimian.blusimian.picklists.PicklistOption;
import com.blusimian.blusimian.platform.web.ApiError;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the heart of the record architecture: the merge-patch write path and
 * the flat read shape. No Spring context and no database -- RecordMapper's only
 * collaborators are the field sources and a JsonMapper.
 *
 * The custom side is wired through the real CustomFieldDescriptors over a mocked
 * CustomFieldService, so these tests still cover the definition -> slot -> descriptor
 * resolution rather than handing RecordMapper ready-made descriptors. Picklist options
 * come from a stubbed PicklistLookup.
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
    private static final CustomFieldDefinition REGION =
            new CustomFieldDefinition(EntityType.COMPANY, "region", "Region",
                    FieldDataType.PICKLIST, "cf_text_02", false, 40);

    private RecordMapper mapper;
    private PicklistLookup picklists;
    private CompanyStandardFieldProvider provider;

    @BeforeEach
    void setUp() {
        CustomFieldService customFieldService = mock(CustomFieldService.class);
        List<CustomFieldDefinition> definitions = List.of(LEAD_SOURCE, RENEWAL, SEATS, REGION);
        when(customFieldService.listActive(any())).thenReturn(definitions);
        when(customFieldService.listAll(any())).thenReturn(definitions);
        picklists = mock(PicklistLookup.class);
        when(picklists.forEntity(EntityType.COMPANY)).thenReturn(new EntityPicklists(Map.of(
                "industry", List.of(
                        new PicklistOption("technology", "Technology", true),
                        new PicklistOption("retail", "Retail", true),
                        new PicklistOption("shipping", "Shipping", false)),
                "region", List.of(new PicklistOption("emea", "EMEA", true)))));
        mapper = new RecordMapper(new CustomFieldDescriptors(customFieldService), picklists,
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

    private static List<ApiError.FieldError> errors(Throwable e) {
        return ((BadRequestException) e).details();
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
        Company company = company(values("name", "Acme", "legalName", "Acme Inc.", "lead_source", "Web"));

        mapper.apply(company, provider, values("legalName", "Acme B.V."));

        assertThat(company.getName()).isEqualTo("Acme");
        assertThat(company.getLegalName()).isEqualTo("Acme B.V.");
        assertThat(company.getSlots().getCfText01()).isEqualTo("Web");
    }

    @Test
    void explicitNullClearsAFieldWhileAbsenceLeavesIt() {
        Company company = company(values("name", "Acme", "legalName", "Acme Inc.", "lead_source", "Web"));

        mapper.apply(company, provider, values("legalName", null));

        assertThat(company.getLegalName()).isNull();
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
        assertThatThrownBy(() -> company(values("legalName", "Acme Inc.")))
                .isInstanceOf(BadRequestException.class);

        Company company = company(values("name", "Acme"));
        // absent on update -> fine, the stored value still satisfies it
        mapper.apply(company, provider, values("legalName", "Acme Inc."));
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
        Company company = company(values("name", "Acme", "industry", "technology"));

        assertThatThrownBy(() -> mapper.apply(company, provider,
                values("industry", "retail", "renewal_date", "not-a-date")))
                .isInstanceOf(BadRequestException.class);

        assertThat(company.getIndustry()).isEqualTo("technology");
    }

    @Test
    void aPicklistFieldTakesAnActiveCode() {
        assertThat(company(values("name", "Acme", "industry", "technology")).getIndustry()).isEqualTo("technology");
    }

    @Test
    void aPicklistRefusesAnUnknownCodeAWrongCaseAndTheEmptyString() {
        for (String value : List.of("software", "Technology", "")) {
            assertThatThrownBy(() -> company(values("name", "Acme", "industry", value)))
                    .isInstanceOf(BadRequestException.class)
                    .satisfies(e -> assertThat(errors(e)).singleElement().satisfies(error -> {
                        assertThat(error.field()).isEqualTo("industry");
                        assertThat(error.reason()).isEqualTo("Not one of the options");
                    }));
        }
    }

    @Test
    void anInactiveCodeIsRefusedOnCreateButKeptWhenUnchanged() {
        assertThatThrownBy(() -> company(values("name", "Acme", "industry", "shipping")))
                .isInstanceOf(BadRequestException.class);

        // A record that held it before it was deactivated, sent back whole (merge patch).
        Company company = company(values("name", "Acme"));
        company.setIndustry("shipping");
        mapper.apply(company, provider, values("name", "Acme Renamed", "industry", "shipping"));
        assertThat(company.getName()).isEqualTo("Acme Renamed");
        assertThat(company.getIndustry()).isEqualTo("shipping");

        // A different inactive or unknown code is still refused.
        company.setIndustry("technology");
        assertThatThrownBy(() -> mapper.apply(company, provider, values("industry", "shipping")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nullClearsAPicklistWithoutALookup() {
        Company company = company(values("name", "Acme"));
        company.setIndustry("technology");

        mapper.apply(company, provider, values("industry", null));

        assertThat(company.getIndustry()).isNull();
        verify(picklists, never()).forEntity(any());
    }

    @Test
    void noLookupWhenNoPicklistFieldIsWritten() {
        company(values("name", "Acme", "legalName", "Acme Inc.", "lead_source", "Web"));

        verify(picklists, never()).forEntity(any());
    }

    @Test
    void aCustomPicklistIsCheckedTheSameWay() {
        assertThat(company(values("name", "Acme", "region", "emea")).getSlots().getCfText02()).isEqualTo("emea");
        // Keys match case-insensitively, codes don't.
        assertThat(company(values("name", "Acme", "REGION", "emea")).getSlots().getCfText02()).isEqualTo("emea");
        assertThatThrownBy(() -> company(values("name", "Acme", "region", "EMEA")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(errors(e)).singleElement()
                        .satisfies(error -> assertThat(error.field()).isEqualTo("region")));
    }

    @Test
    void aPicklistValueOfTheWrongJsonTypeIsOneError() {
        assertThatThrownBy(() -> company(values("name", "Acme", "industry", 7)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(errors(e)).singleElement()
                        .satisfies(error -> assertThat(error.reason()).isEqualTo("Expected PICKLIST")));
    }
}
