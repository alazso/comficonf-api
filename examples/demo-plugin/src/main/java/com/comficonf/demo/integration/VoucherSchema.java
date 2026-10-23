package com.comficonf.demo.integration;

import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;

/** The editable fields of {@code config.yml}. Keys not declared here are never sent to ComfiConf. */
public final class VoucherSchema {

    private VoucherSchema() {
    }

    /**
     * Maps {@code config.yml} to {@link #create()}. The {@code vouchers} section maps each voucher id to the
     * voucher's settings; ComfiConf sees it as a list in file order with the id as the {@code id} field.
     */
    public static SectionMapper mapper() {
        return SectionMapper.builder(create()).keyedSection("vouchers").build();
    }

    public static ConfigSchema create() {
        return ConfigSchema.of(
                ConfigField.object("settings").label("General").fields(
                        ConfigField.string("prefix").label("Message prefix")
                                .description("MiniMessage text shown before every voucher message.")
                                .maxLength(64),
                        ConfigField.bool("announce-redemptions")
                                .description("Tell other players when someone redeems a voucher."),
                        ConfigField.stringList("announcement-channels")
                                .description("Where announcements appear.")
                                .choice("chat", "Chat")
                                .choice("actionbar", "Action bar")
                                .choice("title", "Title")
                                .uniqueItems()
                                .minItems(1)
                                .ordered(false)
                                .visibleWhen("announce-redemptions", true),
                        ConfigField.integer("max-redemptions-per-day")
                                .description("Vouchers one player can redeem per day. 0 means no limit.")
                                .range(0, 100)
                                .slider(),
                        ConfigField.decimal("bonus-multiplier")
                                .description("Multiplier applied to every reward.")
                                .range(0.0, 10.0)
                                .step(0.1)
                                .unit("x"),
                        ConfigField.enumeration("storage-mode")
                                .description("Where redemption history is stored.")
                                .choice("yaml", "YAML file")
                                .choice("sqlite", "SQLite database")
                                .restartRequired(),
                        ConfigField.stringList("disabled-worlds")
                                .description("Worlds in which vouchers cannot be redeemed.")
                                .itemPattern("[A-Za-z0-9_.-]{1,64}",
                                        "World names use letters, digits, '_', '-' and '.'.")
                                .uniqueItems()
                                .placeholder("world_nether"),
                        ConfigField.text("welcome-message")
                                .description("MiniMessage text sent to players when they join.")
                                .maxLength(500),
                        ConfigField.string("webhook-url").label("Webhook URL")
                                .description("Optional https:// webhook notified on every redemption.")
                                .optional()
                                .writeOnly()
                                .pattern("https://.+", "Use an https:// URL.")),
                ConfigField.objectList("vouchers")
                        .itemKey("id")
                        .itemTitle("display-name")
                        .itemLabel("voucher")
                        .minItems(1)
                        .fields(
                                ConfigField.string("id")
                                        .description("Key of the voucher in config.yml. Lowercase letters, digits,"
                                                + " '_' and '-'.")
                                        .pattern("[a-z0-9_-]{1,32}", "Use 1-32 lowercase letters, digits, '_' or '-'."),
                                ConfigField.string("display-name").length(1, 48),
                                ConfigField.bool("enabled").defaultValue(true),
                                ConfigField.material("material").itemsOnly().defaultValue("PAPER"),
                                ConfigField.integer("cooldown-seconds").label("Cooldown")
                                        .description("Time before the same player can redeem this voucher again.")
                                        .range(0, 604_800)
                                        .unit("seconds")
                                        .defaultValue(86_400),
                                ConfigField.string("permission")
                                        .description("Permission needed to redeem the voucher.")
                                        .pattern("[a-z0-9_.-]+", "Use lowercase letters, digits, '_', '.' or '-'.")
                                        .maxLength(64),
                                ConfigField.integer("weight")
                                        .description("Relative chance in random draws.")
                                        .range(1, 1000)
                                        .defaultValue(100),
                                ConfigField.enumeration("rarity")
                                        .choice("common", "Common")
                                        .choice("rare", "Rare")
                                        .choice("epic", "Epic")
                                        .choice("legendary", "Legendary")
                                        .defaultValue("common")));
    }
}
