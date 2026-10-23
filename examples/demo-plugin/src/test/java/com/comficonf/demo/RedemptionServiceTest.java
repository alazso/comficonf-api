package com.comficonf.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.comficonf.demo.config.InvalidConfigException;
import com.comficonf.demo.config.Voucher;
import com.comficonf.demo.config.VoucherConfig;
import com.comficonf.demo.config.VoucherConfigLoader;
import com.comficonf.demo.config.VoucherRuntime;
import com.comficonf.demo.config.VoucherSettings;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RedemptionServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-19T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RedemptionService service = new RedemptionService(clock);

    static VoucherConfig defaults() throws Exception {
        try (InputStream in = RedemptionServiceTest.class.getResourceAsStream("/config.yml")) {
            return VoucherConfigLoader.load(VoucherConfigLoader.parse(in.readAllBytes()));
        }
    }

    @Test
    void loadsTheBundledConfiguration() throws Exception {
        VoucherConfig config = defaults();
        assertThat(config.vouchers()).extracting(Voucher::id)
                .containsExactly("daily", "weekly", "monthly", "starter", "vip", "event");
        assertThat(config.settings().maxRedemptionsPerDay()).isEqualTo(5);
        assertThat(config.settings().bonusMultiplier()).isEqualTo(1.5);
        assertThat(config.settings().webhookConfigured()).isFalse();
        assertThat(config.voucher("event").orElseThrow().enabled()).isFalse();
    }

    @Test
    void rejectsInvalidFilesWithEveryProblem() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> VoucherConfigLoader.load(VoucherConfigLoader.parse(
                ("settings:\n  max-redemptions-per-day: lots\n  storage-mode: mongo\nvouchers:\n  - daily\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("max-redemptions-per-day must be a whole number")
                .hasMessageContaining("storage-mode must be yaml or sqlite")
                .hasMessageContaining("vouchers section is missing");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> VoucherConfigLoader.parse(
                "settings: [unclosed".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void redeemsAndEnforcesTheCooldown() throws Exception {
        VoucherConfig config = defaults();
        RedemptionService.Outcome first = service.redeem(config, "daily", "Steve", null);
        assertThat(first.redeemed()).isTrue();
        assertThat(first.remainingCooldownSeconds()).isEqualTo(86_400);
        assertThat(first.redemptionsToday()).isEqualTo(1);
        assertThat(VoucherLines.redemption("daily", "Steve", first))
                .isEqualTo("redeem daily Steve: ok remaining-cooldown=86400 redemptions-today=1/5 multiplier=1.5");

        clock.advance(400);
        RedemptionService.Outcome again = service.redeem(config, "daily", "steve", null);
        assertThat(again.redeemed()).isFalse();
        assertThat(again.reason()).isEqualTo("cooldown");
        assertThat(again.remainingCooldownSeconds()).isEqualTo(86_000);
        assertThat(service.redeem(config, "daily", "Alex", null).redeemed()).isTrue();
    }

    @Test
    void refusesDisabledAndUnknownVouchersAndDisabledWorlds() throws Exception {
        VoucherConfig config = defaults();
        assertThat(service.redeem(config, "event", "Steve", null).reason()).isEqualTo("disabled");
        assertThat(service.redeem(config, "nope", "Steve", null).reason()).isEqualTo("unknown-voucher");
        assertThat(service.redeem(config, "daily", "Steve", "world_nether").reason()).isEqualTo("disabled-world");
        assertThat(service.redeem(config, "daily", "Steve", "world").redeemed()).isTrue();
    }

    @Test
    void requiresTheVoucherPermissionWhenThePlayerIsOnline() throws Exception {
        VoucherConfig config = defaults();
        RedemptionService.Outcome denied = service.redeem(config, "daily", "Steve", "world",
                permission -> permission.equals("vouchers.weekly"));
        assertThat(denied.redeemed()).isFalse();
        assertThat(denied.reason()).isEqualTo("no-permission");
        assertThat(VoucherLines.redemption("daily", "Steve", denied)).contains("denied reason=no-permission");

        assertThat(service.redeem(config, "daily", "Steve", "world", "vouchers.daily"::equals).redeemed()).isTrue();
        assertThat(service.redeem(config, "weekly", "Alex", null, null).redeemed()).isTrue();
    }

    @Test
    void letsPlayersRedeemForOthersOnlyWithTheAdminPermission() {
        assertThat(VouchersCommand.mayRedeemFor(sender(Player.class, "Steve"), "steve")).isTrue();
        assertThat(VouchersCommand.mayRedeemFor(sender(Player.class, "Steve"), "Alex")).isFalse();
        assertThat(VouchersCommand.mayRedeemFor(sender(Player.class, "Steve", "comficonf.demo.admin"), "Alex"))
                .isTrue();
        assertThat(VouchersCommand.mayRedeemFor(sender(CommandSender.class, "CONSOLE", "comficonf.demo.admin"),
                "Alex")).isTrue();
        assertThat(VouchersCommand.mayRedeemFor(sender(CommandSender.class, "Alex"), "Alex")).isFalse();
    }

    /** A command sender of {@code type} with a name and the given permissions. */
    private static <T extends CommandSender> T sender(Class<T> type, String name, String... permissions) {
        Set<String> granted = Set.of(permissions);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "hasPermission" -> args[0] instanceof String permission && granted.contains(permission);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
    }

    @Test
    void enforcesTheDailyLimitOfTheActiveConfiguration() throws Exception {
        VoucherConfig config = defaults();
        VoucherRuntime runtime = new VoucherRuntime(config);
        VoucherSettings s = config.settings();
        runtime.swap(new VoucherConfig(new VoucherSettings(s.prefix(), s.announceRedemptions(),
                s.announcementChannels(), 2, s.bonusMultiplier(), s.storageMode(), s.disabledWorlds(),
                s.welcomeMessage(), s.webhookUrl()), config.vouchers()));
        assertThat(service.redeem(runtime.config(), "daily", "Steve", null).redeemed()).isTrue();
        assertThat(service.redeem(runtime.config(), "weekly", "Steve", null).redeemed()).isTrue();
        RedemptionService.Outcome third = service.redeem(runtime.config(), "monthly", "Steve", null);
        assertThat(third.reason()).isEqualTo("daily-limit");
        assertThat(third.redemptionsToday()).isEqualTo(2);
        clock.advance(24 * 3600);
        assertThat(service.redeem(runtime.config(), "monthly", "Steve", null).redeemed()).isTrue();
    }

    @Test
    void formatsVoucherAndSettingsLinesExactly() throws Exception {
        VoucherConfig config = defaults();
        assertThat(VoucherLines.voucher(config.voucher("daily").orElseThrow())).isEqualTo(
                "voucher daily: name='Daily Reward' enabled=true material=SUNFLOWER cooldown-seconds=86400"
                        + " permission=vouchers.daily weight=100 rarity=common");
        VoucherRuntime runtime = new VoucherRuntime(config);
        assertThat(VoucherLines.settings(runtime)).isEqualTo("settings: prefix='<gold>[Vouchers]</gold> '"
                + " announce-redemptions=true announcement-channels=chat max-redemptions-per-day=5"
                + " bonus-multiplier=1.5 storage-mode=yaml pending-storage-mode=none webhook-configured=false");
        VoucherSettings s = config.settings();
        runtime.swap(new VoucherConfig(new VoucherSettings(s.prefix(), false, List.of("chat", "title"), 3, 2.0,
                "sqlite", List.of(), "", "https://hooks.example/x"), config.vouchers()));
        assertThat(VoucherLines.settings(runtime)).isEqualTo("settings: prefix='<gold>[Vouchers]</gold> '"
                + " announce-redemptions=false announcement-channels=chat,title max-redemptions-per-day=3"
                + " bonus-multiplier=2.0 storage-mode=yaml pending-storage-mode=sqlite webhook-configured=true");
        assertThat(VoucherLines.settings(runtime)).doesNotContain("hooks.example");
    }
}
