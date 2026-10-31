package com.blusimian.blusimian.roles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The startup comparison, without a database. */
class PermissionColumnsVerifierTest {

    private static final Set<String> EXPECTED = Set.of("email_read", "email_edit");
    private static final List<String> BOOKKEEPING = List.of("id", "tenant_id", "role_id", "created_at", "updated_at");

    private static List<String> columns(String... fieldColumns) {
        List<String> columns = new java.util.ArrayList<>(BOOKKEEPING);
        columns.addAll(List.of("can_read", "can_create", "can_edit", "can_delete"));
        columns.addAll(List.of(fieldColumns));
        return columns;
    }

    @Test
    void aMatchingListPasses() {
        assertThat(PermissionColumnsVerifier.compare("t", EXPECTED, columns("email_read", "email_edit"))).isEmpty();
    }

    @Test
    void crudColumnsArentMistakenForFieldColumns() {
        // can_read and can_edit end like field columns, so a plain suffix match reports them as extra
        assertThat(PermissionColumnsVerifier.compare("t", Set.of(), columns())).isEmpty();
    }

    @Test
    void aMissingColumnIsReportedByName() {
        assertThat(PermissionColumnsVerifier.compare("t", EXPECTED, columns("email_read")))
                .containsExactly("app.t has no column email_edit");
    }

    @Test
    void anExtraColumnIsReportedByName() {
        assertThat(PermissionColumnsVerifier.compare("t", EXPECTED, columns("email_read", "email_edit", "fax_read")))
                .containsExactly("app.t has an extra column fax_read");
    }
}
