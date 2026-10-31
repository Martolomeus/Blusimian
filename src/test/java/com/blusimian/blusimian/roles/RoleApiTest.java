package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import com.blusimian.blusimian.users.UserService;
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
import static com.blusimian.blusimian.roles.RoleTestSupport.PASSWORD;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP contract: JSON shapes, status codes and what a body can't do. */
@SpringBootTest
@AutoConfigureMockMvc
class RoleApiTest {

    private static final String CLOSED_ENTITY = """
            {"canRead":false,"canCreate":false,"canEdit":false,"canDelete":false,"fields":{}}""";
    private static final String APP = """
            {"manageUsers":false,"manageRoles":true,"manageJobTitles":false,"manageLayouts":false,"manageCustomFields":false}""";

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleService roles;
    @Autowired
    UserService users;
    @Autowired
    MockMvc mockMvc;

    TenantId tenant;
    MockHttpSession session;

    @BeforeEach
    void logIn() throws Exception {
        tenant = provisionTenant(provisioning);
        session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"roles-" + tenant.value() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private static String body(String name, String contact) {
        return """
                {"name":"%s","app":%s,"entities":{"CONTACT":%s,"COMPANY":%s,"DEAL":%s}}"""
                .formatted(name, APP, contact, CLOSED_ENTITY, CLOSED_ENTITY);
    }

    private ResultActions postRole(String body) throws Exception {
        return mockMvc.perform(post("/api/roles").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID administrator() {
        return callAs(tenant, () -> roles.list()).stream()
                .filter(RoleSummaryResponse::isAdmin).findFirst().orElseThrow().id();
    }

    private UUID firstUser() {
        return callAs(tenant, () -> users.list()).getFirst().getId();
    }

    @Test
    void theDocumentSpeaksKeysAndNeverSlots() throws Exception {
        String contact = """
                {"canRead":true,"canCreate":false,"canEdit":false,"canDelete":false,
                 "fields":{"email":{"read":true,"edit":false}}}""";

        String response = postRole(body("Sales", contact))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sales"))
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.app.manageRoles").value(true))
                .andExpect(jsonPath("$.entities.CONTACT.canRead").value(true))
                .andExpect(jsonPath("$.entities.CONTACT.fields.email.read").value(true))
                .andExpect(jsonPath("$.entities.CONTACT.fields.email.edit").value(false))
                .andExpect(jsonPath("$.entities.CONTACT.fields.firstName.read").value(false))
                .andExpect(jsonPath("$.entities.DEAL.fields.dealName.read").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("cf_").doesNotContain("first_name");
    }

    @Test
    void aBodyCantMakeAnAdministrator() throws Exception {
        postRole(body("Sneaky", CLOSED_ENTITY).replaceFirst("\\{", "{\"isAdmin\":true,"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isAdmin").value(false));

        mockMvc.perform(get("/api/roles").session(session))
                .andExpect(jsonPath("$[?(@.isAdmin == true)].name", contains("Administrator")));
    }

    @Test
    void everyProblemComesBackInOneValidationError() throws Exception {
        String contact = """
                {"canRead":false,"canCreate":true,"canEdit":false,"canDelete":false,
                 "fields":{"nope":{"read":true,"edit":true},"email":{"read":false,"edit":true}}}""";

        postRole(body("  ", contact))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("name")))
                .andExpect(jsonPath("$.details[*].field", hasItem("entities.CONTACT.canRead")))
                .andExpect(jsonPath("$.details[*].field", hasItem("entities.CONTACT.fields.nope")))
                .andExpect(jsonPath("$.details[*].field", hasItem("entities.CONTACT.fields.email")))
                .andExpect(jsonPath("$.details[*].field", hasItem("entities.CONTACT.fields.firstName")));
    }

    @Test
    void aMalformedDocumentIsA400NotA500() throws Exception {
        postRole("{\"name\":\"x\",\"app\":" + APP + "}").andExpect(status().isBadRequest());
        postRole("{\"name\":\"x\",\"entities\":{}}").andExpect(status().isBadRequest());
        postRole("{\"name\":\"x\",\"app\":" + APP + ",\"entities\":{\"PLANET\":" + CLOSED_ENTITY + "}}")
                .andExpect(status().isBadRequest());
        postRole(body("x", "{\"canRead\":true,\"canCreate\":false,\"canEdit\":false,\"canDelete\":false}"))
                .andExpect(status().isBadRequest());
        postRole(body("x", """
                {"canRead":true,"canCreate":false,"canEdit":false,"canDelete":false,"fields":{"email":null}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingEntityIsNamed() throws Exception {
        postRole("{\"name\":\"x\",\"app\":" + APP + ",\"entities\":{\"CONTACT\":" + CLOSED_ENTITY + "}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].reason", hasItem("Missing COMPANY")))
                .andExpect(jsonPath("$.details[*].reason", hasItem("Missing DEAL")));
    }

    @Test
    void conflictsAre409s() throws Exception {
        postRole(body("Sales", CLOSED_ENTITY)).andExpect(status().isCreated());
        postRole(body("sales", CLOSED_ENTITY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("A role named 'sales' already exists"));

        mockMvc.perform(put("/api/roles/" + administrator()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Boss", CLOSED_ENTITY)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/roles/" + administrator()).session(session))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/users/" + firstUser() + "/roles/" + administrator()).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("At least one user must stay an Administrator."));
    }

    @Test
    void unknownIdsAre404s() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get("/api/roles/" + unknown).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/roles/" + unknown).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", CLOSED_ENTITY)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/roles/" + unknown).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/users/" + firstUser() + "/roles/" + unknown).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/users/" + unknown + "/roles/" + administrator()).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/users/" + unknown + "/roles/" + administrator()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignmentsAreListedAndChangedByPath() throws Exception {
        String created = postRole(body("Sales", CLOSED_ENTITY)).andReturn().getResponse().getContentAsString();
        String roleId = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String userId = firstUser().toString();

        mockMvc.perform(put("/api/users/" + userId + "/roles/" + roleId).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/role-assignments").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roleId == '" + roleId + "')].userId", contains(userId)));
        mockMvc.perform(get("/api/roles").session(session))
                .andExpect(jsonPath("$[?(@.id == '" + roleId + "')].userCount", contains(1)));

        mockMvc.perform(delete("/api/roles/" + roleId).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("1 user still has this role."));

        mockMvc.perform(delete("/api/users/" + userId + "/roles/" + roleId).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/roles/" + roleId).session(session)).andExpect(status().isNoContent());
    }

    @Test
    void everyEndpointNeedsASession() throws Exception {
        mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/role-assignments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + firstUser())).andExpect(status().isUnauthorized());
    }
}
