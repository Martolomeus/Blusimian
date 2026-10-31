package com.blusimian.blusimian.platform.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Refuses to start the app when the database would let the app role around RLS: checks
 * C1-C8 of Architecture/multitenancy.md. A SmartInitializingSingleton, so it runs after
 * Flyway and before the web server opens its port. The rules are pure functions of the
 * query results, so they are unit-tested without a database.
 */
@Component
public class RowLevelSecurityVerifier implements SmartInitializingSingleton {

    static final String POLICY_NAME = "tenant_isolation";
    static final String TENANT_POLICY = "(tenant_id = ( SELECT app.current_tenant_id() AS current_tenant_id))";
    static final String TENANTS_TABLE = "tenants";
    static final String TENANTS_POLICY = "(id = ( SELECT app.current_tenant_id() AS current_tenant_id))";
    static final Set<String> DEFINER_FUNCTIONS = Set.of("find_login_user");

    private static final Logger log = LoggerFactory.getLogger(RowLevelSecurityVerifier.class);

    // C1. No superuser or BYPASSRLS role among the app role and the roles it belongs to.
    private static final String BYPASS_ROLES = """
            select r.rolname
            from pg_roles r
            where (r.rolsuper or r.rolbypassrls)
              and pg_has_role(current_user, r.oid, 'MEMBER')""";

    // C2. Nothing in app is owned by the app role or a role it belongs to.
    private static final String OWNED_RELATIONS = """
            select c.relname
            from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'app'
              and pg_has_role(current_user, c.relowner, 'MEMBER')""";

    // C3. Every table: RLS enabled, and a tenant_id column.
    private static final String TABLES = """
            select c.relname,
                   c.relrowsecurity as rls_enabled,
                   exists (select 1
                           from pg_attribute a
                           where a.attrelid = c.oid
                             and a.attname = 'tenant_id'
                             and not a.attisdropped) as has_tenant_id
            from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'app'
              and c.relkind in ('r', 'p')
              and c.relname <> 'flyway_schema_history'""";

    // C4. Every policy, as Postgres prints it.
    private static final String POLICIES = """
            select tablename, policyname, permissive, array_to_string(roles, ',') as roles,
                   cmd, qual, with_check
            from pg_policies
            where schemaname = 'app'""";

    // C5. No privilege beyond DML.
    private static final String EXTRA_PRIVILEGES = """
            select c.relname, p.privilege
            from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
                     cross join unnest(array ['TRUNCATE', 'REFERENCES', 'TRIGGER']) as p(privilege)
            where n.nspname = 'app'
              and c.relkind in ('r', 'p')
              and has_table_privilege(current_user, c.oid, p.privilege)""";

    // C6. No definer-rights views, no materialized views.
    private static final String UNSAFE_VIEWS = """
            select c.relname, c.relkind
            from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'app'
              and (c.relkind = 'm'
                or (c.relkind = 'v' and not exists (select 1
                                                     from pg_options_to_table(c.reloptions) o
                                                     where o.option_name = 'security_invoker'
                                                       and o.option_value::boolean)))""";

    // C7. SECURITY DEFINER functions, compared against the allowlist.
    private static final String DEFINER_FUNCTIONS_QUERY = """
            select p.proname
            from pg_proc p
                     join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'app'
              and p.prosecdef""";

    // C8. The connection carries no tenant of its own.
    private static final String SESSION_TENANT = "select coalesce(current_setting('app.tenant_id', true), '')";

    record Table(String name, boolean rlsEnabled, boolean hasTenantId) {
    }

    record Policy(String table, String name, boolean permissive, String roles, String command,
                  String using, String withCheck) {
    }

    record Privilege(String table, String privilege) {
    }

    record View(String name, String kind) {
    }

    private final DataSource dataSource;

