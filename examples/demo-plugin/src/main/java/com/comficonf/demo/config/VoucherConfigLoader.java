package com.comficonf.demo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/** Turns the plugin's {@code config.yml} into a {@link VoucherConfig}, reporting every problem at once. */
public final class VoucherConfigLoader {

    public static final Set<String> STORAGE_MODES = Set.of("yaml", "sqlite");
    public static final Set<String> CHANNELS = Set.of("chat", "actionbar", "title");
    public static final Set<String> RARITIES = Set.of("common", "rare", "epic", "legendary");

    private VoucherConfigLoader() {
    }

    /** Parses YAML bytes, keeping comments so the document can be saved again without losing them. */
    public static YamlConfiguration parse(byte[] content) throws InvalidConfigException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(new String(content, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException e) {
            throw new InvalidConfigException("config.yml is not valid YAML: " + firstLine(e.getMessage()), e);
        }
        return yaml;
    }

    /** Reads and loads a configuration file. */
    public static VoucherConfig read(Path file) throws IOException, InvalidConfigException {
        return load(parse(Files.readAllBytes(file)));
    }

    /** Builds the runtime configuration from a parsed document. */
    public static VoucherConfig load(ConfigurationSection root) throws InvalidConfigException {
        List<String> problems = new ArrayList<>();
        ConfigurationSection settings = root.getConfigurationSection("settings");
        VoucherSettings parsedSettings = null;
        if (settings == null) {
            problems.add("the settings section is missing");
        } else {
            parsedSettings = settings(settings, problems);
        }
        List<Voucher> vouchers = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("vouchers");
        if (section == null) {
            problems.add("the vouchers section is missing or is not a map of voucher ids");
        } else {
            for (String id : section.getKeys(false)) {
                ConfigurationSection voucher = section.getConfigurationSection(id);
                if (voucher == null) {
                    problems.add("vouchers." + id + " must be a section");
                    continue;
                }
                Voucher parsed = voucher(id, voucher, problems);
                if (parsed != null) {
                    vouchers.add(parsed);
                }
            }
        }
        if (!problems.isEmpty() || parsedSettings == null) {
            throw new InvalidConfigException(String.join("; ", problems));
        }
        return new VoucherConfig(parsedSettings, vouchers);
    }

    private static @Nullable VoucherSettings settings(ConfigurationSection s, List<String> problems) {
        int before = problems.size();
        String prefix = string(s, "prefix", "", problems);
        boolean announce = bool(s, "announce-redemptions", true, problems);
        List<String> channels = strings(s, "announcement-channels", problems);
        for (String channel : channels) {
            if (!CHANNELS.contains(channel)) {
                problems.add("settings.announcement-channels contains unknown channel '" + channel + "'");
            }
        }
        long max = whole(s, "max-redemptions-per-day", 0, problems);
        if (max < 0 || max > 100) {
            problems.add("settings.max-redemptions-per-day must be between 0 and 100");
        }
        double bonus = decimal(s, "bonus-multiplier", 1.0, problems);
        if (bonus < 0 || bonus > 10) {
            problems.add("settings.bonus-multiplier must be between 0.0 and 10.0");
        }
        String storage = string(s, "storage-mode", "yaml", problems);
        if (!STORAGE_MODES.contains(storage)) {
            problems.add("settings.storage-mode must be yaml or sqlite");
        }
        List<String> worlds = strings(s, "disabled-worlds", problems);
        String welcome = string(s, "welcome-message", "", problems);
        String webhook = string(s, "webhook-url", "", problems);
        if (problems.size() > before) {
            return null;
        }
        return new VoucherSettings(prefix, announce, channels, (int) max, bonus, storage, worlds, welcome, webhook);
    }

    private static @Nullable Voucher voucher(String id, ConfigurationSection v, List<String> problems) {
        int before = problems.size();
        String path = "vouchers." + id;
        String name = string(v, "display-name", null, problems);
        boolean enabled = bool(v, "enabled", true, problems);
        String material = string(v, "material", null, problems);
        long cooldown = whole(v, "cooldown-seconds", 0, problems);
        if (cooldown < 0) {
            problems.add(path + ".cooldown-seconds must not be negative");
        }
        String permission = string(v, "permission", "vouchers." + id, problems);
        long weight = whole(v, "weight", 1, problems);
        if (weight < 1 || weight > 1000) {
            problems.add(path + ".weight must be between 1 and 1000");
        }
        String rarity = string(v, "rarity", "common", problems);
        if (!RARITIES.contains(rarity)) {
            problems.add(path + ".rarity must be common, rare, epic or legendary");
        }
        List<String> lore = strings(v, "lore", problems);
        if (problems.size() > before || name == null || material == null) {
            return null;
        }
        return new Voucher(id, name, enabled, material, cooldown, permission, (int) weight, rarity, lore);
    }

    private static @Nullable String string(ConfigurationSection s, String key, @Nullable String fallback,
            List<String> problems) {
        Object value = s.get(key);
        if (value == null) {
            if (fallback == null) {
                problems.add(s.getCurrentPath() + "." + key + " is missing");
            }
            return fallback;
        }
        if (!(value instanceof String text)) {
            problems.add(s.getCurrentPath() + "." + key + " must be text");
            return fallback;
        }
        return text;
    }

    private static boolean bool(ConfigurationSection s, String key, boolean fallback, List<String> problems) {
        Object value = s.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            problems.add(s.getCurrentPath() + "." + key + " must be true or false");
            return fallback;
        }
        return flag;
    }

    private static long whole(ConfigurationSection s, String key, long fallback, List<String> problems) {
        Object value = s.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Integer || value instanceof Long)) {
            problems.add(s.getCurrentPath() + "." + key + " must be a whole number");
            return fallback;
        }
        return ((Number) value).longValue();
    }

    private static double decimal(ConfigurationSection s, String key, double fallback, List<String> problems) {
        Object value = s.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            problems.add(s.getCurrentPath() + "." + key + " must be a number");
            return fallback;
        }
        return number.doubleValue();
    }

    private static List<String> strings(ConfigurationSection s, String key, List<String> problems) {
        Object value = s.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            problems.add(s.getCurrentPath() + "." + key + " must be a list");
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static String firstLine(@Nullable String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
