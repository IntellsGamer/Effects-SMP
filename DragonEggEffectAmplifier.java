package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DragonEggEffectAmplifier implements Listener {

    private final EffectsSMP plugin;

    public DragonEggEffectAmplifier(EffectsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDragonEggAdvancement(PlayerAdvancementDoneEvent event) {
        if (!event.getAdvancement().getKey().toString().equals("minecraft:end/dragon_egg")) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement ps = plugin.getConnection().prepareStatement("SELECT effect, amplifier FROM permanent_effects WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String effectName = rs.getString("effect");
                    int amp = rs.getInt("amplifier");

                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type == null) return;

                    int newAmp = amp + 2;

                    PreparedStatement update = plugin.getConnection().prepareStatement("UPDATE permanent_effects SET amplifier = ? WHERE uuid = ?");
                    update.setInt(1, newAmp);
                    update.setString(2, player.getUniqueId().toString());
                    update.executeUpdate();
                    update.close();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.removePotionEffect(type);
                        player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, newAmp, true, false, false));
                        player.sendMessage("§5Your " + type.getName() + " effect was enhanced by the Dragon Egg! (+2)");
                    });
                }

                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
