package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Refuses to start when a permission table's field columns don't match the fields:
 * Hibernate maps no class to these tables, so ddl-auto=validate can't. The comparison is
 * a pure function, unit-tested without a database.
 */
@Component
class PermissionColumnsVerifier implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(PermissionColumnsVerifier.class);

    private static final String COLUMNS = """
            select column_name
            from information_schema.columns
            where table_schema = 'app'
              and table_name = ?""";

    private final JdbcClient jdbc;
    private final FieldPermissionCatalog catalog;

    PermissionColumnsVerifier(JdbcClient jdbc, FieldPermissionCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            String table = EntityPermissionRepository.table(type);
            List<String> columns = jdbc.sql(COLUMNS).param(table).query(String.class).list();
            problems.addAll(compare(table, catalog.expectedColumns(type), columns));
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Permission columns don't match the fields:\n  - "
                    + String.join("\n  - ", problems));
        }
        log.info("Permission columns verified");
    }

    /** Every missing and extra field column. can_read and can_edit end the same way but are CRUD. */
    static List<String> compare(String table, Set<String> expected, List<String> columns) {
        Set<String> actual = new TreeSet<>();
        for (String column : columns) {
            boolean fieldColumn = column.endsWith("_read") || column.endsWith("_edit");
            if (fieldColumn && !column.equals(FieldPermissionCatalog.CAN_READ)
                    && !column.equals(FieldPermissionCatalog.CAN_EDIT)) {
                actual.add(column);
            }
        }

        List<String> problems = new ArrayList<>();
        new TreeSet<>(expected).stream()
                .filter(column -> !actual.contains(column))
                .forEach(column -> problems.add("app." + table + " has no column " + column));
        actual.stream()
                .filter(column -> !expected.contains(column))
                .forEach(column -> problems.add("app." + table + " has an extra column " + column));
        return problems;
    }
}
