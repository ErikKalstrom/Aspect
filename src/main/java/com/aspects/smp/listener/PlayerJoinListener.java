package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.config.GeneralConfig;
import com.aspects.smp.util.MessageUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public final class PlayerJoinListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerJoinListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getAspectManager().loadOrAssign(player, () -> announceFirstJoin(player));
        if (plugin.getConfigManager().mastery().enabled()) {
            plugin.getMasteryManager().loadForPlayer(player.getUniqueId());
        }
    }

    private void announceFirstJoin(Player player) {
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        GeneralConfig general = plugin.getConfigManager().general();
        if (!general.firstJoinEnabled()) {
            return;
        }

        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line1"));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line2"),
                Map.of("aspect", aspect.definition().displayName()));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line3"),
                Map.of("rarity", aspect.definition().rarity().name()));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line4"),
                Map.of("passive", aspect.definition().passiveName()));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line5"),
                Map.of("active", aspect.definition().activeName()));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.hint"));
        SoundUtil.play(player, general.firstJoinSound(), 1.0f, 1.0f, plugin.getLogger());
        ParticleUtil.spawn(player.getLocation(), general.firstJoinParticle(),
                general.firstJoinParticleCount(), plugin.getLogger());
    }
}
