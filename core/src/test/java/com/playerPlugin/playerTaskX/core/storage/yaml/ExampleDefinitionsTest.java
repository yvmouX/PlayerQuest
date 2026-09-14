package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出厂示例（随插件发布的 {@code quests/} 与 {@code presets/} 资源）的测试。
 *
 * <p>这里挡两类问题，它们的共同点是「不报错」：
 * <ul>
 *   <li><b>示例没进包</b>：资源目录被改名/漏打包时，插件启动后既不铺示例也不报错，
 *       表现只是「新服里一个任务都没有」；</li>
 *   <li><b>示例自己是错的</b>：材质名拼错、类型被改名、奖励参数少了占位符——
 *       示例是给管理员照抄的活文档，错的东西会被抄进真实配置。</li>
 * </ul>
 * 校验走的是生产同一套读取路径（{@link DefinitionFolder} → {@link YamlDefinitions}），
 * 因此「文件名即 id」「内容里不写 id」这些约定也一并被钉住。
 */
class ExampleDefinitionsTest {

    /** 出厂示例的数量；改示例文件时这个数字跟着改，否则会漏掉「少了一个文件」这种问题。 */
    private static final int QUEST_EXAMPLES = 12;
    private static final int PRESET_EXAMPLES = 11;

    private static ObjectiveRegistryImpl objectiveTypes;
    private static RewardRegistryImpl rewardTypes;

    private final List<String> warnings = new ArrayList<>();

    @BeforeAll
    static void setUpRegistries() {
        objectiveTypes = new ObjectiveRegistryImpl();
        for (ObjectiveType type : BuiltIns.objectives()) {
            objectiveTypes.register(type);
        }
        // 只登记 id 供比对：money/points 的 available() 会探测 Bukkit 插件，单测环境没有服务端
        rewardTypes = new RewardRegistryImpl();
        rewardTypes.register(new MoneyReward());
        rewardTypes.register(new PointsReward());
        rewardTypes.register(new CommandReward());
    }

    @Test
    @DisplayName("示例随资源发布：任务 12 个、预设 11 个，文件名就是 id")
    void resourcesArePackaged(@TempDir Path dir) {
        DefinitionFolder quests = questFolder(dir);
        DefinitionFolder presets = presetFolder(dir);

        assertEquals(QUEST_EXAMPLES, ExampleDefinitions.seedQuests(quests),
                "quests/ 资源没进包，或数量变了（改了示例文件就同步这个数字）");
        assertEquals(PRESET_EXAMPLES, ExampleDefinitions.seedPresets(presets),
                "presets/ 资源没进包，或数量变了（改了示例文件就同步这个数字）");

        // 文件名即 id：目录里落地的文件与资源一一对应，且内容里没有 id（不必重复写）
        assertEquals(QUEST_EXAMPLES, yamlNames(quests).size());
        assertEquals(PRESET_EXAMPLES, yamlNames(presets).size());
        assertTrue(yamlNames(quests).contains("example_daily_mine"),
                "文件按资源里的名字落地，实际: " + yamlNames(quests));

        for (Quest quest : YamlDefinitions.questSources(quests).all()) {
            assertTrue(quest.id().startsWith("example_"), "示例 id 应统一带 example_ 前缀: " + quest.id());
        }
    }

    @Test
    @DisplayName("目录非空时一个字节都不写；已存在的文件不被覆盖")
    void neverTouchesANonEmptyFolder(@TempDir Path dir) throws IOException {
        DefinitionFolder quests = questFolder(dir);
        // 数据目录由插件创建（DefinitionFolder.ensureExists），测试里自己建一下
        quests.ensureExists();
        String mine = "name: 我自己的任务\nobjectives:\n- type: chat\n  properties: {}\n";
        Files.writeString(quests.pathOf("mine"), mine);

        assertEquals(0, ExampleDefinitions.seedQuests(quests), "目录里已经有定义，不该再铺示例");
        assertEquals(mine, Files.readString(quests.pathOf("mine")), "管理员自己的文件必须原样不动");
        assertEquals(1, yamlNames(quests).size());

        // 目录里只有一个非 YAML 文件也算「有人在用这个目录」之外的另一种情况：
        // 这里只要求「不因为目录里有别的文件就覆盖已有 YAML」，因此再加一个 YAML 仍是 0 写入
        Files.writeString(quests.pathOf("another"), "name: 又一个\nobjectives:\n- type: chat\n  properties: {}\n");
        assertEquals(0, ExampleDefinitions.seedQuests(quests));
    }

