package com.comficonf.demo;

import com.comficonf.demo.config.Voucher;
import com.comficonf.demo.config.VoucherConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Simulated redemptions: tracks per-player cooldowns and daily counts in memory and decides each redemption from
 * the configuration that is active at that moment. Thread-safe.
 */
public final class RedemptionService {

    /**
     * Result of a redemption attempt.
     *
     * @param redeemed whether the voucher was redeemed
     * @param reason {@code ok}, {@code unknown-voucher}, {@code disabled}, {@code no-permission},
     *     {@code disabled-world}, {@code cooldown} or {@code daily-limit}
     * @param remainingCooldownSeconds seconds until the player can redeem the voucher again
     * @param redemptionsToday redemptions of the player today, including this one
     * @param maxPerDay daily limit, 0 for unlimited
     * @param multiplier reward multiplier applied
     */
    public record Outcome(boolean redeemed, String reason, long remainingCooldownSeconds, int redemptionsToday,
            int maxPerDay, double multiplier) {
    }

    private record Daily(LocalDate day, int count) {
    }

    private final Clock clock;
    private final Map<String, Map<String, Long>> lastRedeemed = new HashMap<>();
    private final Map<String, Daily> daily = new HashMap<>();

    public RedemptionService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Redeems {@code voucherId} for {@code playerName} without checking the voucher's permission, as for an
     * offline player.
     *
     * @param world world the player is in, or null when the player is offline
     */
    public Outcome redeem(VoucherConfig config, String voucherId, String playerName, @Nullable String world) {
        return redeem(config, voucherId, playerName, world, null);
    }

    /**
     * Redeems {@code voucherId} for {@code playerName}.
     *
     * @param world world the player is in, or null when the player is offline
     * @param permissions whether the player holds a permission, or null when the player is offline and their
     *     permissions are unknown; the voucher's permission is then not checked
     */
    public synchronized Outcome redeem(VoucherConfig config, String voucherId, String playerName,
            @Nullable String world, @Nullable Predicate<String> permissions) {
        String player = playerName.toLowerCase(Locale.ROOT);
        long now = clock.instant().getEpochSecond();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Daily counter = daily.get(player);
        int countToday = counter == null || !counter.day().equals(today) ? 0 : counter.count();
        int max = config.settings().maxRedemptionsPerDay();
        double multiplier = config.settings().bonusMultiplier();
        Voucher voucher = config.voucher(voucherId).orElse(null);
        if (voucher == null) {
            return new Outcome(false, "unknown-voucher", 0, countToday, max, multiplier);
        }
        Long last = lastRedeemed.getOrDefault(player, Map.of()).get(voucher.id());
        long remaining = last == null ? 0 : Math.max(0, last + voucher.cooldownSeconds() - now);
        if (!voucher.enabled()) {
            return new Outcome(false, "disabled", remaining, countToday, max, multiplier);
        }
        if (permissions != null && !permissions.test(voucher.permission())) {
            return new Outcome(false, "no-permission", remaining, countToday, max, multiplier);
        }
        if (world != null && config.settings().disabledWorlds().contains(world)) {
            return new Outcome(false, "disabled-world", remaining, countToday, max, multiplier);
        }
        if (remaining > 0) {
            return new Outcome(false, "cooldown", remaining, countToday, max, multiplier);
        }
        if (max > 0 && countToday >= max) {
            return new Outcome(false, "daily-limit", 0, countToday, max, multiplier);
        }
        lastRedeemed.computeIfAbsent(player, key -> new HashMap<>()).put(voucher.id(), now);
        daily.put(player, new Daily(today, countToday + 1));
        return new Outcome(true, "ok", voucher.cooldownSeconds(), countToday + 1, max, multiplier);
    }
}
