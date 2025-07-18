package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class AdvancementUtils {

    public static void revokeAdvancement(Player player, String advancementKey) {
        NamespacedKey key = NamespacedKey.minecraft(advancementKey);
        Advancement advancement = Bukkit.getAdvancement(key);

        if (advancement == null) {
            player.sendMessage("§cAdvancement " + advancementKey + " not found.");
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String criterion : progress.getAwardedCriteria()) {
            progress.revokeCriteria(criterion);
        }

        player.sendMessage("§aRevoked advancement: " + advancementKey);
    }
}
