package com.playerPlugin.playerTaskX.api.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import org.bukkit.entity.Player;

/** 任务奖励类型——扩展点之二：表单由 {@link #schema()} 声明，发放由 {@link #grant} 完成。 */
public interface RewardType extends ConfigurableType {

    /** 发放奖励（引擎在主线程调用）；发不出去时只记日志、不要抛异常，免得一个奖励拖垮整条发放流程。 */
    void grant(Player player, QuestReward reward);

    /** 依赖的软依赖是否就绪；不可用的类型会被校验报出原因，避免配了却静默不生效。 */
    default boolean available() {
        return true;
    }

    /** 不可用的原因，用于提示管理员。 */
    default String unavailableReason() {
        return "";
    }
}
