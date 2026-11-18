package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.customFields.CustomFieldDefinitionRequest;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.tenants.NewTenant;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A custom PICKLIST field, created with its options in one POST (spec §9). */
@SpringBootTest
@AutoConfigureMockMvc
class CustomFieldPicklistTest {

    private static final String PASSWORD = "custom-picklist-password";
    private static final String LEAD_SOURCE = """
            {"fieldKey":"lead_source","label":"Lead Source","dataType":"PICKLIST","required":false,"sortOrder":40,
             "options":[{"label":"Web"},{"label":"Referral"},{"label":"Event"},
                        {"label":"Cold outreach","code":"outbound"}]}""";

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    CustomFieldService customFields;
    @Autowired
    MockMvc mockMvc;

    TenantId tenant;
    MockHttpSession session;

    @BeforeEach
    void provisionAndLogIn() throws Exception {
        tenant = new TenantId(UUID.randomUUID());
        String username = "custom-picklists-" + tenant.value();
        provisioning.provision(new NewTenant(tenant, "Custom picklist test", username, "First user", PASSWORD));
        session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions createField(String body) throws Exception {
        return mockMvc.perform(post("/api/custom-fields/company").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createCompany(String body) throws Exception {
        return mockMvc.perform(post("/api/companies").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String nextTextSlot() {
        return callAs(tenant, () -> customFields.create(EntityType.COMPANY,
                new CustomFieldDefinitionRequest("probe", "Probe", FieldDataType.TEXT, false, 0))).getColumnName();
    }

    @Test
    void aPicklistFieldIsCreatedWithItsOptionsInATextSlot() throws Exception {
        createField(LEAD_SOURCE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataType").value("PICKLIST"))
                .andExpect(jsonPath("$.options").doesNotExist());

        assertThat(callAs(tenant, () -> customFields.listActive(EntityType.COMPANY)))
                .singleElement().satisfies(field -> assertThat(field.getColumnName()).isEqualTo("cf_text_01"));
        mockMvc.perform(get("/api/picklists/company").session(session))
                .andExpect(jsonPath("$[*].fieldKey", contains("type", "industry", "employeeRange", "lead_source")))
                .andExpect(jsonPath("$[3].label").value("Lead Source"))
                .andExpect(jsonPath("$[3].options[*].code", contains("web", "referral", "event", "outbound")))
                .andExpect(jsonPath("$[3].options[3].label").value("Cold outreach"));
        mockMvc.perform(get("/api/entities/company/schema").session(session))
                .andExpect(jsonPath("$.fields[?(@.key == 'lead_source')].options[*]", hasSize(4)));
    }

    @Test
    void aRecordTakesACodeOfTheCustomPicklistAndNothingElse() throws Exception {
        createField(LEAD_SOURCE).andExpect(status().isCreated());

        createCompany("{\"name\":\"Acme\",\"lead_source\":\"Web\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("lead_source"));
        createCompany("{\"name\":\"Acme\",\"lead_source\":\"outbound\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lead_source").value("outbound"));
    }

    @Test
    void aPicklistWithoutOptionsIsRefusedBeforeASlotIsTaken() throws Exception {
        createField("""
                {"fieldKey":"region","label":"Region","dataType":"PICKLIST","required":false,"sortOrder":0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("options"));
        createField("""
                {"fieldKey":"region","label":"Region","dataType":"PICKLIST","required":false,"sortOrder":0,"options":[]}""")
                .andExpect(status().isBadRequest());

        assertThat(nextTextSlot()).isEqualTo("cf_text_01");
    }

    @Test
    void optionsOnAnotherTypeAreRefused() throws Exception {
        createField("""
                {"fieldKey":"region","label":"Region","dataType":"TEXT","required":false,"sortOrder":0,"options":[{"label":"EMEA"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only a PICKLIST field has options"));

        assertThat(nextTextSlot()).isEqualTo("cf_text_01");
    }

    @Test
    void aBadOptionRollsTheFieldBackAndFreesItsSlot() throws Exception {
        createField("""
                {"fieldKey":"region","label":"Region","dataType":"PICKLIST","required":false,"sortOrder":0,
                 "options":[{"label":"EMEA"},{"label":"emea"},{"label":"APAC","code":"Not-A-Code"},null]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field",
                        contains("options[1].label", "options[2].code", "options[3]")));

        assertThat(callAs(tenant, () -> customFields.listAll(EntityType.COMPANY))).isEmpty();
        assertThat(nextTextSlot()).isEqualTo("cf_text_01");
        mockMvc.perform(get("/api/picklists/company").session(session))
                .andExpect(jsonPath("$[*].fieldKey", not(hasItem("region"))));
    }

    @Test
    void anArchivedPicklistFieldHasNoPicklistButItsValuesStaySaveable() throws Exception {
        String id = JsonPath.read(createField(LEAD_SOURCE)
                .andReturn().getResponse().getContentAsString(), "$.id");
        String company = JsonPath.read(createCompany("{\"name\":\"Acme\",\"lead_source\":\"web\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/custom-fields/" + id).session(session)).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/picklists/company/lead_source").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"options\":[{\"code\":\"web\",\"label\":\"Web\"}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/picklists/company").session(session))
                .andExpect(jsonPath("$[*].fieldKey", not(hasItem("lead_source"))));
        // Archived fields stay writable, and the value it holds is still one of its codes.
        mockMvc.perform(patch("/api/companies/" + company).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lead_source\":\"web\"}"))
                .andExpect(status().isOk());
    }
}
