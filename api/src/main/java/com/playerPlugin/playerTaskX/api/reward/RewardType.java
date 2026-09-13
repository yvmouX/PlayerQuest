package com.playerPlugin.playerTaskX.api.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import org.bukkit.entity.Player;

/**
 * 任务奖励类型——扩展点之二。
 * <p>
 * 新增一种奖励同样只需实现本接口：表单由 {@link #schema()} 生成，
 * 发放由 {@link #grant} 完成，界面与引擎无需改动。
 */
public interface RewardType extends ConfigurableType {

    /**
     * 发放奖励。
     * <p>
     * 由引擎在主线程调用，实现里可以安全访问 Bukkit API。
     * 无法发放时（如 Vault 未安装）应记录日志而不是抛异常，
     * 避免一个奖励失败导致整条发放流程中断。
     */
    void grant(Player player, QuestReward reward);

    /**
     * 是否可用——依赖的软依赖插件是否就绪。
     * 不可用的类型在编辑器与 GUI 中会被标记，避免配置了却静默不生效。
     */
    default boolean available() {
        return true;
    }

    /** 不可用的原因，用于提示管理员。 */
    default String unavailableReason() {
        return "";
    }
}
