package com.playerPlugin.playerTaskX.PlayerTask.Trigger;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class TaskTriggerSpecial {
    /**
     * 处理杀戮
     *
     * @param player  选手
     * @param message 消息
     */
    protected static void handleKill(Player player, String message) {
        if (!message.isEmpty()) {
            player.sendMessage(message.replace("&", "§"));
        }
        player.setHealth(0);
    }

    /**
     * 手柄药水
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handlePotion(Player player, String args) {
        String[] parts = args.split(" ");
        if (parts.length >= 1) {
            String potionName = parts[0];
            String duration;
            String amplifier;
            boolean particleFlag;
            if (parts.length >= 2) { duration = parts[1]; } else { duration = "30"; }
            if (parts.length >= 3) { amplifier = parts[2]; } else { amplifier = "1"; }
            if (parts.length >= 4) { particleFlag = Boolean.parseBoolean(parts[3]); } else { particleFlag = false; }

            PotionEffectType potion = PotionEffectType.getByName(potionName);
            if (potion == null) {
                log.err("无效的药水类型：" + potionName);
                return;
            }
            boolean isHiddenParticle = particleFlag;
            PotionEffect effect = new PotionEffect(potion,
                    Integer.parseInt(duration),
                    Integer.parseInt(amplifier), false, isHiddenParticle, true);

            player.addPotionEffect(effect);
        }
    }

    /**
     * 处理项目
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handleItem(Player player, String args) {
        // TODO 处理物品
    }
}
