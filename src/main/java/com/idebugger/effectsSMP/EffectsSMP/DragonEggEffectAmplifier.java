package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DragonEggEffectAmplifier implements Listener {

    private final EffectsSMP plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long MOVE_COOLDOWN_MS = 60_000;

    public DragonEggEffectAmplifier(EffectsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDragonEggAdvancement(PlayerAdvancementDoneEvent event) {
        if (!event.getAdvancement().getKey().toString().equals("minecraft:end/dragon_egg")) return;

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement ps = plugin.getConnection().prepareStatement(
                        "SELECT effect, amplifier FROM permanent_effects WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String effectName = rs.getString("effect");
                    int amp = rs.getInt("amplifier");
                    int newAmp = amp + 2;

                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type == null) return;

                    PreparedStatement update = plugin.getConnection().prepareStatement(
                            "UPDATE permanent_effects SET amplifier = ?, egg_owner = ? WHERE uuid = ?");
                    update.setInt(1, newAmp);
                    update.setBoolean(2, true);
                    update.setString(3, player.getUniqueId().toString());
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

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && now - cooldowns.get(uuid) < MOVE_COOLDOWN_MS) return;
        cooldowns.put(uuid, now);

        if (player.getInventory().containsAtLeast(new ItemStack(Material.DRAGON_EGG), 1)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement ps = plugin.getConnection().prepareStatement(
                        "SELECT effect, amplifier, egg_owner FROM permanent_effects WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getBoolean("egg_owner")) {
                    String effectName = rs.getString("effect");
                    int amp = rs.getInt("amplifier");

                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type == null) return;

                    int newAmp = Math.max(0, amp - 2);

                    PreparedStatement update = plugin.getConnection().prepareStatement(
                            "UPDATE permanent_effects SET amplifier = ?, egg_owner = ? WHERE uuid = ?");
                    update.setInt(1, newAmp);
                    update.setBoolean(2, false);
                    update.setString(3, uuid.toString());
                    update.executeUpdate();
                    update.close();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.removePotionEffect(type);
                        if (newAmp > 0) {
                            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, newAmp, true, false, false));
                        }
                        player.sendMessage("§5Your " + type.getName() + " effect was reduced due to losing the Dragon Egg! (-2)");
                        AdvancementUtils.revokeAdvancement(player, "minecraft:end/dragon_egg");
                    });
                }

                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}