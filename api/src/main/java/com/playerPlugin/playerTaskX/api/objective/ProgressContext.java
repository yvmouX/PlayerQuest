package com.playerPlugin.playerTaskX.api.objective;

import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 一次游戏内动作的通用表示——引擎与监听器之间的唯一契约。
 * <p>
 * 监听器只负责把 Bukkit 事件翻译成它，判定逻辑一律写在
 * {@link ObjectiveType} 里，因此引擎不需要认识任何具体事件。
 *
 * <h2>为什么同时带 playerId 与 player</h2>
 * 判定与进度累加只需要 {@code playerId}，因此 {@code player} 允许为 {@code null}——
 * 这样引擎可以完全脱离服务端做单元测试。只有需要访问玩家本体
 * （读背包、给物品等）的目标实现才应使用 {@link #player()}，并自行判空。
 *
 * @param playerId 触发动作的玩家 id
 * @param player   玩家本体，可为 null（仅测试或离屏推进时）
 * @param trigger  动作类型
 * @param target   动作对象：方块材质名 / 实体类型名 / 物品材质名 / 命令名 / 消息内容，
 *                 无需区分时可为 {@code null}
 * @param amount   本次动作数量，通常为 1
 * @param extra    附加信息，供特殊目标使用（如交互的具体动作类型）
 */
public record ProgressContext(UUID playerId, Player player, Trigger trigger,
                              String target, int amount, String extra) {

    public ProgressContext {
        if (amount <= 0) {
            amount = 1;
        }
    }

    /** 从玩家本体构造，playerId 自动取自玩家。 */
    public ProgressContext(Player player, Trigger trigger, String target, int amount, String extra) {
        this(player.getUniqueId(), player, trigger, target, amount, extra);
    }

    public static ProgressContext of(Player player, Trigger trigger, String target) {
        return new ProgressContext(player, trigger, target, 1, null);
    }

    public static ProgressContext of(Player player, Trigger trigger, String target, int amount) {
        return new ProgressContext(player, trigger, target, amount, null);
    }

    /** 无玩家本体的构造，供引擎测试使用。 */
    public static ProgressContext of(UUID playerId, Trigger trigger, String target, int amount) {
        return new ProgressContext(playerId, null, trigger, target, amount, null);
    }

    /** 目标是否命中当前动作上下文。 */
    public boolean matches(QuestObjective objective) {
        return this.target != null && this.target.equalsIgnoreCase(objective.string("target", ""));
    }
}
