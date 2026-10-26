package com.comficonf.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigValuesTest {

    @Test
    void parsesAndBuildsPaths() {
        assertThat(ConfigPath.parse("vouchers[2].cooldown-seconds")).containsExactly("vouchers", 2, "cooldown-seconds");
        assertThat(ConfigPath.parse("")).isEmpty();
        assertThat(ConfigPath.parse("grid[0][12].cell")).containsExactly("grid", 0, 12, "cell");
        assertThat(ConfigPath.index(ConfigPath.child("settings", "disabled-worlds"), 0))
                .isEqualTo("settings.disabled-worlds[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = {".a", "a.", "a..b", "[0]", "a[x]", "a b", "a[0]b", "a.[0]", "a[0].[1]", "a[٣]",
            "a[１]"})
    void rejectsMalformedPaths(String path) {
        assertThatThrownBy(() -> ConfigPath.parse(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesNumbersListsAndMaps() {
        ConfigValues values = ConfigValues.of(Map.of("i", 5, "s", (short) 2, "f", 1.5f, "big", new BigDecimal("2.25"),
                "list", List.of("a", "b"), "nested", Map.of("n", 7)));
        assertThat(values.get("i")).isEqualTo(5L);
        assertThat(values.get("s")).isEqualTo(2L);
        assertThat(values.get("f")).isEqualTo(1.5);
        assertThat(values.get("big")).isEqualTo(2.25);
        assertThat(values.getStringList("list")).containsExactly("a", "b");
        assertThat(values.getLong("nested.n")).isEqualTo(7L);
        assertThat(values.getDouble("i")).isEqualTo(5.0);
        assertThatThrownBy(() -> values.asMap().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replacesAndRemovesValuesWithoutChangingTheOriginal() {
        ConfigValues original = ConfigValues.builder().put("settings.prefix", "a").put("items", List.of(
                Map.of("id", "x", "n", 1))).build();
        ConfigValues changed = original.with("items[0].n", 2).with("settings.extra", true).without("settings.prefix");
        assertThat(original.getString("settings.prefix")).isEqualTo("a");
        assertThat(original.getLong("items[0].n")).isEqualTo(1L);
        assertThat(changed.getLong("items[0].n")).isEqualTo(2L);
        assertThat(changed.contains("settings.prefix")).isFalse();
        assertThat(changed.getBoolean("settings.extra")).isTrue();
        assertThat(changed.getObjectList("items")).hasSize(1);
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThatThrownBy(() -> ConfigValues.of(Map.of("x", new Object()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigValues.of(Map.of("bad key", 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigValues.of(Map.of("x", Double.NaN))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesApplyResultInvariants() {
        ApplyResult applied = ApplyResult.applied("rev-2");
        assertThat(applied.persisted()).isTrue();
        assertThat(applied.activated()).isTrue();
        ApplyResult saved = ApplyResult.savedRestartRequired("rev-2", "restart", List.of("settings.storage-mode"));
        assertThat(saved.persisted()).isTrue();
        assertThat(saved.activated()).isFalse();
        assertThat(ApplyResult.conflict("rev-1").persisted()).isFalse();
        assertThat(ApplyResult.failed("broken").persisted()).isFalse();
        assertThatThrownBy(() -> ApplyResult.rejected(ValidationResult.ok())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplyResult.savedRestartRequired("rev", "m", List.of("bad path!")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
