package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.core.objective.BreedObjective;
import com.playerPlugin.playerTaskX.core.objective.BreakBlockObjective;
import com.playerPlugin.playerTaskX.core.objective.ChatObjective;
import com.playerPlugin.playerTaskX.core.objective.CommandObjective;
import com.playerPlugin.playerTaskX.core.objective.ConsumeObjective;
import com.playerPlugin.playerTaskX.core.objective.CraftObjective;
import com.playerPlugin.playerTaskX.core.objective.EnchantObjective;
import com.playerPlugin.playerTaskX.core.objective.FishObjective;
import com.playerPlugin.playerTaskX.core.objective.InteractObjective;
import com.playerPlugin.playerTaskX.core.objective.KillObjective;
import com.playerPlugin.playerTaskX.core.objective.PlaceBlockObjective;
import com.playerPlugin.playerTaskX.core.objective.ShearObjective;
import com.playerPlugin.playerTaskX.core.objective.SubmitObjective;
import com.playerPlugin.playerTaskX.core.objective.TameObjective;
import com.playerPlugin.playerTaskX.core.reward.CommandReward;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;

import java.util.List;

/**
 * 内置目标与奖励类型的清单——「插件自带哪些类型」唯一的事实来源。
 *
 * <p>这里显式列出而不是扫描包：多几行代码，换来「新增类型必须显式登记」的确定性，
 * 也避免反射扫描在插件类加载器下的兼容问题。</p>
 *
 * <p>测试同样从这里取清单（如 ObjectiveFieldTypeConsistencyTest），
 * 因此「清单更新了但测试没跟上」的不一致不可能发生。</p>
 */
public final class BuiltIns {

    private BuiltIns() {
    }

    /** 全部内置目标类型，按登记顺序排列（注册表按序展示）。 */
    public static List<ObjectiveType> objectives() {
        return List.of(
                new BreakBlockObjective(),
                new PlaceBlockObjective(),
                new CraftObjective(),
                new FishObjective(),
                new KillObjective(),
                new ConsumeObjective(),
                new EnchantObjective(),
                new ShearObjective(),
                new BreedObjective(),
                new TameObjective(),
                new InteractObjective(),
                new ChatObjective(),
                new SubmitObjective(),
                new CommandObjective()
        );
    }

    /** 全部内置奖励类型。money/points 依赖软依赖，注册后需逐个检查可用性并告知管理员。 */
    public static List<RewardType> rewards() {
        return List.of(
                new MoneyReward(),
                new PointsReward(),
                new ExpReward(),
                new ItemReward(),
                new CommandReward()
        );
    }
}
