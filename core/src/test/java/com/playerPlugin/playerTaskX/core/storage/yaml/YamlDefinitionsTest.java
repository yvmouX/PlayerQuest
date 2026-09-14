package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YAML 导出/导入（一个文件一个定义）与「引号安全」的测试。
 *
 * <p>要防的错误全是静默的：字符串被写成裸量（{@code NO} 变成布尔、{@code 1.20} 变成浮点、
 * 空串变成 null）、长描述被折行、导出与导入的形状对不上导致「导出再导入就少东西」。</p>
 */
class YamlDefinitionsTest {

    @Test
    @DisplayName("任务往返：字段、目标、奖励全都保留")
    void questRoundTrip() {
        Quest quest = new Quest("daily_mine", "<yellow>挖矿日常",
                List.of("<gray>挖掘 64 个石头", "第二行"), "STONE_PICKAXE", "每日", QuestType.DAILY,
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64)),
                        QuestObjective.of("chat", Map.of("target", "你好", "amount", 1))),
                List.of(QuestReward.of("money", Map.of("amount", 200))),
                1000.0, false);

        Quest loaded = YamlDefinitions.readQuest(YamlDefinitions.writeQuest(quest));

        assertEquals(quest.id(), loaded.id());
        assertEquals(quest.name(), loaded.name(), "颜色标签要原样保留");
        assertEquals(quest.description(), loaded.description(), "描述按行存，不能折行或丢行");
        assertEquals(quest.type(), loaded.type());
        assertFalse(loaded.enabled(), "enabled 是布尔，往返不能变字符串");
        assertEquals(1000.0, loaded.refreshCost());
        assertEquals(quest.objectives(), loaded.objectives());
        assertEquals(quest.rewards(), loaded.rewards());
    }

    @Test
    @DisplayName("看起来像别的东西的字符串必须带引号：往返后仍是字符串原值")
    void ambiguousStringsSurvive() {
        // 这些都是合法配置值：发言关键词、材质名、命令名、尺寸文本…
        List<String> tricky = List.of("yes", "no", "on", "off", "NO", "y", "n", "true", "false",
                "1.20", "123", "0x10", "012", "*", "~", "null", ".inf", "", "带 空格", "冒号: 后面");

        for (String value : tricky) {
            Quest quest = new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                    List.of(QuestObjective.of("chat", Map.of("target", value, "amount", 1))),
                    List.of(), 0.0, true);

            Object loaded = YamlDefinitions.readQuest(YamlDefinitions.writeQuest(quest))
                    .objectives().get(0).properties().get("target");

            assertEquals(value, loaded, "「" + value + "」往返后变了值");
            assertEquals(String.class, loaded.getClass(), "「" + value + "」不再是字符串");
        }
    }

    @Test
    @DisplayName("数值与布尔属性保持原类型")
    void numbersAndBooleansKeepTheirType() {
        Quest quest = new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("x",
                        Map.of("amount", 64, "ratio", 1.5, "flag", true))),
                List.of(), 0.0, true);

        Map<String, Object> properties = YamlDefinitions.readQuest(YamlDefinitions.writeQuest(quest))
                .objectives().get(0).properties();

        assertEquals(64, properties.get("amount"));
        assertEquals(1.5, properties.get("ratio"));
        assertEquals(true, properties.get("flag"));
    }

    @Test
    @DisplayName("导出省略空的可选字段，但关键字段一定在")
    void emptyOptionalFieldsAreOmitted() {
        Quest quest = new Quest("q", "任务", List.of(), "PAPER", "", QuestType.NORMAL,
                List.of(QuestObjective.of("chat", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);

        String yaml = YamlDefinitions.writeQuest(quest);

        assertFalse(yaml.contains("description:"), yaml);
        assertFalse(yaml.contains("category:"), yaml);
        assertTrue(yaml.contains("id: q"), yaml);
        assertTrue(yaml.contains("objectives:"), yaml);
    }

    @Test
    @DisplayName("长描述不折行：折行会在读回来时改变字符串")
    void longTextIsNotFolded() {
        String longLine = "很长的描述".repeat(40);
        Quest quest = new Quest("q", "任务", List.of(longLine), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("chat", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);

        Quest back = YamlDefinitions.readQuest(YamlDefinitions.writeQuest(quest));

        assertEquals(List.of(longLine), back.description());
    }

    @Test
    @DisplayName("导入只接受一个定义：顶层是列表 / 标量 / 空文件都明确报错并指出该用 zip")
    void importAcceptsOneDefinitionOnly() {
        String single = """
                id: one
                name: 单个
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """;
        assertEquals("one", YamlDefinitions.readQuest(single).id());

        // 顶层是列表（旧的多任务清单）：报错文案要指出出路，而不是静默只取第一条
        IllegalArgumentException listed = assertThrows(IllegalArgumentException.class,
                () -> YamlDefinitions.readQuest("- " + single.replace("\n", "\n  ")));
        assertTrue(listed.getMessage().contains("zip"), listed.getMessage());
        assertTrue(listed.getMessage().contains("quests/"), listed.getMessage());

        // 标量与空文件同样拒绝
        assertThrows(IllegalArgumentException.class, () -> YamlDefinitions.readQuest("就一句话"));
        assertThrows(IllegalArgumentException.class, () -> YamlDefinitions.readQuest(""));
        // 语法错误：抛异常，由调用方回 400
        assertThrows(RuntimeException.class, () -> YamlDefinitions.readQuest("id: [ 未闭合\n"));
    }

    @Test
    @DisplayName("预设往返：kind / name / type / properties 保留，导出带 kind")
    void presetRoundTrip() {
        Preset preset = new Preset(Preset.REWARDS, "money_100", "100 金币", "money",
                Map.of("amount", 100), "常用");

        String yaml = YamlDefinitions.writePreset(preset);
        Preset back = YamlDefinitions.readPreset(yaml, Preset.OBJECTIVES);

        assertTrue(yaml.contains("kind: rewards"), yaml);
        assertEquals(preset.id(), back.id());
        assertTrue(back.isReward(), "导出的 kind 必须被读回来");
        assertEquals(100, back.properties().get("amount"));
    }

    @Test
    @DisplayName("预设导入：文件里没写 kind 时按调用方给的类别兜底；缺 type 明确报错")
    void presetImportUsesDefaultKind() {
        Preset back = YamlDefinitions.readPreset("""
                type: money
                properties: { amount: 100 }
                """, Preset.REWARDS);

        assertTrue(back.isReward());
        assertThrows(IllegalArgumentException.class,
                () -> YamlDefinitions.readPreset("name: 只有名字\n", Preset.OBJECTIVES));
    }
}
