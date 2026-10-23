package com.comficonf.demo;

import com.comficonf.demo.config.Voucher;
import com.comficonf.demo.config.VoucherConfig;
import com.comficonf.demo.config.VoucherSettings;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /vouchers [info <id>|settings|redeem <id> <player>|status|reload]}. Every answer is one line per item,
 * readable on the console and in the server log.
 */
final class VouchersCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("info", "settings", "redeem", "status", "reload");

    private final ComfiConfDemoPlugin plugin;

    VouchersCommand(ComfiConfDemoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> {
                if (args.length != 2) {
                    reply(sender, "usage: /" + label + " info <id>");
                } else {
                    info(sender, args[1]);
                }
            }
            case "settings" -> reply(sender, VoucherLines.settings(plugin.runtime()));
            case "redeem" -> {
                if (args.length != 3) {
                    reply(sender, "usage: /" + label + " redeem <id> <player>");
                } else {
                    redeem(sender, args[1], args[2]);
                }
            }
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            default -> reply(sender, "usage: /" + label + " [info <id>|settings|redeem <id> <player>|status|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(SUBCOMMANDS.stream(), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("info") || sub.equals("redeem"))) {
            return matching(plugin.runtime().config().vouchers().stream().map(Voucher::id), args[1]);
        }
        if (args.length == 3 && sub.equals("redeem")) {
            return matching(plugin.getServer().getOnlinePlayers().stream().map(Player::getName), args[2]);
        }
        return List.of();
    }

    private void list(CommandSender sender) {
        for (Voucher voucher : plugin.runtime().config().vouchers()) {
            reply(sender, VoucherLines.voucher(voucher));
        }
    }

    private void info(CommandSender sender, String id) {
        plugin.runtime().config().voucher(id).ifPresentOrElse(
                voucher -> reply(sender, VoucherLines.voucher(voucher)),
                () -> reply(sender, "voucher " + id + ": not found"));
    }

    /**
     * Whether {@code sender} may redeem a voucher for {@code playerName}: players for themselves, and senders
     * with the admin permission (such as the console) for anyone.
     */
    static boolean mayRedeemFor(CommandSender sender, String playerName) {
        return sender instanceof Player self && self.getName().equalsIgnoreCase(playerName)
                || sender.hasPermission(ComfiConfDemoPlugin.ADMIN_PERMISSION);
    }

    /**
     * Redeems a voucher. An online player must hold the voucher's permission; for an offline player, which only
     * a sender with the admin permission can name, the permission is not checked.
     */
    private void redeem(CommandSender sender, String id, String playerName) {
        if (!mayRedeemFor(sender, playerName)) {
            reply(sender, "redeem " + id + " " + playerName + ": denied, redeeming for another player needs"
                    + " permission " + ComfiConfDemoPlugin.ADMIN_PERMISSION);
            return;
        }
        VoucherConfig config = plugin.runtime().config();
        Player player = plugin.getServer().getPlayerExact(playerName);
        String world = player == null ? null : player.getWorld().getName();
        RedemptionService.Outcome outcome = plugin.redemptions().redeem(config, id, playerName, world,
                player == null ? null : player::hasPermission);
        reply(sender, VoucherLines.redemption(id, playerName, outcome));
        if (outcome.redeemed()) {
            announce(config, config.voucher(id).orElseThrow(), playerName);
        }
    }

    private void announce(VoucherConfig config, Voucher voucher, String playerName) {
        VoucherSettings settings = config.settings();
        if (!settings.announceRedemptions()) {
            return;
        }
        Component prefix = MiniMessage.miniMessage().deserialize(settings.prefix());
        Component text = Component.text(playerName + " redeemed " + voucher.displayName() + "!");
        for (String channel : settings.announcementChannels()) {
            switch (channel) {
                case "chat" -> plugin.getServer().broadcast(prefix.append(text));
                case "actionbar" -> plugin.getServer().getOnlinePlayers()
                        .forEach(online -> online.sendActionBar(text));
                case "title" -> plugin.getServer().getOnlinePlayers()
                        .forEach(online -> online.showTitle(Title.title(prefix, text)));
                default -> {
                }
            }
        }
    }

    private void status(CommandSender sender) {
        DemoIntegration integration = plugin.integration();
        String pending = plugin.runtime().pendingStorageMode();
        String comficonf;
        if (integration != null && integration.isRegistered()) {
            comficonf = "registered resource=" + integration.resourceId();
        } else if (plugin.bridgeInstalled()) {
            comficonf = "installed-not-registered";
        } else {
            comficonf = "not-installed";
        }
        reply(sender, "status: storage-mode=" + plugin.runtime().activeStorageMode() + " pending-storage-mode="
                + (pending == null ? "none" : pending) + " vouchers=" + plugin.runtime().config().vouchers().size()
                + " comficonf=" + comficonf);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ComfiConfDemoPlugin.ADMIN_PERMISSION)) {
            reply(sender, "reload: denied, missing permission " + ComfiConfDemoPlugin.ADMIN_PERMISSION);
            return;
        }
        String problem = plugin.reload();
        if (problem == null) {
            reply(sender, "reload: ok vouchers=" + plugin.runtime().config().vouchers().size());
        } else {
            reply(sender, "reload: failed error='" + problem + "', keeping the previous configuration");
        }
    }

    private static void reply(CommandSender sender, String line) {
        sender.sendMessage(Component.text(line));
    }

    private static List<String> matching(Stream<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
