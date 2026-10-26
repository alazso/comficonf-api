package com.comficonf.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.comficonf.api.ConfigValues;
import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.demo.config.VoucherConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** How {@link VoucherSchema#mapper()} maps the demo's {@code config.yml}. */
class VoucherConfigMappingTest {

    private static final SectionMapper MAPPER = VoucherSchema.mapper();

    static byte[] defaultConfig() throws IOException {
        try (InputStream in = VoucherConfigMappingTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    static byte[] configWithUnknownKeys() throws IOException {
        // $1 is the line break before the comment, so the inserted line uses the file's CRLF or LF line breaks.
        String text = new String(defaultConfig(), StandardCharsets.UTF_8)
                .replaceFirst("(\\R)(  # MiniMessage text shown before every voucher message\\.)",
                        "$1  legacy-colors: false$1$2");
        assertThat(text).contains("legacy-colors: false");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static YamlConfiguration parse(byte[] bytes) throws Exception {
        return VoucherConfigLoader.parse(bytes);
    }

    @Test
    void readsDeclaredValuesWithTypesAndVoucherOrder() throws Exception {
        ConfigValues values = MAPPER.read(parse(defaultConfig()));
        assertThat(values.keys()).containsExactly("settings", "vouchers");
        assertThat(values.getObject("settings").keys()).containsExactly("prefix", "announce-redemptions",
                "announcement-channels", "max-redemptions-per-day", "bonus-multiplier", "storage-mode",
                "disabled-worlds", "welcome-message", "webhook-url");
        assertThat(values.get("settings.max-redemptions-per-day")).isEqualTo(5L);
        assertThat(values.get("settings.bonus-multiplier")).isEqualTo(1.5);
        assertThat(values.get("settings.announce-redemptions")).isEqualTo(true);
        assertThat(values.getStringList("settings.announcement-channels")).containsExactly("chat");
        assertThat(values.getStringList("settings.disabled-worlds")).containsExactly("world_nether");
        assertThat(values.getString("settings.welcome-message")).contains("\n");
        assertThat(values.get("settings.webhook-url")).isNull();
        assertThat(values.getObjectList("vouchers")).extracting(voucher -> voucher.getString("id"))
                .containsExactly("daily", "weekly", "monthly", "starter", "vip", "event");
        ConfigValues daily = values.getObjectList("vouchers").getFirst();
        assertThat(daily.keys()).containsExactly("id", "display-name", "enabled", "material", "cooldown-seconds",
                "permission", "weight", "rarity");
        assertThat(daily.get("cooldown-seconds")).isEqualTo(86_400L);
        assertThat(values.contains("config-version")).isFalse();
        assertThat(values.contains("vouchers[0].lore")).isFalse();
    }

    @Test
    void writesChangesAndKeepsEverythingTheSchemaDoesNotDeclare() throws Exception {
        YamlConfiguration yaml = parse(configWithUnknownKeys());
        ConfigValues values = MAPPER.read(yaml);
        List<Object> vouchers = new ArrayList<>();
        List<ConfigValues> existing = values.getObjectList("vouchers");
        vouchers.add(existing.get(1).with("cooldown-seconds", 3600L).asMap());
        vouchers.add(existing.get(0).asMap());
        Map<String, Object> summer = new LinkedHashMap<>();
        summer.put("id", "summer");
        summer.put("display-name", "Summer Gift");
        summer.put("enabled", true);
        summer.put("material", "EMERALD");
        summer.put("cooldown-seconds", 7200L);
        summer.put("permission", "vouchers.summer");
        summer.put("weight", 30L);
        summer.put("rarity", "rare");
        vouchers.add(summer);
        vouchers.add(existing.get(2).asMap());
        vouchers.add(existing.get(3).asMap());
        vouchers.add(existing.get(4).asMap());
        ConfigValues proposed = values
                .with("settings.prefix", "<aqua>[Rewards]</aqua> ")
                .with("settings.bonus-multiplier", 2.0)
                .with("settings.max-redemptions-per-day", 7L)
                .with("settings.announcement-channels", List.of("chat", "title"))
                .with("vouchers", vouchers);

        MAPPER.write(proposed, yaml);
        String saved = yaml.saveToString();
        YamlConfiguration reloaded = parse(saved.getBytes(StandardCharsets.UTF_8));

        assertThat(reloaded.get("config-version")).isEqualTo(3);
        assertThat(reloaded.getBoolean("metrics.enabled")).isFalse();
        assertThat(reloaded.get("metrics.report-interval-minutes")).isEqualTo(30);
        assertThat(reloaded.get("settings.legacy-colors")).isEqualTo(false);
        assertThat(reloaded.getString("settings.prefix")).isEqualTo("<aqua>[Rewards]</aqua> ");
        assertThat(reloaded.get("settings.bonus-multiplier")).isEqualTo(2.0);
        assertThat(reloaded.get("settings.max-redemptions-per-day")).isEqualTo(7);
        assertThat(reloaded.getStringList("settings.announcement-channels")).containsExactly("chat", "title");
        assertThat(reloaded.getString("settings.webhook-url")).isEmpty();
        assertThat(reloaded.getConfigurationSection("vouchers").getKeys(false))
                .containsExactly("weekly", "daily", "summer", "monthly", "starter", "vip");
        assertThat(reloaded.get("vouchers.weekly.cooldown-seconds")).isEqualTo(3600);
        assertThat(reloaded.getStringList("vouchers.daily.lore")).containsExactly("Claim once every day.");
        assertThat(reloaded.getStringList("vouchers.weekly.lore")).containsExactly("A bigger reward every week.");
        assertThat(reloaded.getString("vouchers.summer.material")).isEqualTo("EMERALD");
        assertThat(reloaded.get("vouchers.summer.weight")).isEqualTo(30);
        assertThat(reloaded.contains("vouchers.summer.lore")).isFalse();
        assertThat(reloaded.contains("vouchers.summer.id")).isFalse();
        assertThat(reloaded.contains("vouchers.event")).isFalse();
        assertThat(reloaded.getString("settings.welcome-message")).isEqualTo(
                values.getString("settings.welcome-message"));

        assertThat(saved).contains("# ComfiConfDemo configuration.")
                .contains("# MiniMessage text shown before every voucher message.")
                .contains("# Vouchers by id.")
                .contains("weight: 30\n");
        assertThat(MAPPER.read(reloaded)).isEqualTo(proposed);
    }

    @Test
    void roundTripsWithoutChangingTheFile() throws Exception {
        YamlConfiguration yaml = parse(defaultConfig());
        String before = yaml.saveToString();
        ConfigValues values = MAPPER.read(yaml);

        MAPPER.write(values, yaml);

        assertThat(yaml.saveToString()).isEqualTo(before);
        YamlConfiguration reloaded = parse(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
        assertThat(MAPPER.read(reloaded)).isEqualTo(values);
        assertThat(VoucherConfigLoader.load(reloaded).vouchers()).hasSize(6);
    }

    @Test
    void storesAWebhookAndClearsItToAnEmptyString() throws Exception {
        YamlConfiguration yaml = parse(defaultConfig());
        ConfigValues values = MAPPER.read(yaml);
        MAPPER.write(values.with("settings.webhook-url", "https://hooks.example/a"), yaml);
        assertThat(yaml.getString("settings.webhook-url")).isEqualTo("https://hooks.example/a");
        assertThat(MAPPER.read(yaml).getString("settings.webhook-url")).isEqualTo("https://hooks.example/a");
        MAPPER.write(values.with("settings.webhook-url", null), yaml);
        assertThat(yaml.get("settings.webhook-url")).isEqualTo("");
        assertThat(yaml.saveToString()).contains("# Optional https:// webhook notified on every redemption.");
    }
}
