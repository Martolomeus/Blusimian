package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.picklists.dto.PicklistResponse;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.tenants.NewTenant;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standard picklists and the picklist API against the dev database, as blusimian_app, on a fresh tenant. */
@SpringBootTest
@AutoConfigureMockMvc
class PicklistApiTest {

    private static final String PASSWORD = "picklist-password";

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    PicklistService picklists;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    MockMvc mockMvc;

    TenantId tenant;
    MockHttpSession session;

    @BeforeEach
    void provisionAndLogIn() throws Exception {
        tenant = new TenantId(UUID.randomUUID());
        String username = "picklists-" + tenant.value();
        provisioning.provision(new NewTenant(tenant, "Picklist test", username, "First user", PASSWORD));
        session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    /** The field's options as PUT items, so a test can edit the stored list. */
    private List<Map<String, Object>> stored(EntityType entityType, String fieldKey) {
        PicklistResponse picklist = callAs(tenant, () -> picklists.listFor(entityType)).stream()
                .filter(candidate -> candidate.fieldKey().equals(fieldKey))
                .findFirst().orElseThrow();
        List<Map<String, Object>> items = new ArrayList<>();
        picklist.options().forEach(option -> items.add(option(option.code(), option.label(), option.active())));
        return items;
    }

    private static Map<String, Object> option(String code, String label, boolean active) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (code != null) {
            item.put("code", code);
        }
        item.put("label", label);
        item.put("active", active);
        return item;
    }

    private static Map<String, Object> find(List<Map<String, Object>> items, String code) {
        return items.stream().filter(item -> code.equals(item.get("code"))).findFirst().orElseThrow();
    }

    private ResultActions putIndustry(List<Map<String, Object>> options) throws Exception {
        return putPicklist("industry", options);
    }

