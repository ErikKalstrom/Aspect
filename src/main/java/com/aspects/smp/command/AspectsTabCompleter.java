package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AspectsTabCompleter implements TabCompleter {
    private final AspectsPlugin plugin;

    public AspectsTabCompleter(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("info");
            options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.add("set");
            options.add("reroll");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reroll"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
            options.addAll(plugin.getConfigManager().aspectDefinitions().keySet());
        }

        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        options.removeIf(opt -> !opt.toLowerCase().startsWith(current));
        return options;
    }
}
