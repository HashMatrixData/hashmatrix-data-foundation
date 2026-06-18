package io.hashmatrix.data.connector.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 方言按 URL 选取 + 兜底回退；引用规则差异。 */
class DialectRegistryTest {

    private final DialectRegistry registry = DialectRegistry.load();

    @Test
    void selects_builtin_dialects_by_url() {
        assertThat(registry.forUrl("jdbc:postgresql://host:5432/df_acme").name()).isEqualTo("postgresql");
        assertThat(registry.forUrl("jdbc:mysql://host:3306/df_acme").name()).isEqualTo("mysql");
    }

    @Test
    void falls_back_to_ansi_for_unknown_url() {
        assertThat(registry.forUrl("jdbc:h2:mem:df_acme").name()).isEqualTo("ansi");
    }

    @Test
    void quoting_rules_differ_per_dialect() {
        assertThat(registry.forUrl("jdbc:postgresql://h/db").quoteIdentifier("orders")).isEqualTo("\"orders\"");
        assertThat(registry.forUrl("jdbc:mysql://h/db").quoteIdentifier("orders")).isEqualTo("`orders`");
    }
}
