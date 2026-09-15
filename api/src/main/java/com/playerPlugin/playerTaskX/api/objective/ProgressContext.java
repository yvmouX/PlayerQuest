package com.playerPlugin.playerTaskX.api.objective;

import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 一次游戏内动作：引擎与监听器之间唯一的契约，监听器只把事件翻译成它。
 * {@code player} 可为 null（判定只需 {@code playerId}，便于脱机单测）；{@code aliases} 是同一对象的等价名字（原版类型名 + mythic id）。
 */
public record ProgressContext(UUID playerId, Player player, Trigger trigger,
                              String target, int amount, String extra, List<String> aliases) {

    public ProgressContext {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        if (amount <= 0) {
            amount = 1;
        }
    }

    /** 从玩家本体构造，playerId 自动取自玩家。 */
    public ProgressContext(Player player, Trigger trigger, String target, int amount, String extra) {
        this(player.getUniqueId(), player, trigger, target, amount, extra, List.of());
    }

    /** 从玩家本体构造，并带上等价标识（如原版实体类型 + MythicMobs 怪物 id）。 */
    public ProgressContext(Player player, Trigger trigger, String target, int amount, String extra,
                           List<String> aliases) {
        this(player.getUniqueId(), player, trigger, target, amount, extra, aliases);
    }

    public static ProgressContext of(Player player, Trigger trigger, String target) {
        return new ProgressContext(player, trigger, target, 1, null);
    }

    public static ProgressContext of(Player player, Trigger trigger, String target, int amount) {
        return new ProgressContext(player, trigger, target, amount, null);
    }

    /** 无玩家本体的构造，供引擎测试使用。 */
    public static ProgressContext of(UUID playerId, Trigger trigger, String target, int amount) {
        return new ProgressContext(playerId, null, trigger, target, amount, null, List.of());
    }

    /** 目标是否命中当前动作上下文。 */
    public boolean matches(QuestObjective objective) {
        return this.target != null && this.target.equalsIgnoreCase(objective.string("target", ""));
    }
}
