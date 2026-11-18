package com.blusimian.blusimian.contacts;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The contact as a flat record: what ContactStandardFieldProvider adds on top of the
 * generic behaviour RecordMapperTest already covers. No Spring context and no database.
 *
 * Lives in the contacts package so it can reach Contact's protected no-arg constructor,
 * the same way JPA does.
 */
class ContactRecordTest {

    private static final CustomFieldDefinition WORK_EMAIL =
            new CustomFieldDefinition(EntityType.CONTACT, "work_email", "Work Email",
                    FieldDataType.EMAIL, "cf_text_01", false, 10);

    private RecordMapper mapper;
    private ContactStandardFieldProvider provider;

    @BeforeEach
    void setUp() {
        CustomFieldService customFieldService = mock(CustomFieldService.class);
        when(customFieldService.listActive(EntityType.CONTACT)).thenReturn(List.of(WORK_EMAIL));
        when(customFieldService.listAll(EntityType.CONTACT)).thenReturn(List.of(WORK_EMAIL));
        PicklistLookup picklists = mock(PicklistLookup.class);
        when(picklists.forEntity(EntityType.CONTACT)).thenReturn(new EntityPicklists(Map.of("salutation",
                List.of(new PicklistOption("mr", "Mr.", true), new PicklistOption("dr", "Dr.", true)))));
        mapper = new RecordMapper(new CustomFieldDescriptors(customFieldService), picklists,
                JsonMapper.builder().build());
        provider = new ContactStandardFieldProvider();
    }

    private Contact contact(Map<String, Object> values) {
        Contact contact = new Contact();
        mapper.apply(contact, provider, values);
        return contact;
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
    void readShapeCarriesEveryBuiltInFieldAndTheCustomOnesSideBySide() {
        Contact contact = contact(values("salutation", "dr", "firstName", "Ada", "lastName", "Lovelace",
                "email", "ada@example.test", "countryCode", "GB", "work_email", "ada@work.test"));

        Map<String, Object> record = mapper.toRecord(contact, provider);

        assertThat(record).containsEntry("salutation", "dr")
                .containsEntry("firstName", "Ada")
                .containsEntry("lastName", "Lovelace")
                .containsEntry("email", "ada@example.test")
                .containsEntry("countryCode", "GB")
                .containsEntry("work_email", "ada@work.test")
                .containsKeys("phone", "street", "city", "state", "postalCode",
                        "id", "createdAt", "updatedAt");
        assertThat(contact.getSlots().getCfText01()).isEqualTo("ada@work.test");
    }

    @Test
    void firstAndLastNameAreBothRequired() {
        assertThatThrownBy(() -> contact(values("email", "ada@example.test")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactlyInAnyOrder("firstName", "lastName"));
    }

    @Test
    void emailMustLookLikeAnAddressButMayBeBlank() {
        assertThatThrownBy(() -> contact(values("firstName", "Ada", "lastName", "Lovelace", "email", "not-an-email")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("email"));

        // The current form sends "" for an untouched email field: no value, not a bad one.
        assertThat(contact(values("firstName", "Ada", "lastName", "Lovelace", "email", "")).getEmail()).isEmpty();
        assertThat(contact(values("firstName", "Ada", "lastName", "Lovelace", "email", null)).getEmail()).isNull();
    }

    @Test
    void aCustomEmailFieldIsFormatCheckedLikeTheBuiltInOne() {
        assertThatThrownBy(() -> contact(values("firstName", "Ada", "lastName", "Lovelace", "work_email", "nope")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("work_email"));
    }

    @Test
    void salutationIsACodeNotTheLabel() {
        assertThatThrownBy(() -> contact(values("firstName", "Ada", "lastName", "Lovelace", "salutation", "Dr.")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("salutation"));
    }

    @Test
    void valuesLongerThanTheirColumnAreRejected() {
        assertThatThrownBy(() -> contact(values("firstName", "Ada", "lastName", "Lovelace", "countryCode", "GBR")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(invalidFields(e)).containsExactly("countryCode"));
    }
}