    @Test
    @DisplayName("任务示例的数据合法：类型已注册、材质/实体真实、奖励参数有效")
    void questExamplesAreValid(@TempDir Path dir) {
        DefinitionFolder folder = questFolder(dir);
        ExampleDefinitions.seedQuests(folder);

        Set<String> ids = new HashSet<>();
        int dailies = 0;
        for (Quest quest : YamlDefinitions.questSources(folder).all()) {
            assertTrue(ids.add(quest.id()), "示例任务 id 重复: " + quest.id());
            assertTrue(quest.isUsable(), "示例任务 " + quest.id() + " 缺少目标");
            assertNotNull(Material.matchMaterial(quest.icon()),
                    "示例任务 " + quest.id() + " 的图标不是有效材质: " + quest.icon());
            assertTrue(quest.enabled(), "示例任务 " + quest.id() + " 不应默认禁用");
            if (quest.isPeriodic()) {
                dailies++;
                assertTrue(quest.refreshCost() > 0,
                        "周期示例 " + quest.id() + " 应带一个刷新费用（示例就是给管理员照抄的）");
            } else {
                assertEquals(0.0, quest.refreshCost(), "常驻示例 " + quest.id() + " 不该有刷新费用");
            }
            for (QuestObjective objective : quest.objectives()) {
                assertTrue(objectiveTypes.find(objective.type()).isPresent(),
                        "示例任务 " + quest.id() + " 引用了未注册的目标类型: " + objective.type());
                assertTargetNamesValid(quest.id(), objective);
            }
            for (QuestReward reward : quest.rewards()) {
                assertTrue(rewardTypes.find(reward.type()).isPresent(),
                        "示例任务 " + quest.id() + " 引用了未注册的奖励类型: " + reward.type());
                assertRewardValid(quest.id(), reward);
            }
        }
        assertTrue(dailies > 3, "每日示例只有 " + dailies + " 个，与默认抽取数相同就没有抽取的意义了");
    }

    @Test
    @DisplayName("预设示例的数据合法：kind / type / 参数都对")
    void presetExamplesAreValid(@TempDir Path dir) {
        DefinitionFolder folder = presetFolder(dir);
        ExampleDefinitions.seedPresets(folder);

        List<Preset> presets = YamlDefinitions.presetSources(folder).all();
        assertEquals(PRESET_EXAMPLES, presets.size(), "有预设没被读出来（kind 写错会被跳过并告警）");
        assertTrue(warnings.isEmpty(), "示例预设不该产生告警: " + warnings);

        Set<String> ids = new HashSet<>();
        boolean hasObjective = false;
        boolean hasReward = false;
        for (Preset preset : presets) {
            assertTrue(ids.add(preset.id()), "示例预设 id 重复: " + preset.id());
            assertTrue(!preset.name().isBlank(), "示例预设 " + preset.id() + " 缺少显示名");
            if (preset.isReward()) {
                hasReward = true;
                assertTrue(rewardTypes.find(preset.type()).isPresent(),
                        "预设 " + preset.id() + " 引用了未注册的奖励类型: " + preset.type());
                assertRewardValid(preset.id(), QuestReward.of(preset.type(), preset.properties()));
                continue;
            }
            hasObjective = true;
            String target = text(preset.properties().get("target"));
            if (!target.isEmpty()) {
                assertTargetNamesValid(preset.id(), QuestObjective.of(preset.type(), preset.properties()));
            }
            Object amount = preset.properties().get("amount");
            if (amount != null) {
                assertTrue(((Number) amount).longValue() > 0,
                        "预设 " + preset.id() + " 的数量应为正数，实际 " + amount);
            }
        }
        assertTrue(hasObjective && hasReward, "两类预设都要有示例，否则编辑器里看不到另一类长什么样");
    }

    // ---------- 辅助 ----------

    private DefinitionFolder questFolder(Path dir) {
        return new DefinitionFolder(dir.resolve("quests"), "quests", warnings::add);
    }

    private DefinitionFolder presetFolder(Path dir) {
        return new DefinitionFolder(dir.resolve("presets"), "presets", warnings::add);
    }

    /** 目录下的 YAML 文件名（不带扩展名）。 */
    private static Set<String> yamlNames(DefinitionFolder folder) {
        Set<String> names = new HashSet<>();
        try (var files = Files.list(folder.directory())) {
            files.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        names.add(name.substring(0, name.length() - ".yml".length()));
                    });
        } catch (IOException e) {
            throw new AssertionError("读取示例目录失败: " + e.getMessage(), e);
        }
        return names;
    }

    /** 目标名必须是真实枚举名、或明确的「任意」（留空 / {@code *}）；拼错不报错，只会永远做不动。 */
    private static void assertTargetNamesValid(String owner, QuestObjective objective) {
        String target = objective.string("target", "");
        if (target.isBlank() || "*".equals(target.trim())) {
            return;
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
                                owner + " 的目标不是有效材质: " + name);
                // 这些类型的目标是实体类型
                case "kill", "tame", "shear", "breed" -> assertEnumValue(EntityType.class, name,
                        owner + " 的目标不是有效实体类型: " + name);
                // enchant / chat / command 是自由文本，不做枚举校验
                default -> { }
            }
        }
    }

    /** 奖励参数：货币奖励数量为正，命令奖励必须写命令且带 {@code %player%} 占位符。 */
    private static void assertRewardValid(String owner, QuestReward reward) {
        switch (reward.type()) {
            case "money", "points" -> assertTrue(reward.decimal("amount", 0) > 0,
                    owner + " 的 " + reward.type() + " 奖励数量应大于 0");
            case "command" -> {
                String command = reward.string("command", "");
                assertTrue(!command.isBlank(), owner + " 的命令奖励没有写命令");
                // 少了占位符就成了「每次奖励都发给同一个人」，是这类配置最常见的手滑
                assertTrue(command.contains("%player%"),
                        owner + " 的命令奖励缺少 %player% 占位符: " + command);
            }
            default -> { }
        }
    }

    private static <E extends Enum<E>> void assertEnumValue(Class<E> type, String name, String message) {
        try {
            Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(message, e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
