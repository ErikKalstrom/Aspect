package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.gui.AdminGUI;
import com.aspects.smp.item.CustomItemManager;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminCommand {
    private final AspectsPlugin plugin;

    public AdminCommand(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|give|give-selection|clear-cooldown|reload|list|debug> ...</red>"));
                return true;
            }
            if (!player.hasPermission("aspects.admin")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
                return true;
            }
            new AdminGUI(plugin, player).open();
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "reroll" -> handleReroll(sender, args);
            case "reset" -> handleReset(sender, args);
            case "give-reroller" -> handleGiveReroller(sender, args);
            case "give" -> handleGive(sender, args);
            case "give-selection" -> handleGiveSelection(sender, args);
            case "clear-cooldown" -> handleClearCooldown(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "debug" -> handleDebug(sender, args);
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

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.reset")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin reset <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Optional<Aspect> result = plugin.getAspectManager().resetAspect(target);
        if (result.isPresent()) {
            Aspect aspect = result.get();
            sender.sendMessage(MessageUtil.parse(
                    "<green>Reset " + target.getName() + "'s Aspect — reassigned " + aspect.id() + ".</green>"));
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.changed"));
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                    Map.of("new", aspect.definition().displayName()));
        } else {
            sender.sendMessage(MessageUtil.parse("<green>Reset " + target.getName() +
                    "'s Aspect — they will receive a new one on their next join.</green>"));
        }
        return true;
    }

    private boolean handleGiveReroller(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.give")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give-reroller <player> [amount]</red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        int requested = 1;
        if (args.length >= 3) {
            try {
                requested = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>'" + args[2] + "' is not a valid amount.</red>"));
                return true;
            }
        }
        if (requested < 1) {
            sender.sendMessage(MessageUtil.parse("<red>Amount must be at least 1.</red>"));
            return true;
        }
        int amount = clampGiveAmount(requested);
        if (amount != requested) {
            sender.sendMessage(MessageUtil.parse("<yellow>Amount capped at " + amount + ".</yellow>"));
        }
        CustomItemManager itemManager = plugin.getCustomItemManager();
        ItemStack stack = itemManager.createReroller();
        stack.setAmount(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            Location dropLocation = target.getLocation();
            World world = target.getWorld();
            for (ItemStack remaining : leftover.values()) {
                world.dropItemNaturally(dropLocation, remaining);
            }
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the remainder was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + amount + " Aspect Reroller(s) to " + target.getName());
        sender.sendMessage(MessageUtil.parse("<green>Gave " + amount + " Aspect Reroller(s) to " + target.getName() + ".</green>"));
        return true;
    }

    static int clampGiveAmount(int requested) {
        return Math.min(requested, 64);
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.give")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give <player> <aspect></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        String aspectId = args[2].toLowerCase();
        if (plugin.getAspectRegistry().get(aspectId).isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>Unknown Aspect id: " + aspectId + "</red>"));
            return true;
        }
        ItemStack token = plugin.getCustomItemManager().createSelectorToken(aspectId);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(token);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover.values().iterator().next());
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the token was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + target.getName() + " an Aspect Selection Token for " + aspectId);
        sender.sendMessage(MessageUtil.parse("<green>Gave " + target.getName() + " a Selection Token for " + aspectId + ".</green>"));
        return true;
    }

    private boolean handleGiveSelection(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.selector")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give-selection <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        ItemStack selector = plugin.getCustomItemManager().createGenericSelector();
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(selector);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover.values().iterator().next());
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the Selector was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + target.getName() + " an Aspect Selector");
        sender.sendMessage(MessageUtil.parse("<green>Gave " + target.getName() + " an Aspect Selector.</green>"));
        return true;
    }

    private boolean handleClearCooldown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.cooldown")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin clear-cooldown <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        plugin.getCooldownManager().resetCooldowns(target.getUniqueId());
        sender.sendMessage(MessageUtil.parse("<green>Cleared all ability cooldowns for " + target.getName() + ".</green>"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("aspects.admin.reload")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        List<String> errors = plugin.getConfigManager().tryReload();
        if (!errors.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>Reload failed — configuration errors:</red>"));
            for (String error : errors) {
                sender.sendMessage(MessageUtil.parse("<red> - " + error + "</red>"));
            }
            sender.sendMessage(MessageUtil.parse("<yellow>The previous configuration is still active. " +
                    "Note that a value like this may have been tolerated with a safe default at server " +
                    "startup, but a reload never applies a config with any unresolved error — fix the " +
                    "issue(s) above and reload again.</yellow>"));
            return true;
        }
        plugin.getRerollerRecipeManager().register(plugin.getConfigManager().recipes());
        sender.sendMessage(MessageUtil.parse("<green>Aspects configuration reloaded.</green>"));
        sender.sendMessage(MessageUtil.parse("<yellow>Note: messages.yml, recipes.yml, and the reveal-animation " +
                "setting are applied live. Changes to aspects.yml and to the reroll/reset behavior flags, " +
                "blocked-worlds, or respect-worldguard in config.yml require a server restart to take " +
                "effect.</yellow>"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("aspects.admin")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        sender.sendMessage(MessageUtil.parse("<dark_purple>All Aspects:</dark_purple>"));
        for (Aspect aspect : plugin.getAspectRegistry().all()) {
            String status = aspect.definition().enabled() ? "<green>enabled</green>" : "<gray>disabled</gray>";
            sender.sendMessage(MessageUtil.parse("<white> - " + aspect.id() + "</white> (" + status + ")"));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.debug")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin debug <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        sender.sendMessage(MessageUtil.parse("<dark_purple>Debug info for " + target.getName() + ":</dark_purple>"));
        sender.sendMessage(MessageUtil.parse("<white>Aspect: " + (aspect != null ? aspect.id() : "none") + "</white>"));
        if (aspect != null) {
            long remaining = plugin.getCooldownManager().remainingMillis(target.getUniqueId(), aspect.id());
            String cooldownText = remaining > 0 ? ((remaining + 999) / 1000) + "s remaining" : "ready";
            sender.sendMessage(MessageUtil.parse("<white>Cooldown: " + cooldownText + "</white>"));
        }
        sender.sendMessage(MessageUtil.parse("<white>exclude-current-aspect: " +
                plugin.getConfigManager().general().excludeCurrentOnReroll() + "</white>"));
        sender.sendMessage(MessageUtil.parse("<white>reset.immediate-reassign: " +
                plugin.getConfigManager().general().resetImmediateReassign() + "</white>"));
        return true;
    }
}
