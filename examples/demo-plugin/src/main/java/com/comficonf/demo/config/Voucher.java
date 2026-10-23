package com.comficonf.demo.config;

import java.util.List;
import java.util.Objects;

/**
 * One voucher of the runtime configuration. Immutable.
 *
 * @param id key of the voucher in the {@code vouchers} section
 * @param displayName name shown to players
 * @param enabled whether the voucher can be redeemed
 * @param material item shown for the voucher, a {@code Material} name
 * @param cooldownSeconds time before the same player can redeem it again
 * @param permission permission needed to redeem it
 * @param weight relative chance in random draws
 * @param rarity {@code common}, {@code rare}, {@code epic} or {@code legendary}
 * @param lore tooltip lines; not editable from ComfiConf
 */
public record Voucher(String id, String displayName, boolean enabled, String material, long cooldownSeconds,
        String permission, int weight, String rarity, List<String> lore) {

    public Voucher {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(rarity, "rarity");
        lore = List.copyOf(lore);
    }
}
