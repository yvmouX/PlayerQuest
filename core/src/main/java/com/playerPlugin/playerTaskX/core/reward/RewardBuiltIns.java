package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.reward.RewardType;

import java.util.List;

/**
 * 内置奖励类型清单（「插件自带哪些奖励」的唯一事实来源）：显式登记而不扫描包，新增类型必须改这里。
 * money / points 依赖软依赖，注册后要逐个检查可用性并告知管理员。
 */
public final class RewardBuiltIns {

    private RewardBuiltIns() {
    }

    /** 全部内置奖励类型。 */
    public static List<RewardType> all() {
        return List.of(
                new MoneyReward(),
                new PointsReward(),
                new CommandReward()
        );
    }
}
