package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.customFields.exception.CustomFieldSlotsExhaustedException;
import com.blusimian.blusimian.customFields.exception.DuplicateCustomFieldKeyException;
import com.blusimian.blusimian.customFields.exception.ReservedFieldKeyException;
import com.blusimian.blusimian.entityModel.CustomFieldSlots;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.platform.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slot allocation: the one piece of genuinely new logic the column pool introduced.
 * A definition is not just metadata -- creating one claims a physical column, and
 * claiming the wrong one (or the same one twice) would corrupt data.
 */
class CustomFieldSlotAllocationTest {

    private CustomFieldDefinitionRepository repository;
    private CustomFieldService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomFieldDefinitionRepository.class);
        StandardFieldRegistry standardFields = mock(StandardFieldRegistry.class);
        when(standardFields.reservedKeysFor(any())).thenReturn(Set.of("name", "website", "id"));
        JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new CustomFieldService(repository, standardFields, jdbcClient);
    }

    private CustomFieldDefinition create(String key, FieldDataType type) {
        return service.create(EntityType.COMPANY,
                new CustomFieldDefinitionRequest(key, key, type, false, 0));
    }

    @Test
    void allocatesTheLowestFreeSlotFromThePoolMatchingTheDataType() {
        when(repository.findAllocatedColumnNames(any())).thenReturn(List.of());

        assertThat(create("region", FieldDataType.TEXT).getColumnName()).isEqualTo("cf_text_01");
        assertThat(create("seats", FieldDataType.NUMBER).getColumnName()).isEqualTo("cf_num_01");
        assertThat(create("active", FieldDataType.BOOLEAN).getColumnName()).isEqualTo("cf_bool_01");
        assertThat(create("renewal", FieldDataType.DATE).getColumnName()).isEqualTo("cf_date_01");
        assertThat(create("notes", FieldDataType.LONGTEXT).getColumnName()).isEqualTo("cf_longtext_01");
    }

    @Test
    void aTypeWithoutAPoolIsRejectedBeforeAnythingIsClaimed() {
        assertThatThrownBy(() -> create("owner", FieldDataType.REFERENCE))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void urlAndPhoneShareTheTextPool() {
        when(repository.findAllocatedColumnNames(any())).thenReturn(List.of("cf_text_01"));

        assertThat(create("homepage", FieldDataType.URL).getColumnName()).isEqualTo("cf_text_02");
    }

    @Test
    void skipsSlotsAlreadyTakenIncludingByArchivedFields() {
        // gaps are filled, so allocation stays dense
        when(repository.findAllocatedColumnNames(any()))
                .thenReturn(List.of("cf_text_01", "cf_text_02", "cf_text_04"));

        assertThat(create("region", FieldDataType.TEXT).getColumnName()).isEqualTo("cf_text_03");
    }

    @Test
    void failsWithAConflictWhenThePoolIsExhausted() {
        when(repository.findAllocatedColumnNames(any()))
                .thenReturn(CustomFieldSlots.pool(CustomFieldSlots.POOL_DATE));

        assertThatThrownBy(() -> create("renewal", FieldDataType.DATE))
                .isInstanceOf(CustomFieldSlotsExhaustedException.class)
                .hasMessageContaining("all 10");
    }

    @Test
    void poolSizesMatchTheMigration() {
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_TEXT)).hasSize(40);
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_NUM)).hasSize(25);
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_BOOL)).hasSize(25);
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_DATE)).hasSize(10);
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_LONGTEXT)).hasSize(5);
        // the text pool must not swallow the long-text columns
        assertThat(CustomFieldSlots.pool(CustomFieldSlots.POOL_TEXT))
                .noneMatch(column -> column.startsWith("cf_longtext"));
    }

    @Test
    void noSlotIsClaimedWhenTheKeyIsReservedOrDuplicated() {
        when(repository.findAllocatedColumnNames(any())).thenReturn(List.of());

        assertThatThrownBy(() -> create("name", FieldDataType.TEXT))
                .isInstanceOf(ReservedFieldKeyException.class);

        when(repository.existsByEntityTypeAndFieldKeyIgnoreCase(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> create("region", FieldDataType.TEXT))
                .isInstanceOf(DuplicateCustomFieldKeyException.class);

        ArgumentCaptor<CustomFieldDefinition> saved = ArgumentCaptor.forClass(CustomFieldDefinition.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(saved.capture());
    }
}