    public RowLevelSecurityVerifier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = verify();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Row-level security is not enforced for the app role:\n  - "
                    + String.join("\n  - ", problems));
        }
        log.info("Row-level security verified (checks C1-C8 passed)");
    }

    // One short transaction, rolled back, only so search_path can be pinned to pg_catalog
    // for it: Postgres prints a policy's function schema-qualified only when the function
    // isn't on the search_path, and C4 compares that text.
    private List<String> verify() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
                jdbc.sql("select set_config('search_path', 'pg_catalog', true)").query().singleValue();
                return verify(jdbc);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify row-level security", e);
        }
    }

    private static List<String> verify(JdbcClient jdbc) {
        String role = jdbc.sql("select current_user").query(String.class).single();
        List<Table> tables = jdbc.sql(TABLES)
                .query((rs, row) -> new Table(rs.getString("relname"), rs.getBoolean("rls_enabled"),
                        rs.getBoolean("has_tenant_id")))
                .list();
        List<Policy> policies = jdbc.sql(POLICIES)
                .query((rs, row) -> new Policy(rs.getString("tablename"), rs.getString("policyname"),
                        "PERMISSIVE".equals(rs.getString("permissive")), rs.getString("roles"),
                        rs.getString("cmd"), rs.getString("qual"), rs.getString("with_check")))
                .list();

        List<String> problems = new ArrayList<>();
        problems.addAll(checkBypassRoles(jdbc.sql(BYPASS_ROLES).query(String.class).list()));
        problems.addAll(checkOwnedRelations(jdbc.sql(OWNED_RELATIONS).query(String.class).list()));
        problems.addAll(checkTables(tables));
        problems.addAll(checkPolicies(tables, policies, role));
        problems.addAll(checkPrivileges(jdbc.sql(EXTRA_PRIVILEGES)
                .query((rs, row) -> new Privilege(rs.getString("relname"), rs.getString("privilege")))
                .list()));
        problems.addAll(checkViews(jdbc.sql(UNSAFE_VIEWS)
                .query((rs, row) -> new View(rs.getString("relname"), rs.getString("relkind")))
                .list()));
        problems.addAll(checkDefinerFunctions(jdbc.sql(DEFINER_FUNCTIONS_QUERY).query(String.class).list()));
        problems.addAll(checkSessionTenant(jdbc.sql(SESSION_TENANT).query(String.class).single()));
        return problems;
    }

    static List<String> checkBypassRoles(List<String> roles) {
        return roles.stream()
                .map(role -> "C1: the app role is or belongs to " + role + ", which is superuser or BYPASSRLS")
                .toList();
    }

    static List<String> checkOwnedRelations(List<String> relations) {
        return relations.stream()
                .map(relation -> "C2: app." + relation + " is owned by the app role or a role it belongs to")
                .toList();
    }

    static List<String> checkTables(List<Table> tables) {
        List<String> problems = new ArrayList<>();
        for (Table table : tables) {
            if (!table.rlsEnabled()) {
                problems.add("C3: app." + table.name() + " has row-level security disabled");
            }
            if (!table.hasTenantId() && !TENANTS_TABLE.equals(table.name())) {
                problems.add("C3: app." + table.name() + " has no tenant_id column");
            }
        }
        return problems;
    }

    /** Exactly one permissive policy per table, the tenant_isolation one; any others must be restrictive. */
    static List<String> checkPolicies(List<Table> tables, List<Policy> policies, String role) {
        List<String> problems = new ArrayList<>();
        for (Table table : tables) {
            List<Policy> permissive = policies.stream()
                    .filter(policy -> policy.table().equals(table.name()) && policy.permissive())
                    .toList();
            if (permissive.size() != 1) {
                problems.add("C4: app." + table.name() + " has " + permissive.size()
                        + " permissive policies, expected exactly " + POLICY_NAME);
                continue;
            }
            Policy policy = permissive.getFirst();
            String expected = TENANTS_TABLE.equals(table.name()) ? TENANTS_POLICY : TENANT_POLICY;
            if (!POLICY_NAME.equals(policy.name())
                    || !"ALL".equals(policy.command())
                    || !role.equals(policy.roles())
                    || !expected.equals(policy.using())
                    || !expected.equals(policy.withCheck())) {
                problems.add("C4: app." + table.name() + " policy " + policy.name() + " is not exactly "
                        + POLICY_NAME + " for ALL to " + role + " using and with check " + expected
                        + " (found " + policy.command() + " to " + policy.roles() + " using "
                        + policy.using() + " with check " + policy.withCheck() + ")");
            }
        }
        return problems;
    }

    static List<String> checkPrivileges(List<Privilege> privileges) {
        return privileges.stream()
                .map(p -> "C5: the app role has " + p.privilege() + " on app." + p.table() + "; only DML is allowed")
                .toList();
    }

    static List<String> checkViews(List<View> views) {
        return views.stream()
                .map(view -> "m".equals(view.kind())
                        ? "C6: app." + view.name() + " is a materialized view, which can't have RLS"
                        : "C6: app." + view.name() + " is a view without security_invoker")
                .toList();
    }

    static List<String> checkDefinerFunctions(List<String> functions) {
        return functions.stream()
                .filter(function -> !DEFINER_FUNCTIONS.contains(function))
                .map(function -> "C7: app." + function + " is SECURITY DEFINER but not on the allowlist")
                .toList();
    }

    static List<String> checkSessionTenant(String setting) {
        return setting.isEmpty()
                ? List.of()
                : List.of("C8: connections start with app.tenant_id = '" + setting
                        + "' (a default on the role, database or JDBC URL)");
    }
}
