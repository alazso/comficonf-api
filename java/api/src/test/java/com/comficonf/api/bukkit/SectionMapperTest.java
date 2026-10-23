package com.comficonf.api.bukkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.comficonf.api.ConfigValues;
import com.comficonf.api.ValidationIssue;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SectionMapperTest {

    private static final ConfigSchema SCHEMA = ConfigSchema.of(
            ConfigField.string("name"),
            ConfigField.text("motd"),
            ConfigField.bool("enabled"),
            ConfigField.integer("max-players"),
            ConfigField.decimal("multiplier"),
            ConfigField.enumeration("mode").choice("easy").choice("hard"),
            ConfigField.stringList("worlds"),
            ConfigField.material("icon"),
            ConfigField.string("token").optional().writeOnly(),
            ConfigField.object("limits").fields(
                    ConfigField.integer("per-day"),
                    ConfigField.object("nested").fields(ConfigField.bool("flag").optional())),
            ConfigField.objectList("rewards").itemKey("id").fields(
                    ConfigField.string("id"),
                    ConfigField.integer("amount"),
                    ConfigField.stringList("commands").optional()),
            ConfigField.objectList("kits").itemKey("id").fields(
                    ConfigField.string("id"),
                    ConfigField.material("icon"),
                    ConfigField.objectList("items").fields(
                            ConfigField.string("name"),
                            ConfigField.integer("count"))));

    private static final SectionMapper MAPPER = SectionMapper.builder(SCHEMA).keyedSection("kits").build();

    private static final String CONFIG = """
            # Layout version. Not editable on the website.
            config-version: 3
            # Server name shown in the tab list.
            name: Survival
            motd: |-
              Welcome!
              Have fun.
            enabled: true
            max-players: 50
            # Multiplier applied to rewards.
            multiplier: 2
            mode: easy
            worlds:
            - world
            - world_nether
            icon: DIAMOND
            token: 12345
            limits:
              per-day: 5
              nested:
                flag: false
              extra: kept
            rewards:
            - id: daily
              amount: 10
              note: kept
            - id: weekly
              amount: 50
              commands:
              - give %player% diamond 1
            kits:
              # The kit new players get.
              starter:
                icon: WOODEN_SWORD
                lore:
                - First steps.
                items:
                - name: sword
                  count: 1
                  enchanted: false
              pro:
                icon: DIAMOND_SWORD
                items: []
            """;

    private static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration reload(YamlConfiguration yaml) throws InvalidConfigurationException {
        return parse(yaml.saveToString());
    }

    private static Map<String, Object> item(Object... keysAndValues) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            item.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return item;
    }

    @Test
    void readsEveryFieldType() throws Exception {
        ConfigValues values = MAPPER.read(parse(CONFIG));

        assertThat(values.keys()).containsExactly("name", "motd", "enabled", "max-players", "multiplier", "mode",
                "worlds", "icon", "token", "limits", "rewards", "kits");
        assertThat(values.get("name")).isEqualTo("Survival");
        assertThat(values.get("motd")).isEqualTo("Welcome!\nHave fun.");
        assertThat(values.get("enabled")).isEqualTo(true);
        assertThat(values.get("max-players")).isEqualTo(50L);
        assertThat(values.get("multiplier")).isEqualTo(2.0);
        assertThat(values.get("mode")).isEqualTo("easy");
        assertThat(values.get("worlds")).isEqualTo(List.of("world", "world_nether"));
        assertThat(values.get("icon")).isEqualTo("DIAMOND");
        assertThat(values.get("token")).isEqualTo("12345");
        assertThat(values.get("limits.per-day")).isEqualTo(5L);
        assertThat(values.get("limits.nested.flag")).isEqualTo(false);
        assertThat(values.getObjectList("rewards")).extracting(ConfigValues::asMap).containsExactly(
                item("id", "daily", "amount", 10L, "commands", null),
                item("id", "weekly", "amount", 50L, "commands", List.of("give %player% diamond 1")));
        assertThat(values.getObjectList("kits")).extracting(ConfigValues::asMap).containsExactly(
                item("id", "starter", "icon", "WOODEN_SWORD", "items", List.of(item("name", "sword", "count", 1L))),
                item("id", "pro", "icon", "DIAMOND_SWORD", "items", List.of()));

        assertThat(values.contains("config-version")).isFalse();
        assertThat(values.contains("limits.extra")).isFalse();
        assertThat(values.contains("rewards[0].note")).isFalse();
        assertThat(values.contains("kits[0].lore")).isFalse();
        assertThat(values.contains("kits[0].items[0].enchanted")).isFalse();
    }

    @Test
    void writingTheValuesThatWereReadChangesNothing() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        String before = yaml.saveToString();

        MAPPER.write(MAPPER.read(yaml), yaml);

        assertThat(yaml.saveToString()).isEqualTo(before).contains("multiplier: 2\n").contains("token: 12345\n");
    }

    @Test
    void writesEveryFieldTypeAndKeepsUndeclaredKeysAndComments() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        ConfigValues proposed = MAPPER.read(yaml)
                .with("name", "Creative")
                .with("motd", "Line one\nLine two")
                .with("enabled", false)
                .with("max-players", 5_000_000_000L)
                .with("multiplier", 1.5)
                .with("mode", "hard")
                .with("worlds", List.of("lobby"))
                .with("icon", "EMERALD")
                .with("token", "s3cret")
                .with("limits.per-day", 7L)
                .with("limits.nested.flag", true)
                .with("rewards[0].amount", 12L)
                .with("rewards[1].commands", List.of("say hi", "say bye"))
                .with("kits[0].icon", "STONE_SWORD")
                .with("kits[0].items[0].count", 2L);

        MAPPER.write(proposed, yaml);
        String saved = yaml.saveToString();
        YamlConfiguration reloaded = parse(saved);

        assertThat(MAPPER.read(reloaded)).isEqualTo(proposed);
        assertThat(reloaded.get("name")).isEqualTo("Creative");
        assertThat(reloaded.get("enabled")).isEqualTo(false);
        assertThat(reloaded.get("max-players")).isEqualTo(5_000_000_000L);
        assertThat(reloaded.get("multiplier")).isEqualTo(1.5);
        assertThat(reloaded.get("limits.per-day")).isEqualTo(7);
        assertThat(reloaded.getStringList("worlds")).containsExactly("lobby");
        assertThat(reloaded.get("kits.starter.items")).isEqualTo(
                List.of(item("name", "sword", "count", 2, "enchanted", false)));

        assertThat(reloaded.get("config-version")).isEqualTo(3);
        assertThat(reloaded.get("limits.extra")).isEqualTo("kept");
        assertThat(reloaded.getMapList("rewards").getFirst().get("note")).isEqualTo("kept");
        assertThat(reloaded.getStringList("kits.starter.lore")).containsExactly("First steps.");
        assertThat(saved).contains("# Layout version. Not editable on the website.\n")
                .contains("# Server name shown in the tab list.\nname: Creative\n")
                .contains("# Multiplier applied to rewards.\nmultiplier: 1.5\n")
                .contains("  # The kit new players get.\n  starter:\n");
    }

    @Test
    void storesIntegersAsTheTypesYamlLoads() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigValues values = ConfigValues.builder().put("max-players", 20L).put("limits.per-day", 3_000_000_000L)
                .build();

        MAPPER.write(values, yaml);

        assertThat(yaml.get("max-players")).isEqualTo(20);
        assertThat(yaml.isInt("max-players")).isTrue();
        assertThat(yaml.get("limits.per-day")).isEqualTo(3_000_000_000L);
    }

    @Test
    void keyedSectionsFollowTheProposedOrderAndKeepEachItemsSectionAndComments() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        ConfigValues values = MAPPER.read(yaml);
        List<ConfigValues> kits = values.getObjectList("kits");
        List<Object> proposedKits = new ArrayList<>();
        proposedKits.add(item("id", "vip", "icon", "NETHERITE_SWORD", "items", List.of()));
        proposedKits.add(kits.get(0).with("icon", "IRON_SWORD").asMap());

        MAPPER.write(values.with("kits", proposedKits), yaml);
        String saved = yaml.saveToString();
        YamlConfiguration reloaded = parse(saved);

        assertThat(reloaded.getConfigurationSection("kits").getKeys(false)).containsExactly("vip", "starter");
        assertThat(reloaded.get("kits.starter.icon")).isEqualTo("IRON_SWORD");
        assertThat(reloaded.getStringList("kits.starter.lore")).containsExactly("First steps.");
        assertThat(reloaded.contains("kits.starter.id")).isFalse();
        assertThat(reloaded.getConfigurationSection("kits.vip").getKeys(false)).containsExactly("icon");
        assertThat(reloaded.contains("kits.pro")).isFalse();
        assertThat(saved).contains("  # The kit new players get.\n  starter:\n");
        assertThat(MAPPER.read(reloaded).getObjectList("kits")).extracting(kit -> kit.getString("id"))
                .containsExactly("vip", "starter");
    }

    @Test
    void listItemsKeepUndeclaredKeysOfTheStoredItemWithTheSameKey() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        ConfigValues values = MAPPER.read(yaml);
        List<ConfigValues> rewards = values.getObjectList("rewards");

        MAPPER.write(values.with("rewards", List.of(rewards.get(1).asMap(), rewards.get(0).asMap(),
                item("id", "monthly", "amount", 100L, "commands", null))), yaml);
        YamlConfiguration reloaded = reload(yaml);

        assertThat(reloaded.getMapList("rewards")).containsExactly(
                item("id", "weekly", "amount", 50, "commands", List.of("give %player% diamond 1")),
                item("id", "daily", "amount", 10, "note", "kept"),
                item("id", "monthly", "amount", 100));
    }

    @Test
    void listItemsWithoutAnItemKeyAreMatchedByPosition() throws Exception {
        ConfigSchema schema = ConfigSchema.of(ConfigField.objectList("steps").fields(ConfigField.string("say")));
        SectionMapper mapper = SectionMapper.of(schema);
        YamlConfiguration yaml = parse("""
                steps:
                - say: one
                  delay: 5
                -
                - say: two
                """);
        ConfigValues values = mapper.read(yaml);
        assertThat(values.getObjectList("steps")).extracting(step -> step.getString("say")).containsExactly("one",
                "two");

        mapper.write(values.with("steps[1].say", "three"), yaml);

        assertThat(reload(yaml).getMapList("steps")).containsExactly(item("say", "one", "delay", 5),
                item("say", "three"));
    }

    @Test
    void keyedSectionsInsideListItemsAreMapsKeyedByTheItemKey() throws Exception {
        ConfigSchema schema = ConfigSchema.of(ConfigField.objectList("shops").itemKey("name").fields(
                ConfigField.string("name"),
                ConfigField.objectList("offers").itemKey("item").fields(
                        ConfigField.string("item"),
                        ConfigField.integer("price"))));
        SectionMapper mapper = SectionMapper.builder(schema).keyedSection("shops.offers").build();
        YamlConfiguration yaml = parse("""
                shops:
                - name: farm
                  offers:
                    wheat:
                      price: 2
                      stock: 64
                    carrot:
                      price: 3
                """);
        ConfigValues values = mapper.read(yaml);
        assertThat(values.getObjectList("shops[0].offers")).extracting(ConfigValues::asMap).containsExactly(
                item("item", "wheat", "price", 2L), item("item", "carrot", "price", 3L));

        mapper.write(values.with("shops[0].offers", List.of(item("item", "carrot", "price", 4L),
                item("item", "wheat", "price", 2L), item("item", "potato", "price", 9L))), yaml);
        YamlConfiguration reloaded = reload(yaml);

        Map<?, ?> offers = (Map<?, ?>) reloaded.getMapList("shops").getFirst().get("offers");
        assertThat(List.copyOf(offers.keySet())).isEqualTo(List.of("carrot", "wheat", "potato"));
        assertThat(offers.get("wheat")).isEqualTo(item("price", 2, "stock", 64));
        assertThat(offers.get("carrot")).isEqualTo(item("price", 4));
        assertThat(offers.get("potato")).isEqualTo(item("price", 9));
        assertThat(mapper.read(reloaded).getObjectList("shops[0].offers")).extracting(o -> o.getString("item"))
                .containsExactly("carrot", "wheat", "potato");
    }

    @Test
    void optionalTextReadsAnEmptyStringAsNoValueAndWritesNoValueAsAnEmptyString() throws Exception {
        YamlConfiguration yaml = parse("token: ''\nname: ''\n");
        ConfigValues values = MAPPER.read(yaml);
        assertThat(values.get("token")).isNull();
        assertThat(values.get("name")).isEqualTo("");

        MAPPER.write(values, yaml);
        assertThat(yaml.saveToString()).contains("token: ''\n");

        YamlConfiguration set = parse("token: abc\n");
        MAPPER.write(MAPPER.read(set).with("token", null), set);
        assertThat(set.get("token")).isEqualTo("");

        YamlConfiguration absent = parse("name: x\n");
        MAPPER.write(MAPPER.read(absent), absent);
        assertThat(absent.contains("token")).isFalse();
    }

    @Test
    void noValueRemovesTheKeyOfOtherFields() throws Exception {
        ConfigSchema schema = ConfigSchema.of(ConfigField.integer("limit").optional(),
                ConfigField.stringList("tags").optional(), ConfigField.string("motd"));
        SectionMapper mapper = SectionMapper.of(schema);
        YamlConfiguration yaml = parse("limit: 5\ntags: [a]\nmotd: hi\nother: 1\n");

        mapper.write(ConfigValues.empty(), yaml);

        assertThat(yaml.getKeys(false)).containsExactly("other");
    }

    @Test
    void missingRequiredListsReadAsEmptyAndOptionalListsAsNoValue() throws Exception {
        ConfigSchema schema = ConfigSchema.of(
                ConfigField.stringList("required-tags"),
                ConfigField.stringList("optional-tags").optional(),
                ConfigField.objectList("required-items").fields(ConfigField.string("name")),
                ConfigField.objectList("optional-items").optional().fields(ConfigField.string("name")));
        SectionMapper mapper = SectionMapper.of(schema);
        YamlConfiguration yaml = parse("other: 1\n");

        ConfigValues values = mapper.read(yaml);
        assertThat(values.asMap()).containsEntry("required-tags", List.of()).containsEntry("optional-tags", null)
                .containsEntry("required-items", List.of()).containsEntry("optional-items", null);

        mapper.write(values, yaml);
        assertThat(yaml.getKeys(false)).containsExactly("other");

        mapper.write(values.with("optional-tags", List.of()).with("optional-items", List.of()), yaml);
        assertThat(reload(yaml).getValues(false)).containsEntry("optional-tags", List.of())
                .containsEntry("optional-items", List.of());
    }

    @Test
    void readsNumbersAndBooleansInTextFieldsAsText() throws Exception {
        ConfigValues values = MAPPER.read(parse("name: 123\nmotd: 1.5\nmode: yes\nworlds: [1, true, ~, x]\n"));

        assertThat(values.get("name")).isEqualTo("123");
        assertThat(values.get("motd")).isEqualTo("1.5");
        assertThat(values.get("mode")).isEqualTo("true");
        assertThat(values.get("worlds")).isEqualTo(List.of("1", "true", "x"));
    }

    @Test
    void convertsNumbersToTheDeclaredNumberTypeWhereThatIsLossless() throws Exception {
        ConfigValues values = MAPPER.read(parse("max-players: 20.0\nmultiplier: 3\nlimits:\n  per-day: 2.5\n"));

        assertThat(values.get("max-players")).isEqualTo(20L);
        assertThat(values.get("multiplier")).isEqualTo(3.0);
        assertThat(values.get("limits.per-day")).isEqualTo(2.5);
    }

    @Test
    void readsSingleValuesOfAnotherKindUnchangedSoTheEditorReportsThem() throws Exception {
        ConfigValues values = MAPPER.read(parse("enabled: 'true'\nmax-players: many\nmultiplier: false\n"
                + "worlds: world\n"));

        assertThat(values.get("enabled")).isEqualTo("true");
        assertThat(values.get("max-players")).isEqualTo("many");
        assertThat(values.get("multiplier")).isEqualTo(false);
        assertThat(values.get("worlds")).isEqualTo("world");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name:\n  first: a|name is a section, not text",
            "name: [a]|name is a list, not text",
            "enabled: [true]|enabled is a list, not a single value",
            "limits: 5|limits is a single value, not a section",
            "limits: [5]|limits is a list, not a section",
            "worlds:\n  a: b|worlds is a section, not a list",
            "worlds: [[a]]|worlds[0] is a list, not text",
            "rewards: daily|rewards is a single value, not a list",
            "rewards: [daily]|rewards[0] is a single value, not a section",
            "kits: [starter]|kits is a list, not a section of items keyed by id",
            "kits:\n  starter: 5|kits.starter is a single value, not a section",
            "kits:\n  starter:\n    items:\n      sword: {}|kits.starter.items is a section, not a list",
            "name: !!binary G1szM20=|name is stored as binary data (!!binary)",
            "worlds: [!!binary G1szM20=]|worlds[0] is stored as binary data (!!binary)",
            "multiplier: .nan|multiplier is not a finite number",
            "max-players: 99999999999999999999|max-players is a whole number that does not fit in a long",
            "motd: 2024-01-01|motd holds a value of type Date, which cannot be read as text"})
    void refusesValuesItCannotReadWithoutLosingData(String caseText) throws Exception {
        String[] parts = caseText.split("\\|");
        YamlConfiguration yaml = parse(parts[0] + "\n");

        assertThatThrownBy(() -> MAPPER.read(yaml)).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith(parts[1]);
    }

    @Test
    void namesTheKeyedSectionOptionWhenAListIsStoredAsASection() throws Exception {
        YamlConfiguration yaml = parse("rewards:\n  daily:\n    amount: 10\n");

        assertThatThrownBy(() -> MAPPER.read(yaml)).isInstanceOf(IllegalStateException.class)
                .hasMessage("rewards is a section, not a list; to read a section keyed by id, declare it with"
                        + " SectionMapper.Builder.keyedSection(\"rewards\")");
    }

    @Test
    void readsAnEmptyListAsNoItemsOfAKeyedSection() throws Exception {
        assertThat(MAPPER.read(parse("kits: []\n")).getObjectList("kits")).isEmpty();
    }

    @Test
    void namesPathsFromTheRootOfASubsection() throws Exception {
        YamlConfiguration yaml = parse("my-plugin:\n  name: [a]\n");
        ConfigurationSection section = yaml.getConfigurationSection("my-plugin");

        assertThatThrownBy(() -> MAPPER.read(section)).hasMessageStartingWith("my-plugin.name is a list");
    }

    @Test
    void ignoresDefaultsOfTheRootConfiguration() throws Exception {
        YamlConfiguration yaml = parse("name: Survival\n");
        MemoryConfiguration defaults = new MemoryConfiguration();
        defaults.set("motd", "Default motd");
        defaults.set("limits.per-day", 9);
        defaults.set("kits.basic.icon", "STICK");
        yaml.setDefaults(defaults);
        yaml.options().copyDefaults(true);

        ConfigValues values = MAPPER.read(yaml);
        assertThat(values.get("motd")).isNull();
        assertThat(values.get("limits.per-day")).isNull();
        assertThat(values.getObjectList("kits")).isEmpty();

        MAPPER.write(values, yaml);
        assertThat(yaml.get("motd", null)).isNull();
        assertThat(yaml.get("limits", null)).isNull();
    }

    @Test
    void createsAMissingSectionOnlyWhenOneOfItsValuesIsWritten() throws Exception {
        YamlConfiguration yaml = parse("name: x\n");

        MAPPER.write(MAPPER.read(yaml), yaml);
        assertThat(yaml.contains("limits")).isFalse();

        MAPPER.write(MAPPER.read(yaml).with("limits.nested.flag", true), yaml);
        assertThat(yaml.get("limits.nested.flag")).isEqualTo(true);
        assertThat(yaml.getConfigurationSection("limits").getKeys(false)).containsExactly("nested");
    }

    @Test
    void writesSensitiveValuesLikeAnyOtherValue() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        ConfigValues values = MAPPER.read(yaml);

        MAPPER.write(values.with("token", "rotated-secret"), yaml);

        assertThat(yaml.get("token")).isEqualTo("rotated-secret");
    }

    @Test
    void refusesItemKeysThatCannotNameASection() throws Exception {
        YamlConfiguration yaml = parse(CONFIG);
        ConfigValues values = MAPPER.read(yaml);

        assertThatThrownBy(() -> MAPPER.write(values.with("kits[1].id", "starter"), yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kits[1].id repeats the key of an earlier item");
        assertThatThrownBy(() -> MAPPER.write(values.with("kits[0].id", "pro.max"), parse(CONFIG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kits[0].id contains the path separator '.'");
        assertThatThrownBy(() -> MAPPER.write(values.with("kits[0].id", ""), parse(CONFIG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kits[0].id must be a non-empty string, the key of the item's section");
    }

    @Test
    void usesThePathSeparatorOfTheRootConfiguration() throws Exception {
        YamlConfiguration slashes = new YamlConfiguration();
        slashes.options().pathSeparator('/');
        slashes.loadFromString("kits:\n  starter:\n    icon: STICK\n");
        ConfigValues values = MAPPER.read(slashes);

        MAPPER.write(values.with("kits[0].id", "starter.v2"), slashes);

        assertThat(slashes.getConfigurationSection("kits").getKeys(false)).containsExactly("starter.v2");
        assertThat(slashes.get("kits/starter.v2/icon")).isEqualTo("STICK");

        YamlConfiguration dashes = new YamlConfiguration();
        dashes.options().pathSeparator('-');
        assertThatThrownBy(() -> MAPPER.read(dashes)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field key 'max-players' contains the path separator '-' of the configuration");
    }

    @Test
    void reportsTextAndItemKeysThatYamlCannotStoreUnchanged() throws Exception {
        ConfigValues values = MAPPER.read(parse(CONFIG))
                .with("name", "\u001B[33mSurvival")
                .with("motd", "Hello\u0085there")
                .with("worlds", List.of("world", "bell\u0007"))
                .with("token", "del\u007F")
                .with("rewards[1].id", "weekly\u0092s")
                .with("kits[1].id", "pro.max")
                .with("kits[0].items[0].name", "\uFFFE");

        ValidationResult result = MAPPER.validate(values);

        assertThat(result.errors()).extracting(ValidationIssue::path).containsExactly("name", "motd", "worlds[1]",
                "token", "rewards[1].id", "kits[0].items[0].name", "kits[1].id");
        assertThat(result.errors()).extracting(ValidationIssue::message).allSatisfy(message ->
                assertThat(message).doesNotContain("Survival", "bell", "weekly", "pro"));
        assertThat(result.errors().getLast().message())
                .isEqualTo("Cannot contain '.', because it is the key of this item in the configuration file.");
        assertThat(MAPPER.validate(MAPPER.read(parse(CONFIG))).issues()).isEmpty();
    }

    @Test
    void reportsExactlyTheTextThatYamlCannotSaveAsText() throws Exception {
        ConfigSchema schema = ConfigSchema.of(ConfigField.text("value"));
        SectionMapper mapper = SectionMapper.of(schema);
        for (String text : List.of("<gold>[V]</gold> ", "tab\tand\nlines\r\n", "\u00A0nbsp", "\u2028",
                "\uD83C\uDF81", "\uFEFFbom", "")) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("value", text);
            assertThat(mapper.validate(ConfigValues.of(Map.of("value", text))).issues()).as(text).isEmpty();
            assertThat(reload(yaml).get("value")).as(text).isEqualTo(text);
        }
        for (String text : List.of("\u001B[33m", "bell\u0007", "\u000C", "del\u007F", "nel\u0085", "\u0090",
                "smart\u0092s", "\uFFFE", "\uFFFF", "lone\uD800")) {
            assertThat(mapper.validate(ConfigValues.of(Map.of("value", text))).errors())
                    .extracting(ValidationIssue::path, ValidationIssue::code).as(text)
                    .containsExactly(tuple("value", "plugin"));
        }
    }

    @Test
    void acceptsOnlyObjectListsWithAnItemKeyAsKeyedSections() {
        SectionMapper.Builder builder = SectionMapper.builder(SCHEMA);
        assertThatThrownBy(() -> builder.keyedSection("missing")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The schema has no field 'missing'");
        assertThatThrownBy(() -> builder.keyedSection("limits")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field 'limits' must be an object list with an itemKey to be stored as a keyed section");
        assertThatThrownBy(() -> builder.keyedSection("kits.items")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Field 'kits.items' must be an object list with an itemKey");
        assertThatThrownBy(() -> builder.keyedSection("name.first")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The schema has no field 'name.first'");
        assertThat(builder.keyedSection("rewards").build().schema()).isSameAs(SCHEMA);
    }
}
