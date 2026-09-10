package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 奖励：执行自定义命令。
 * <p>
 * 支持 {@code %player%} 占位符，命令由控制台执行，
 * 因此不需要给玩家任何额外权限。
 */
public final class CommandReward implements RewardType {

    public static final String ID = "command";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "自定义命令";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.text("command", "命令", "give %player% diamond 1",
                        "不含前导 / 的命令；%player% 会替换为玩家名，由控制台执行"),
                ConfigField.bool("as-player", "以玩家身份执行", false,
                        "开启后以玩家自己执行（受其权限限制），默认由控制台执行")
        );
    }

    @Override
    public void grant(Player player, QuestReward reward) {
        String command = reward.string("command", "").trim();
        if (command.isEmpty()) {
            return;
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        command = command.replace("%player%", player.getName())
                .replace("{player}", player.getName());
        boolean asPlayer = reward.properties().get("as-player") instanceof Boolean bool && bool;
        if (asPlayer) {
            player.performCommand(command);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
