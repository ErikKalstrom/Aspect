package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.gui.MainAspectGUI;
import com.aspects.smp.util.MessageUtil;
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
        return handleOpenMenu(sender);
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
        return true;
    }

    private String[] shift(String[] args) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }
}
