package com.comficonf.demo.config;

import java.util.List;
import java.util.Objects;

/**
 * The {@code settings} section of the runtime configuration. Immutable.
 *
 * @param prefix MiniMessage text shown before voucher messages
 * @param announceRedemptions whether redemptions are announced
 * @param announcementChannels where announcements appear: {@code chat}, {@code actionbar}, {@code title}
 * @param maxRedemptionsPerDay redemptions per player and day; 0 means unlimited
 * @param bonusMultiplier multiplier applied to rewards
 * @param storageMode storage mode written in the file; see {@link VoucherRuntime#activeStorageMode()}
 * @param disabledWorlds worlds in which vouchers cannot be redeemed
 * @param welcomeMessage MiniMessage text sent to players when they join
 * @param webhookUrl webhook notified on redemptions, empty when none
 */
public record VoucherSettings(String prefix, boolean announceRedemptions, List<String> announcementChannels,
        int maxRedemptionsPerDay, double bonusMultiplier, String storageMode, List<String> disabledWorlds,
        String welcomeMessage, String webhookUrl) {

    public VoucherSettings {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(storageMode, "storageMode");
        Objects.requireNonNull(welcomeMessage, "welcomeMessage");
        Objects.requireNonNull(webhookUrl, "webhookUrl");
        announcementChannels = List.copyOf(announcementChannels);
        disabledWorlds = List.copyOf(disabledWorlds);
    }

    public boolean webhookConfigured() {
        return !webhookUrl.isBlank();
    }

    @Override
    public String toString() {
        return "VoucherSettings[prefix=" + prefix + ", storageMode=" + storageMode + ", webhookConfigured="
                + webhookConfigured() + "]";
    }
}