    private ResultActions putPicklist(String fieldKey, List<Map<String, Object>> options) throws Exception {
        return mockMvc.perform(put("/api/picklists/company/" + fieldKey).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("options", options))));
    }

    private String schemaEtag() throws Exception {
        return mockMvc.perform(get("/api/entities/company/schema").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    @Test
    void provisioningCopiesEverySeedRow() {
        String columns = "entity_type, field_key, code, label, sort_order";
        List<List<Object>> seed = jdbc.sql("select " + columns + " from public.standard_picklist_values order by 1, 2, 3")
                .query((rs, row) -> List.<Object>of(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5)))
                .list();
        List<List<Object>> copied = callAs(tenant, () -> transaction.execute(status ->
                jdbc.sql("select " + columns + ", active from app.picklist_values order by 1, 2, 3")
                        .query((rs, row) -> {
                            assertThat(rs.getBoolean("active")).isTrue();
                            return List.<Object>of(rs.getString(1), rs.getString(2), rs.getString(3),
                                    rs.getString(4), rs.getInt(5));
                        })
                        .list()));

        assertThat(seed).hasSize(54);
        assertThat(copied).isEqualTo(seed);
    }

    @Test
    void theGetListsEveryPicklistFieldWithItsOptions() throws Exception {
        mockMvc.perform(get("/api/picklists/company").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fieldKey", contains("type", "industry", "employeeRange")))
                .andExpect(jsonPath("$[1].entityType").value("COMPANY"))
                .andExpect(jsonPath("$[1].label").value("Industry"))
                .andExpect(jsonPath("$[1].options", hasSize(33)))
                .andExpect(jsonPath("$[1].options[15].code").value("food_beverage"))
                .andExpect(jsonPath("$[1].options[15].label").value("Food & Beverage"))
                .andExpect(jsonPath("$[1].options[15].active").value(true))
                .andExpect(jsonPath("$[2].options[0].label").value("1–10"));
        mockMvc.perform(get("/api/picklists/contact").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fieldKey", contains("salutation")))
                .andExpect(jsonPath("$[0].options[*].code", contains("mr", "ms", "mrs", "mx", "dr", "prof")));
        mockMvc.perform(get("/api/picklists/deal").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/picklists/nope").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRecordStoresTheCodeAndRefusesFreeText() throws Exception {
        mockMvc.perform(post("/api/companies").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"industry\":\"Software\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("industry"))
                .andExpect(jsonPath("$.details[0].reason").value("Not one of the options"));
        mockMvc.perform(post("/api/companies").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"industry\":\"technology\",\"employeeRange\":\"10001_plus\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.industry").value("technology"))
                .andExpect(jsonPath("$.employeeRange").value("10001_plus"));
    }

    @Test
    void theBundleCarriesOptionsOnPicklistFieldsOnly() throws Exception {
        mockMvc.perform(get("/api/entities/company/schema").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'industry')].dataType").value(hasItem("PICKLIST")))
                .andExpect(jsonPath("$.fields[?(@.key == 'industry')].options[*]", hasSize(33)))
                .andExpect(jsonPath("$.fields[?(@.key == 'name')].options").value(hasItem((Object) null)));
    }

    @Test
    void aPutRelabelsReordersDeactivatesAndAddsInOneGo() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "industry");
        Map<String, Object> technology = find(options, "technology");
        options.remove(technology);
        options.addFirst(option("technology", "Tech & Software", true));
        find(options, "shipping").put("active", false);
        options.add(option(null, "Renewable Energy", true));
        options.add(option("space", "Space", true));

        putIndustry(options)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldKey").value("industry"))
                .andExpect(jsonPath("$.options", hasSize(35)))
                .andExpect(jsonPath("$.options[0].code").value("technology"))
                .andExpect(jsonPath("$.options[0].label").value("Tech & Software"))
                .andExpect(jsonPath("$.options[?(@.code == 'shipping')].active").value(hasItem(false)))
                .andExpect(jsonPath("$.options[33].code").value("renewable_energy"))
                .andExpect(jsonPath("$.options[34].code").value("space"));

        assertThat(stored(EntityType.COMPANY, "industry").getFirst()).containsEntry("label", "Tech & Software");
        // An inactive value can no longer be picked for a new record.
        mockMvc.perform(post("/api/companies").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"industry\":\"shipping\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNewLabelThatDerivesATakenCodeGetsASuffix() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "industry");
        find(options, "retail").put("label", "Retail (old)");
        options.add(option(null, "Retail", true));

        putIndustry(options)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[33].code").value("retail_2"));
    }

    @Test
    void aStoredCodeCantBeLeftOut() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "industry");
        options.remove(find(options, "shipping"));

        putIndustry(options)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("options"))
                .andExpect(jsonPath("$.details[0].reason")
                        .value("'shipping' is missing: deactivate a value instead of removing it"));
    }

    @Test
    void everyBrokenRuleComesBackInOne400() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "type");
        options.add(option(null, "  ", true));                 // 7: blank
        options.add(option(null, "x".repeat(101), true));      // 8: too long
        options.add(option(null, "PARTNER", true));            // 9: same as Partner, ignoring case
        options.add(option("Bad-Code", "Bad", true));          // 10: not a code
        options.add(option("customer", "Customer again", true)); // 11: code twice

        putPicklist("type", options)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", contains(
                        "options[7].label", "options[8].label", "options[9].label",
                        "options[10].code", "options[11].code")))
                .andExpect(jsonPath("$.details[2].reason").value("Same label as options[2]"));

        // Nothing was written.
        assertThat(stored(EntityType.COMPANY, "type")).hasSize(7);
    }

    @Test
    void accentsMakeADifferentLabelButCaseDoesNot() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "type");
        options.add(option(null, "Résellér", true));   // accent-sensitive: not the same as Reseller

        putPicklist("type", options).andExpect(status().isOk());
    }

    @Test
    void atLeastOneValueMustStayActive() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.CONTACT, "salutation");
        options.forEach(item -> item.put("active", false));

        mockMvc.perform(put("/api/picklists/contact/salutation").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("options", options))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].reason").value("At least one value must be active"));
    }

    @Test
    void aListIsCappedAt500Values() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "type");
        for (int i = options.size(); i <= PicklistService.MAX_VALUES; i++) {
            options.add(option(null, "Type " + i, true));
        }

        putPicklist("type", options)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].reason").value("At most 500 values"));
    }

    @Test
    void twoLabelsCanBeSwappedInOnePut() throws Exception {
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "type");
        find(options, "vendor").put("label", "Competitor");
        find(options, "competitor").put("label", "Vendor");

        putPicklist("type", options).andExpect(status().isOk());

        List<Map<String, Object>> after = stored(EntityType.COMPANY, "type");
        assertThat(find(after, "vendor")).containsEntry("label", "Competitor");
        assertThat(find(after, "competitor")).containsEntry("label", "Vendor");
    }

    @Test
    void theFieldKeyInThePathIsCaseInsensitive() throws Exception {
        mockMvc.perform(put("/api/picklists/COMPANY/INDUSTRY").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("options", stored(EntityType.COMPANY, "industry")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldKey").value("industry"));
    }

    @Test
    void aFieldThatIsntAPicklistIs404() throws Exception {
        List<Map<String, Object>> options = List.of(option(null, "Acme", true));
        putPicklist("name", options).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        putPicklist("no_such_field", options).andExpect(status().isNotFound());
    }

    @Test
    void aPutChangesTheBundlesEtag() throws Exception {
        String before = schemaEtag();
        List<Map<String, Object>> options = stored(EntityType.COMPANY, "industry");
        find(options, "retail").put("label", "Retail & Commerce");

        putIndustry(options).andExpect(status().isOk());

        assertThat(schemaEtag()).isNotEqualTo(before);
    }
}
