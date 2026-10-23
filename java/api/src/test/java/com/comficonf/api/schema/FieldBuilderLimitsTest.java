package com.comficonf.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FieldBuilderLimitsTest {

    @Test
    void limitsPatternsToTheLengthsOfTheSchemaFormat() {
        String pattern = "a{1,5}" + "|b".repeat(497);
        assertThat(pattern).hasSize(1000);
        String message = "m".repeat(300);
        assertThat(ConfigField.string("s").pattern(pattern, message).build().pattern()).isEqualTo(pattern);
        assertThat(ConfigField.stringList("l").itemPattern(pattern, message).build().patternMessage())
                .isEqualTo(message);

        assertThatThrownBy(() -> ConfigField.string("s").pattern(pattern + "|c").build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("pattern must be at most 1000");
        assertThatThrownBy(() -> ConfigField.stringList("l").itemPattern(pattern + "|c", "m").build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("pattern must be at most 1000");
        assertThatThrownBy(() -> ConfigField.string("s").pattern("a+", message + "!").build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("pattern message must be at most 300");
        assertThatThrownBy(() -> ConfigField.stringList("l").itemPattern("a+", message + "!").build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("pattern message must be at most 300");
    }

    @Test
    void limitsIntegerAttributesToTheSafeRange() {
        long safe = 9_007_199_254_740_991L;
        ConfigField widest = ConfigField.integer("n").range(-safe, safe).step(safe).defaultValue(-safe).slider()
                .build();
        assertThat(widest.min()).isEqualTo(-safe);
        assertThat(widest.max()).isEqualTo(safe);

        assertThatThrownBy(() -> ConfigField.integer("n").min(Long.MIN_VALUE).build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("min must be within");
        assertThatThrownBy(() -> ConfigField.integer("n").range(Long.MIN_VALUE, 10).slider().build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("min must be within");
        assertThatThrownBy(() -> ConfigField.integer("n").max(safe + 1).build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("max must be within");
        assertThatThrownBy(() -> ConfigField.integer("n").step(Long.MAX_VALUE).build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("step must be within");
        assertThatThrownBy(() -> ConfigField.integer("n").defaultValue(Long.MAX_VALUE).build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("default value must be within");
        assertThatThrownBy(() -> ConfigField.integer("n").defaultValue(Long.MIN_VALUE).build())
                .isInstanceOf(SchemaException.class).hasMessageContaining("default value must be within");
    }

    @Test
    void rejectsIntegerVisibilityValuesOutsideTheSafeRange() {
        long safe = 9_007_199_254_740_991L;
        assertThat(ConfigSchema.of(ConfigField.integer("n"),
                ConfigField.bool("b").visibleWhen(VisibilityCondition.whenEquals("n", safe))).fields()).hasSize(2);
        assertThatThrownBy(() -> ConfigSchema.of(ConfigField.integer("n"),
                ConfigField.bool("b").visibleWhen(VisibilityCondition.whenEquals("n", Long.MAX_VALUE))))
                .isInstanceOf(SchemaException.class).hasMessageContaining("incompatible value");
        assertThatThrownBy(() -> ConfigSchema.of(ConfigField.integer("n"),
                ConfigField.bool("b").visibleWhen(VisibilityCondition.whenIn("n", 1L, -safe - 1))))
                .isInstanceOf(SchemaException.class).hasMessageContaining("incompatible value");
    }
}
