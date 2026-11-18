package com.blusimian.blusimian.deals;

import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.picklists.PicklistLookup;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The deal as a flat record: what DealStandardFieldProvider adds on top of the generic
 * behaviour RecordMapperTest already covers -- a built-in NUMBER field and a REFERENCE.
 * No Spring context and no database.
 *
 * Lives in the deals package so it can reach Deal's protected no-arg constructor, the
 * same way JPA does.
 */
class DealRecordTest {

    private RecordMapper mapper;
    private DealStandardFieldProvider provider;

    @BeforeEach
    void setUp() {
        // an unstubbed mock lists no definitions: these tests need no custom fields
        mapper = new RecordMapper(new CustomFieldDescriptors(mock(CustomFieldService.class)),
                mock(PicklistLookup.class), JsonMapper.builder().build());
        provider = new DealStandardFieldProvider();
    }

    private Deal deal(Map<String, Object> values) {
        Deal deal = new Deal();
        mapper.apply(deal, provider, values);
        return deal;
    }

    private static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static List<String> invalidFields(Throwable e) {
        return ((BadRequestException) e).details().stream().map(ApiError.FieldError::field).toList();
    }

    @Test
    void amountAndCustomerArriveAsJsonAndLandTyped() {
        UUID customerId = UUID.randomUUID();

        Deal deal = deal(values("dealName", "Renewal", "amount", 1250.5, "customerId", customerId.toString()));

        assertThat(deal.getAmount()).isEqualByComparingTo(new BigDecimal("1250.5"));
        assertThat(deal.getCustomerId()).isEqualTo(customerId);
        assertThat(mapper.toRecord(deal, provider))
                .containsEntry("dealName", "Renewal")
                .containsEntry("customerId", customerId)
                .containsKeys("amount", "id", "createdAt", "updatedAt");
    }

    @Test
    void dealNameIsRequiredButTheCustomerIsNot() {
        assertThatThrownBy(() -> deal(values("amount", 10)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("dealName"));

        assertThat(deal(values("dealName", "Renewal")).getCustomerId()).isNull();
    }

    @Test
    void aReferenceMustBeAWellFormedId() {
        assertThatThrownBy(() -> deal(values("dealName", "Renewal", "customerId", "acme")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("customerId"));
        assertThatThrownBy(() -> deal(values("dealName", "Renewal", "customerId", 42)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nullClearsTheCustomer() {
        Deal deal = deal(values("dealName", "Renewal", "customerId", UUID.randomUUID().toString()));

        mapper.apply(deal, provider, values("customerId", null));

        assertThat(deal.getCustomerId()).isNull();
        assertThat(deal.getDealName()).isEqualTo("Renewal");
    }

    @Test
    void amountRejectsAString() {
        assertThatThrownBy(() -> deal(values("dealName", "Renewal", "amount", "a lot")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("amount"));
    }
}
