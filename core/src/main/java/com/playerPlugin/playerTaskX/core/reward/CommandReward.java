package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** 奖励：执行自定义命令（支持 {@code %player%}），默认由控制台执行，{@code as-player} 为 true 时改用玩家本人。 */
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
                ConfigField.text("command", "命令",
                        "不含前导 / 的命令；%player% 会替换为玩家名，由控制台执行"),
                ConfigField.bool("as-player", "以玩家身份执行",
                        "填 true 则改用玩家自己执行（受其权限限制），缺省由控制台执行")
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
        // 只认 Boolean 是不够的：手改的库内容与 YAML 定义很容易把布尔写成字符串 "true"，
        // 那样会静默退回控制台执行（玩家侧看不出差别，属于「不报错的错误」）。
        // 全项目只有这一处读布尔型奖励配置，因此不做成 QuestReward.bool 那种通用访问器。
        boolean asPlayer = Boolean.parseBoolean(reward.string("as-player", "false").trim());
        if (asPlayer) {
            player.performCommand(command);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
