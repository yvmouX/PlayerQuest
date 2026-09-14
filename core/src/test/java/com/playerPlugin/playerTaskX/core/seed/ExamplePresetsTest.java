package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.CommandReward;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认预设的数据合法性检查。
 *
 * <p>与 {@link ExampleQuestsTest} 同样的理由：预设是给管理员对照格式的样例，
 * 里面的类型 id、材质名、实体名拼错时编译与运行都不报错——只会在套用后
 * 得到一个永远做不动的目标，或者一个发不出东西的奖励。这里过一遍真实注册表与枚举。</p>
 *
 * <p>另外钉住 id 唯一：预设是按 id 覆盖的，重复 id 会让前一条被静默顶掉，
 * 而列表里看起来「少了一条」根本不像错误。</p>
 */
class ExamplePresetsTest {

    private static ObjectiveRegistryImpl objectiveTypes;
    private static RewardRegistryImpl rewardTypes;

    @BeforeAll
    static void setUpRegistries() {
        objectiveTypes = new ObjectiveRegistryImpl();
        // 与生产共用同一份清单：预设里写的类型 id 一旦被改名，这里立刻失败
        for (ObjectiveType type : BuiltIns.objectives()) {
            objectiveTypes.register(type);
        }
        // 只登记 id 供比对；available() 会探测 Bukkit 插件，单测环境没有服务端，不能调
        rewardTypes = new RewardRegistryImpl();
        rewardTypes.register(new MoneyReward());
        rewardTypes.register(new PointsReward());
        rewardTypes.register(new CommandReward());
    }

    private static List<Preset> presets() {
        return ExamplePresets.all();
    }

    @Test
    @DisplayName("id 唯一、类别合法、显示名与类型都不为空")
    void idsUniqueAndFieldsPresent() {
        Set<String> seen = new HashSet<>();
        for (Preset preset : presets()) {
            assertTrue(seen.add(preset.id()), "默认预设 id 重复: " + preset.id());
            assertTrue(Preset.OBJECTIVES.equals(preset.kind()) || Preset.REWARDS.equals(preset.kind()),
                    "预设 " + preset.id() + " 的类别非法: " + preset.kind());
            assertFalse(preset.name() == null || preset.name().isBlank(),
                    "预设 " + preset.id() + " 缺少显示名");
            assertFalse(preset.type() == null || preset.type().isBlank(),
                    "预设 " + preset.id() + " 缺少类型 id");
        }
    }

    @Test
    @DisplayName("目标与奖励两类都要有，否则编辑器里会缺掉一半的「怎么用」")
    void bothKindsPresent() {
        assertTrue(presets().stream().anyMatch(preset -> !preset.isReward()), "缺少目标预设");
        assertTrue(presets().stream().anyMatch(Preset::isReward), "缺少奖励预设");
    }

    @Test
    @DisplayName("类型 id 必须在对应注册表里")
    void typeIdsRegistered() {
        for (Preset preset : presets()) {
            boolean known = preset.isReward()
                    ? rewardTypes.contains(preset.type())
                    : objectiveTypes.contains(preset.type());
            assertTrue(known, "预设 " + preset.id() + " 引用了未注册的"
                    + (preset.isReward() ? "奖励" : "目标") + "类型: " + preset.type());
        }
    }

    @Test
    @DisplayName("目标预设的 target 必须是真实枚举名；命令奖励预设必须写命令")
    void enumValuesExist() {
        for (Preset preset : presets()) {
            if (preset.isReward()) {
                if (!"command".equals(preset.type())) {
                    continue;
                }
                String command = text(preset.properties().get("command"));
                assertTrue(!command.isBlank(), "预设 " + preset.id() + " 的命令奖励没有写命令");
                assertTrue(command.contains("%player%"),
                        "预设 " + preset.id() + " 的命令奖励缺少 %player% 占位符: " + command);
                continue;
            }
            String target = text(preset.properties().get("target"));
            if (target.isEmpty()) {
                // 留空 = 任意，这是刻意的约定（见 ExampleQuests 的注释）
                continue;
            }
            for (String part : target.split(",")) {
                String name = part.trim();
                if (name.isEmpty()) {
                    continue;
                }
                switch (preset.type()) {
                    // 这些类型的目标是物品/方块材质
                    case "break_block", "place_block", "craft", "consume", "submit", "fish" ->
                            assertNotNull(Material.matchMaterial(name),
                                    "预设 " + preset.id() + " 的目标不是有效材质: " + name);
                    // 这些类型的目标是实体类型
                    case "kill", "tame", "shear", "breed" -> assertEnumValue(EntityType.class, name,
                            "预设 " + preset.id() + " 的目标不是有效实体类型: " + name);
                    // enchant/chat/command 是自由文本，不做枚举校验
                    default -> { }
                }
            }
        }
    }

    @Test
    @DisplayName("数值型配置为正：数量写成 0 的预设套用后等于没配")
    void amountsArePositive() {
        for (Preset preset : presets()) {
            Object amount = preset.properties().get("amount");
            if (amount == null) {
                continue;
            }
            long value = ((Number) amount).longValue();
            assertTrue(value > 0, "预设 " + preset.id() + " 的数量应为正数，实际 " + value);
        }
    }

    /** 属性表里的取值统一按文本比对，与仓储读出来的形态保持一致。 */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static <E extends Enum<E>> void assertEnumValue(Class<E> type, String name, String message) {
        try {
            Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(message, e);
        }
    }
}
