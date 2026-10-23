package com.comficonf.demo;

import com.comficonf.demo.config.Voucher;
import com.comficonf.demo.config.VoucherRuntime;
import com.comficonf.demo.config.VoucherSettings;

/** Single-line, machine-readable command output, so that tests can read results from the server log. */
public final class VoucherLines {

    private VoucherLines() {
    }

    public static String voucher(Voucher voucher) {
        return "voucher " + voucher.id() + ": name='" + voucher.displayName() + "' enabled=" + voucher.enabled()
                + " material=" + voucher.material() + " cooldown-seconds=" + voucher.cooldownSeconds()
                + " permission=" + voucher.permission() + " weight=" + voucher.weight() + " rarity="
                + voucher.rarity();
    }

    public static String settings(VoucherRuntime runtime) {
        VoucherSettings settings = runtime.config().settings();
        String pending = runtime.pendingStorageMode();
        return "settings: prefix='" + settings.prefix() + "' announce-redemptions=" + settings.announceRedemptions()
                + " announcement-channels=" + String.join(",", settings.announcementChannels())
                + " max-redemptions-per-day=" + settings.maxRedemptionsPerDay()
                + " bonus-multiplier=" + settings.bonusMultiplier()
                + " storage-mode=" + runtime.activeStorageMode()
                + " pending-storage-mode=" + (pending == null ? "none" : pending)
                + " webhook-configured=" + settings.webhookConfigured();
    }

    public static String redemption(String voucherId, String playerName, RedemptionService.Outcome outcome) {
        String limit = outcome.maxPerDay() == 0 ? "unlimited" : String.valueOf(outcome.maxPerDay());
        return "redeem " + voucherId + " " + playerName + ": " + (outcome.redeemed() ? "ok" : "denied reason="
                + outcome.reason()) + " remaining-cooldown=" + outcome.remainingCooldownSeconds()
                + " redemptions-today=" + outcome.redemptionsToday() + "/" + limit
                + " multiplier=" + outcome.multiplier();
    }
}
