package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public final class AdminCommand {
    private final AspectsPlugin plugin;

    public AdminCommand(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin <set|reroll> <player> [aspect]</red>"));
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "reroll" -> handleReroll(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>Unknown admin subcommand '" + sub + "'.</red>"));
                yield true;
            }
        };
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.set")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin set <player> <aspect></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        String aspectId = args[2].toLowerCase();
        Optional<Aspect> result = plugin.getAspectManager().adminSet(target, aspectId);
        if (result.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>Unknown or disabled Aspect id: " + aspectId + "</red>"));
            return true;
        }
        Aspect aspect = result.get();
        sender.sendMessage(MessageUtil.parse("<green>Set " + target.getName() + "'s Aspect to " + aspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", aspect.definition().displayName()));
        return true;
    }

    private boolean handleReroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.reroll")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin reroll <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect previous = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        Optional<Aspect> result = plugin.getAspectManager().adminReroll(target);
        if (result.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>No eligible Aspect was available to reroll into.</red>"));
            return true;
        }
        Aspect newAspect = result.get();
        sender.sendMessage(MessageUtil.parse("<green>Rerolled " + target.getName() + "'s Aspect to " + newAspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.changed"));
        if (previous != null) {
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.previous"),
                    Map.of("old", previous.definition().displayName()));
        }
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", newAspect.definition().displayName()));
        return true;
    }
}
