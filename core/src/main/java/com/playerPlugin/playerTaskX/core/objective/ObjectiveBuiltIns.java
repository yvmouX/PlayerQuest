package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;

import java.util.List;

/**
 * 内置目标类型清单（「插件自带哪些目标」的唯一事实来源）：显式登记而不扫描包，新增类型必须改这里。
 * 每个 target 字段的值域也在这里定下——校验按它判断「配了却永远不会命中」的值，编辑器按它推候选清单与图标。
 */
public final class ObjectiveBuiltIns {

    private ObjectiveBuiltIns() {
    }

    /** 全部内置目标类型，按登记顺序排列（注册表按序展示）。 */
    public static List<ObjectiveType> all() {
        return List.of(
                new TargetObjective("break_block", "挖掘方块", Trigger.BREAK_BLOCK,
                        ConfigField.of("target", "目标方块", "方块名，如 DIAMOND_ORE", ValueKind.BLOCK)),
                new TargetObjective("place_block", "放置方块", Trigger.PLACE_BLOCK,
                        ConfigField.of("target", "目标方块",
                                "方块名，如 STONE；只有能拿在手里放下的方块才算", ValueKind.PLACEABLE)),
                new TargetObjective("craft", "合成物品", Trigger.CRAFT,
                        ConfigField.of("target", "目标物品", "物品名，如 DIAMOND", ValueKind.ITEM)),
                new TargetObjective("fish", "垂钓", Trigger.FISH,
                        ConfigField.of("target", "钓获物",
                                "物品名，如 COD；留空或 * 表示任意物品", ValueKind.ITEM)),
                // CustomFishing 的自定义鱼：单独一个动作，因为原版垂钓事件看不到那些掉落
                new CustomFishObjective(),
                new TargetObjective("kill", "击杀生物", Trigger.KILL,
                        ConfigField.of("target", "生物类型",
                                "实体类型名，如 ZOMBIE；装了 MythicMobs 5.x 时也可写 mythic:<怪物id>；"
                                        + "留空或 * 表示任意", ValueKind.LIVING)),
                new TargetObjective("consume", "消耗物品", Trigger.CONSUME,
                        ConfigField.of("target", "目标物品", "物品名，如 BREAD", ValueKind.ITEM)),
                new TargetObjective("enchant", "附魔", Trigger.ENCHANT,
                        ConfigField.of("target", "附魔",
                                "附魔名，如 SHARPNESS、EFFICIENCY；留空或 * 表示任意附魔",
                                ValueKind.ENCHANTMENT)),
                new TargetObjective("shear", "剪切", Trigger.SHEAR,
                        ConfigField.of("target", "被剪实体",
                                "能剪毛的生物，如 SHEEP；留空或 * 表示任意", ValueKind.SHEARABLE)),
                new TargetObjective("breed", "繁殖", Trigger.BREED,
                        ConfigField.of("target", "幼崽实体",
                                "能繁殖的动物，如 COW；留空或 * 表示任意", ValueKind.BREEDABLE)),
                new TargetObjective("tame", "驯服", Trigger.TAME,
                        ConfigField.of("target", "生物类型",
                                "能驯服的生物，如 WOLF、CAT；留空或 * 表示任意", ValueKind.TAMEABLE)),
                new TargetObjective("submit", "提交物品", Trigger.SUBMIT,
                        ConfigField.of("target", "提交物品", "物品名，如 DIAMOND", ValueKind.ITEM)),
                new TargetObjective("command", "执行命令", Trigger.COMMAND,
                        ConfigField.text("target", "命令名",
                                "不带前导斜杠的命令名，如 home；留空或 * 表示任意命令")),
                // 自带判定逻辑的两种，仍然是独立类
                new InteractObjective(),
                new ChatObjective());
    }

    /** 按 id 取内置目标类型；id 不存在时抛 {@link java.util.NoSuchElementException}（类型是数据行，没有可 new 的类）。 */
    public static ObjectiveType byId(String id) {
        return all().stream()
                .filter(type -> type.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("没有内置目标类型: " + id));
    }
}
