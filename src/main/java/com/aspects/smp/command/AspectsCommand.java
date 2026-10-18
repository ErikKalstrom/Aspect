package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.gui.MainAspectGUI;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AspectsCommand implements CommandExecutor {
    private final AspectsPlugin plugin;
    private final AdminCommand adminCommand;

    public AspectsCommand(AspectsPlugin plugin, AdminCommand adminCommand) {
        this.plugin = plugin;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, shift(args));
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            return handleList(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("inspect")) {
            return handleInspect(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            return handleHelp(sender);
        }
        return handleOpenMenu(sender);
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<dark_purple>Enabled Aspects:</dark_purple>"));
        for (Aspect aspect : plugin.getAspectRegistry().enabled()) {
            sender.sendMessage(MessageUtil.parse(
                    "<" + aspect.definition().rarity().color() + ">" + aspect.definition().displayName() +
                            "</" + aspect.definition().rarity().color() + "> <gray>-</gray> " + aspect.definition().description()));
        }
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.inspect")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects inspect <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        if (aspect == null) {
            sender.sendMessage(MessageUtil.parse("<red>" + target.getName() + " does not have an Aspect assigned yet.</red>"));
            return true;
        }
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>" + target.getName() + "'s Aspect:</dark_purple> " + aspect.definition().displayName()));
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <" + aspect.definition().rarity().color() + ">" +
                        aspect.definition().rarity().name() + "</" + aspect.definition().rarity().color() + ">"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>Description:</dark_purple> <white>" + aspect.definition().description() + "</white>"));
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<dark_purple>Aspects commands:</dark_purple>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects</white> <gray>- Open the Aspects menu.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects info</white> <gray>- Show your current Aspect.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects list</white> <gray>- List all enabled Aspects.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects inspect <player></white> <gray>- View another player's Aspect.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects help</white> <gray>- Show this message.</gray>"));
        return true;
    }

    private boolean handleOpenMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Only players can open the Aspects menu. Use /aspects admin debug <player> from console.</red>"));
            return true;
        }
        if (!player.hasPermission("aspects.use")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        if (!player.hasPermission("aspects.gui")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to open the Aspects menu.</red>"));
            return true;
        }
        new MainAspectGUI(plugin, player).open();
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Only players have an Aspect. Use /aspects admin debug <player> from console.</red>"));
            return true;
        }
        if (!player.hasPermission("aspects.use")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().message("ability.no-aspect"));
            return true;
        }
        player.sendMessage(MessageUtil.parse("<dark_purple>Aspect:</dark_purple> " + aspect.definition().displayName()));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <" + aspect.definition().rarity().color() + ">" +
                        aspect.definition().rarity().name() + "</" + aspect.definition().rarity().color() + ">"));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Description:</dark_purple> <white>" + aspect.definition().description() + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Passive:</dark_purple> <white>" + aspect.definition().passiveName() + "</white> - " +
                        aspect.definition().passiveDescription()));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Active:</dark_purple> <white>" + aspect.definition().activeName() + "</white> - " +
                        aspect.definition().activeDescription()));
        long remainingMillis = plugin.getCooldownManager().remainingMillis(player.getUniqueId(), aspect.id());
        String cooldownText = remainingMillis > 0
                ? "<red>" + ((remainingMillis + 999) / 1000) + "s remaining</red>"
                : "<green>Ready</green>";
        player.sendMessage(MessageUtil.parse("<dark_purple>Cooldown:</dark_purple> " + cooldownText));
        return true;
    }

    private String[] shift(String[] args) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }
}
