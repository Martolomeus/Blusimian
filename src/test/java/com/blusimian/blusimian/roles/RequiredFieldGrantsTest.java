package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldCreated;
import com.blusimian.blusimian.entityModel.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Who the one automatic grant reaches is RequiredFieldRuleIntegrationTest's; this is when it fires. */
class RequiredFieldGrantsTest {

    private final EntityPermissionRepository permissions = mock(EntityPermissionRepository.class);
    private final RequiredFieldGrants grants = new RequiredFieldGrants(permissions);

    @Test
    void anOptionalFieldWritesNothing() {
        grants.on(new CustomFieldCreated(EntityType.CONTACT, "region", "cf_text_01", false, List.of()));

        verifyNoInteractions(permissions);
    }

    @Test
    void aRequiredFieldIsGrantedOnItsEntity() {
        grants.on(new CustomFieldCreated(EntityType.DEAL, "seats", "cf_num_02", true, List.of()));

        verify(permissions).grantToRolesWithCreate(EntityType.DEAL, "cf_num_02");
    }
}
