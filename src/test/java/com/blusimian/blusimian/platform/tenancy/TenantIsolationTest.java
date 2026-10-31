package com.blusimian.blusimian.platform.tenancy;

import com.blusimian.blusimian.companies.Company;
import com.blusimian.blusimian.companies.CompanyRepository;
import com.blusimian.blusimian.companies.CompanyService;
import com.blusimian.blusimian.companies.exception.CompanyNotFoundException;
import com.blusimian.blusimian.companyContacts.CompanyContactRequest;
import com.blusimian.blusimian.companyContacts.CompanyContactService;
import com.blusimian.blusimian.contacts.ContactService;
import com.blusimian.blusimian.customFields.CustomFieldDefinitionRequest;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.customFields.exception.DuplicateCustomFieldKeyException;
import com.blusimian.blusimian.deals.DealService;
import com.blusimian.blusimian.deals.exception.InvalidDealCustomerException;
import com.blusimian.blusimian.entityLayouts.EntityLayoutService;
import com.blusimian.blusimian.entityLayouts.dto.CreateEntityLayoutRequest;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionRequest;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.security.LoginLookup;
import com.blusimian.blusimian.roles.RoleAssignmentService;
import com.blusimian.blusimian.roles.RoleService;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import com.blusimian.blusimian.roles.exception.RoleNotFoundException;
import com.blusimian.blusimian.tenants.NewTenant;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import com.blusimian.blusimian.users.UserService;
import com.blusimian.blusimian.users.exception.UserNotFoundException;
import com.blusimian.blusimian.users.exception.UsernameTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation against the dev database, as blusimian_app. Every test provisions two
 * fresh tenants, A and B, instead of rolling back: a test transaction would begin before a
 * tenant could be bound.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationTest {

    static final String PASSWORD = "isolation-password";

    record TestTenant(TenantId id, String username) {
    }

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    CompanyService companies;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    ContactService contacts;
    @Autowired
    DealService deals;
    @Autowired
    CompanyContactService companyContacts;
    @Autowired
    CustomFieldService customFields;
    @Autowired
    EntityLayoutService layouts;
    @Autowired
    UserService users;
    @Autowired
    RoleService roles;
    @Autowired
    RoleAssignmentService roleAssignments;
    @Autowired
    LoginLookup loginLookup;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    MockMvc mockMvc;

    TestTenant a;
    TestTenant b;

    @BeforeEach
    void provisionTwoFreshTenants() {
        a = provision(provisioning, "A");
        b = provision(provisioning, "B");
    }

    static TestTenant provision(TenantProvisioningService provisioning, String name) {
        TenantId id = new TenantId(UUID.randomUUID());
        String username = "isolation-" + id.value();   // usernames are global, so unique per run
        provisioning.provision(new NewTenant(id, "Isolation " + name, username, "Admin " + name, PASSWORD));
        return new TestTenant(id, username);
    }

    private UUID createCompany(TestTenant tenant, String name) {
        return (UUID) callAs(tenant.id(), () -> companies.create(Map.of("name", name))).get("id");
    }

    private UUID firstUserOf(TestTenant tenant) {
        return callAs(tenant.id(), () -> users.list()).getFirst().getId();
    }

    private RoleSummaryResponse administratorOf(TestTenant tenant) {
        return callAs(tenant.id(), () -> roles.list()).stream()
                .filter(RoleSummaryResponse::isAdmin).findFirst().orElseThrow();
    }

    private static CustomFieldDefinitionRequest textField(String key) {
        return new CustomFieldDefinitionRequest(key, key, FieldDataType.TEXT, false, 0);
    }

    // 1
    @Test
    void anotherTenantsCompanyCantBeListedReadUpdatedOrDeleted() {
        UUID id = createCompany(a, "Rockets");

        assertThat(callAs(b.id(), () -> companies.list())).extracting(record -> record.get("id")).doesNotContain(id);
        assertThatThrownBy(() -> callAs(b.id(), () -> companies.get(id)))
                .isInstanceOf(CompanyNotFoundException.class);
        assertThatThrownBy(() -> callAs(b.id(), () -> companies.update(id, Map.of("name", "Hijacked"))))
                .isInstanceOf(CompanyNotFoundException.class);
        assertThatThrownBy(() -> runAs(b.id(), () -> companies.delete(id)))
                .isInstanceOf(CompanyNotFoundException.class);

        assertThat(callAs(a.id(), () -> companies.get(id))).containsEntry("name", "Rockets");
    }

    // 2
    @Test
    void rawSqlCantInsertARowForAnotherTenant() {
        assertThatThrownBy(() -> runAs(b.id(), () -> transaction.executeWithoutResult(status ->
                jdbc.sql("insert into app.companies (tenant_id, name) values (?, 'Planted')")
                        .param(a.id().value())
                        .update())))
                .rootCause().hasMessageContaining("violates row-level security policy");
    }

    // 3
    @Test
    void rawSqlCantMoveARowToAnotherTenant() {
        UUID id = createCompany(b, "Mine");

        assertThatThrownBy(() -> runAs(b.id(), () -> transaction.executeWithoutResult(status ->
                jdbc.sql("update app.companies set tenant_id = ? where id = ?")
                        .param(a.id().value())
                        .param(id)
                        .update())))
                .rootCause().hasMessageContaining("violates row-level security policy");
    }

    // 4: the composite foreign key, not a lookup, rejects it
    @Test
    void aDealCantReferenceAnotherTenantsCompany() {
        UUID companyOfA = createCompany(a, "A's customer");

        assertThatThrownBy(() -> callAs(b.id(), () ->
                deals.create(Map.of("dealName", "Poach", "customerId", companyOfA.toString()))))
                .isInstanceOf(InvalidDealCustomerException.class);
    }

    // 5
    @Test
    void aContactCantBeLinkedToAnotherTenantsCompany() {
        UUID companyOfA = createCompany(a, "A's company");
        UUID contactOfB = (UUID) callAs(b.id(), () ->
                contacts.create(Map.of("firstName", "Bea", "lastName", "Bee"))).get("id");

        assertThatThrownBy(() -> callAs(b.id(), () ->
                companyContacts.create(new CompanyContactRequest(companyOfA, contactOfB))))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // 6
    @Test
    void tenantsAllocateCustomFieldSlotsIndependently() {
        assertThat(callAs(a.id(), () -> customFields.create(EntityType.COMPANY, textField("region")))
                .getColumnName()).isEqualTo("cf_text_01");
        assertThat(callAs(b.id(), () -> customFields.create(EntityType.COMPANY, textField("region")))
                .getColumnName()).isEqualTo("cf_text_01");
    }

    // 7: plain equality, case-insensitive through the column's collation
    @Test
    void customFieldKeysCollideRegardlessOfCase() {
        callAs(a.id(), () -> customFields.create(EntityType.COMPANY, textField("Region")));

        assertThatThrownBy(() -> callAs(a.id(), () -> customFields.create(EntityType.COMPANY, textField("region"))))
                .isInstanceOf(DuplicateCustomFieldKeyException.class);
    }

    // 8
    @Test
    void changingADefaultLayoutLeavesOtherTenantsAlone() {
        UUID defaultOfB = callAs(b.id(), () -> layouts.resolve(EntityType.COMPANY).orElseThrow().id());
        UUID second = callAs(a.id(), () -> layouts.create(new CreateEntityLayoutRequest(EntityType.COMPANY,
                "Second", List.of(new EntityLayoutSectionRequest("Main", 1, List.of("name")))))).id();

        runAs(a.id(), () -> layouts.setDefault(second));

        assertThat(callAs(a.id(), () -> layouts.resolve(EntityType.COMPANY).orElseThrow().id())).isEqualTo(second);
        assertThat(callAs(b.id(), () -> layouts.resolve(EntityType.COMPANY).orElseThrow().id())).isEqualTo(defaultOfB);
    }

    // 9
    @Test
    void aRepositoryCallWithoutATenantFailsRatherThanReturningNothing() {
        assertThatThrownBy(() -> companyRepository.findAll())
                .isInstanceOf(CannotCreateTransactionException.class)
                .hasCauseInstanceOf(MissingTenantContextException.class);
    }

    // 10
    @Test
    void theTenantCantBeSwitchedInsideATransaction() {
        runAs(a.id(), () -> transaction.executeWithoutResult(status -> {
            assertThatThrownBy(() -> runAs(b.id(), () -> {
            })).isInstanceOf(IllegalStateException.class);

            AtomicBoolean ran = new AtomicBoolean();
            runAs(a.id(), () -> ran.set(true));
            assertThat(ran).isTrue();
        }));
    }

    // 11 is PooledConnectionTenantTest: it needs a pool of one connection.

    // 12
    @Test
    void otherThreadsDontInheritTheTenant() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<List<Company>> future = callAs(a.id(), () -> executor.submit(() -> companyRepository.findAll()));

            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .rootCause().isInstanceOf(MissingTenantContextException.class);
        }
    }

    // 13
    @Test
    void theLoginLookupFindsAnyTenantsUserCaseInsensitivelyWithNoTenantBound() {
        assertThat(TenantContext.current()).isEmpty();

        assertThat(loginLookup.find(a.username().toUpperCase())).hasValueSatisfying(user -> {
            assertThat(user.tenantId()).isEqualTo(a.id());
            assertThat(user.getUsername()).isEqualTo(a.username());
        });
    }

    // 14
    @Test
    void aLoggedInUserSeesOnlyTheirOwnTenant() throws Exception {
        createCompany(a, "Visible to A");
        createCompany(b, "Visible to B");
        MockHttpSession session = login(a.username(), PASSWORD);

        mockMvc.perform(get("/api/companies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Visible to A")));
        mockMvc.perform(get("/api/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", contains(a.username())))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(a.username()))
                .andExpect(jsonPath("$.tenant.id").value(a.id().toString()))
                .andExpect(jsonPath("$.tenant.name").value("Isolation A"));
    }

    // 15
    @Test
    void aRequestWithoutASessionIsA401WithAnApiErrorBody() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aUsernameTakenInAnotherTenantIsAConflictInAnyCase() {
        assertThatThrownBy(() -> callAs(b.id(), () -> users.create(a.username().toUpperCase(), "Copy", PASSWORD)))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void aWrongPasswordAndAnUnknownUserGetTheSame401() throws Exception {
        for (String username : List.of(a.username(), "no-such-user-" + UUID.randomUUID())) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(username, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(a.username(), PASSWORD);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisioningGivesEachTenantItsRolesAndMakesItsFirstUserAnAdministrator() {
        for (TestTenant tenant : List.of(a, b)) {
            assertThat(callAs(tenant.id(), () -> roles.list()))
                    .extracting(RoleSummaryResponse::name, RoleSummaryResponse::isAdmin, RoleSummaryResponse::userCount)
                    .containsExactly(tuple("Administrator", true, 1L), tuple("Standard user", false, 0L));

            UUID standardId = callAs(tenant.id(), () -> roles.list()).getLast().id();
            RoleResponse standard = callAs(tenant.id(), () -> roles.get(standardId));
            assertThat(standard.app().manageRoles()).isFalse();
            assertThat(standard.entities().values()).allSatisfy(entity -> {
                assertThat(entity.canRead() && entity.canCreate() && entity.canEdit() && entity.canDelete()).isTrue();
                assertThat(entity.fields().values()).allSatisfy(field ->
                        assertThat(field.read() && field.edit()).isTrue());
            });

            UUID administrator = administratorOf(tenant).id();
            assertThat(callAs(tenant.id(), () -> roleAssignments.list())).singleElement().satisfies(assignment -> {
                assertThat(assignment.getUserId()).isEqualTo(firstUserOf(tenant));
                assertThat(assignment.getRoleId()).isEqualTo(administrator);
            });
        }
    }

    @Test
    void theRoleListShowsOnlyTheTenantsRoles() {
        UUID administratorOfA = administratorOf(a).id();

        assertThat(callAs(b.id(), () -> roles.list()))
                .extracting(RoleSummaryResponse::id).doesNotContain(administratorOfA);
        assertThatThrownBy(() -> callAs(b.id(), () -> roles.get(administratorOfA)))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void aGuessedRoleOrUserOfAnotherTenantCantBeAssigned() {
        UUID administratorOfA = administratorOf(a).id();
        UUID userOfA = firstUserOf(a);
        UUID userOfB = firstUserOf(b);
        UUID administratorOfB = administratorOf(b).id();

        assertThatThrownBy(() -> runAs(b.id(), () -> roleAssignments.assign(userOfB, administratorOfA)))
                .isInstanceOf(RoleNotFoundException.class);
        assertThatThrownBy(() -> runAs(b.id(), () -> roleAssignments.assign(userOfA, administratorOfB)))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> runAs(b.id(), () -> roleAssignments.unassign(userOfA, administratorOfA)))
                .isInstanceOf(UserNotFoundException.class);
    }

    // the composite foreign key, not a lookup, rejects it
    @Test
    void rawSqlCantAssignAnotherTenantsRole() {
        UUID administratorOfA = administratorOf(a).id();
        UUID userOfB = firstUserOf(b);

        assertThatThrownBy(() -> runAs(b.id(), () -> transaction.executeWithoutResult(status ->
                jdbc.sql("insert into app.role_assignments (user_id, role_id) values (?, ?)")
                        .param(userOfB)
                        .param(administratorOfA)
                        .update())))
                .rootCause().hasMessageContaining("fk_role_assignments_role_id");
    }

    @Test
    void anotherTenantsUserIsA404AndYourOwnCanBeRenamed() throws Exception {
        MockHttpSession session = login(a.username(), PASSWORD);
        UUID userOfA = firstUserOf(a);
        UUID userOfB = firstUserOf(b);

        mockMvc.perform(get("/api/users/" + userOfB).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/users/" + userOfB).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/users/" + userOfA).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"  Renamed  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.username").value(a.username()));
        mockMvc.perform(put("/api/users/" + userOfA).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users/" + userOfA).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
