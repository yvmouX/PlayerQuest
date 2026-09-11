package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
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
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.CommandReward;
import com.playerPlugin.playerTaskX.core.reward.ExpReward;
import com.playerPlugin.playerTaskX.core.reward.ItemReward;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 示例任务的数据合法性检查。
 *
 * <p>示例任务是给管理员对照格式的活文档，里面的材质名、实体名、类型 id
 * 若拼错，编译与运行都不会报错——只会得到一个「永远做不动」的任务或
 * 启动日志里的一串警告。这里把全部示例过一遍真实注册表与 Bukkit 枚举。</p>
 */
class ExampleQuestsTest {

    private static final double ANY_REFRESH_COST = 1000.0;

    private static ObjectiveRegistryImpl objectiveTypes;
    private static RewardRegistryImpl rewardTypes;

    @BeforeAll
    static void setUpRegistries() {
        objectiveTypes = new ObjectiveRegistryImpl();
        for (ObjectiveType type : List.of(
                new BreakBlockObjective(), new PlaceBlockObjective(), new CraftObjective(),
                new FishObjective(), new KillObjective(), new ConsumeObjective(),
                new EnchantObjective(), new ShearObjective(), new BreedObjective(),
                new TameObjective(), new InteractObjective(), new ChatObjective(),
                new SubmitObjective(), new CommandObjective())) {
            objectiveTypes.register(type);
        }
        // 只登记 id 供比对；available() 会探测 Bukkit 插件，单测环境没有服务端，不能调
        rewardTypes = new RewardRegistryImpl();
        rewardTypes.register(new MoneyReward());
        rewardTypes.register(new PointsReward());
        rewardTypes.register(new ExpReward());
        rewardTypes.register(new ItemReward());
        rewardTypes.register(new CommandReward());
    }

    private static List<Quest> examples() {
        return ExampleQuests.all(ANY_REFRESH_COST);
    }

    @Test
    @DisplayName("id 唯一、结构完整、图标是真实材质")
    void idsUniqueAndStructureValid() {
        Set<String> seen = new HashSet<>();
        for (Quest quest : examples()) {
            assertTrue(seen.add(quest.id()), "示例任务 id 重复: " + quest.id());
            assertTrue(quest.isUsable(), "示例任务 " + quest.id() + " 缺少目标");
            assertNotNull(Material.matchMaterial(quest.icon()),
                    "示例任务 " + quest.id() + " 的图标不是有效材质: " + quest.icon());
            assertTrue(quest.enabled(), "示例任务 " + quest.id() + " 不应默认禁用");
        }
    }

    @Test
    @DisplayName("每日池必须大于默认抽取数量（3），否则「每天换一批」名存实亡")
    void dailyPoolLargerThanDefaultDraw() {
        long dailies = examples().stream().filter(Quest::isDaily).count();
        assertTrue(dailies > 3, "每日示例只有 " + dailies + " 个，与默认抽取数相同就没有抽取的意义了");
    }

    @Test
    @DisplayName("每日示例的刷新费用统一取传入配置，常驻示例固定为 0")
    void refreshCostFollowsConvention() {
        for (Quest quest : examples()) {
            if (quest.isDaily()) {
                assertEquals(ANY_REFRESH_COST, quest.refreshCost(),
                        "示例任务 " + quest.id() + " 的刷新费用应取配置值");
            } else {
                assertEquals(0.0, quest.refreshCost(), "常驻任务 " + quest.id() + " 不应有刷新费用");
            }
        }
    }

    @Test
    @DisplayName("目标与奖励的类型 id 都必须在注册表里")
    void typeIdsRegistered() {
        for (Quest quest : examples()) {
            for (QuestObjective objective : quest.objectives()) {
                assertTrue(objectiveTypes.contains(objective.type()),
                        "示例任务 " + quest.id() + " 引用了未注册的目标类型: " + objective.type());
            }
            for (QuestReward reward : quest.rewards()) {
                assertTrue(rewardTypes.contains(reward.type()),
                        "示例任务 " + quest.id() + " 引用了未注册的奖励类型: " + reward.type());
            }
        }
    }

    @Test
    @DisplayName("材质/实体目标必须是真实枚举名——拼错不报错，只会永远做不动")
    void enumTargetsExist() {
        for (Quest quest : examples()) {
            for (QuestObjective objective : quest.objectives()) {
                String target = objective.string("target", "");
                if (target.isBlank() || "*".equals(target.trim())) {
                    continue;
                }
                for (String part : target.split(",")) {
                    String name = part.trim();
                    if (name.isEmpty() || "*".equals(name)) {
                        continue;
                    }
                    switch (objective.type()) {
                        // 这些类型的目标是物品/方块材质
                        case "break_block", "place_block", "craft", "consume", "submit", "fish" ->
                                assertNotNull(Material.matchMaterial(name),
                                        "示例任务 " + quest.id() + " 的目标不是有效材质: " + name);
                        // 这些类型的目标是实体类型
                        case "kill", "tame", "shear", "breed" ->
                                assertEnumValue(EntityType.class, name,
                                        "示例任务 " + quest.id() + " 的目标不是有效实体类型: " + name);
                        // enchant/chat/command 是自由文本，不做枚举校验
                        default -> { }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("奖励属性有效：物品奖励材质真实、数量为正，数值奖励大于 0")
    void rewardPropertiesValid() {
        for (Quest quest : examples()) {
            for (QuestReward reward : quest.rewards()) {
                switch (reward.type()) {
                    case "item" -> {
                        String material = reward.string("material", "");
                        assertNotNull(Material.matchMaterial(material),
                                "示例任务 " + quest.id() + " 的物品奖励材质无效: " + material);
                        assertTrue(reward.integer("amount", 0) > 0,
                                "示例任务 " + quest.id() + " 的物品奖励数量应为正数");
                    }
                    case "money", "exp", "points" -> assertTrue(reward.decimal("amount", 0) > 0,
                            "示例任务 " + quest.id() + " 的 " + reward.type() + " 奖励数量应大于 0");
                    default -> { }
                }
            }
        }
    }

    private static <E extends Enum<E>> void assertEnumValue(Class<E> type, String name, String message) {
        try {
            Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(message, e);
        }
    }
}
