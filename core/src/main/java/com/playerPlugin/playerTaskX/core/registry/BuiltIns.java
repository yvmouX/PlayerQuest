package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.core.objective.ChatObjective;
import com.playerPlugin.playerTaskX.core.objective.InteractObjective;
import com.playerPlugin.playerTaskX.core.objective.TargetObjective;
import com.playerPlugin.playerTaskX.core.reward.CommandReward;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;

import java.util.List;

/**
 * 内置目标与奖励类型的清单——「插件自带哪些类型」唯一的事实来源。
 *
 * <h2>为什么全部列在这里而不是扫描包</h2>
 * 多几行代码，换来「新增类型必须显式登记」的确定性，也避免反射扫描在插件类加载器下的兼容问题。
 * 测试同样从这里取清单（如 {@code ObjectiveFieldTypeConsistencyTest}、{@code ExampleQuestsTest}），
 * 因此「清单更新了但测试没跟上」的不一致不可能发生。
 *
 * <h2>为什么目标类型大多是数据行</h2>
 * 14 种目标里有 12 种的行为完全一致（{@code target} 命中就加本次数量），
 * 只差 id、动作与 {@code target} 字段的语义类型，因此用 {@link TargetObjective} 一行一个；
 * 只有自带判定逻辑的 {@link InteractObjective}（{@code mode} 匹配）与
 * {@link ChatObjective}（关键词包含匹配）是独立类。
 */
public final class BuiltIns {

    private BuiltIns() {
    }

    /** 全部内置目标类型，按登记顺序排列（注册表按序展示）。 */
    public static List<ObjectiveType> objectives() {
        return List.of(
                new TargetObjective("break_block", "挖掘方块", Trigger.BREAK_BLOCK,
                        ConfigField.material("target", "目标方块", "DIAMOND_ORE"), 64),
                new TargetObjective("place_block", "放置方块", Trigger.PLACE_BLOCK,
                        ConfigField.material("target", "目标方块", "STONE"), 64),
                new TargetObjective("craft", "合成物品", Trigger.CRAFT,
                        ConfigField.material("target", "目标物品", "DIAMOND"), 1),
                new TargetObjective("fish", "垂钓", Trigger.FISH,
                        ConfigField.optionalMaterial("target", "钓获物", ""), 1),
                new TargetObjective("kill", "击杀生物", Trigger.KILL,
                        ConfigField.optionalEntity("target", "生物类型", "ZOMBIE"), 1),
                new TargetObjective("consume", "消耗物品", Trigger.CONSUME,
                        ConfigField.material("target", "目标物品", "BREAD"), 1),
                new TargetObjective("enchant", "附魔", Trigger.ENCHANT,
                        ConfigField.optionalText("target", "附魔", "",
                                "附魔名，如 SHARPNESS、EFFICIENCY；留空或 * 表示任意附魔"), 1),
                new TargetObjective("shear", "剪切", Trigger.SHEAR,
                        ConfigField.optionalEntity("target", "被剪实体", ""), 1),
                new TargetObjective("breed", "繁殖", Trigger.BREED,
                        ConfigField.optionalEntity("target", "幼崽实体", ""), 1),
                new TargetObjective("tame", "驯服", Trigger.TAME,
                        ConfigField.optionalEntity("target", "生物类型", "WOLF"), 1),
                new TargetObjective("submit", "提交物品", Trigger.SUBMIT,
                        ConfigField.material("target", "提交物品", "DIAMOND"), 1),
                new TargetObjective("command", "执行命令", Trigger.COMMAND,
                        ConfigField.optionalText("target", "命令名", "home",
                                "不带前导斜杠的命令名，如 home；留空或 * 表示任意命令"), 1),
                // 自带判定逻辑的两种，仍然是独立类
                new InteractObjective(),
                new ChatObjective());
    }

    /**
     * 按 id 取内置目标类型。
     * <p>
     * 测试与管理员工具需要「拿到某个内置类型本身」（例如注册一个最小注册表），
     * 用 id 取比 {@code new BreakBlockObjective()} 更稳——类型改成数据行后
     * 就没有对应的类可 new 了。
     *
     * @throws java.util.NoSuchElementException id 不存在（属于写错 id，应当立刻失败）
     */
    public static ObjectiveType objective(String id) {
        return objectives().stream()
                .filter(type -> type.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("没有内置目标类型: " + id));
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
