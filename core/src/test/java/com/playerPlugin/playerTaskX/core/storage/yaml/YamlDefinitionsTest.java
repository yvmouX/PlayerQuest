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
 * YAML 导出/导入与「引号安全」的测试。
 *
 * <p>要防的错误全是静默的：字符串被写成裸量（{@code NO} 变成布尔、{@code 1.20} 变成浮点、
 * 空串变成 null）、长描述被折行、导出与导入的形状对不上导致「导出再导入就少东西」。</p>
 */
class YamlDefinitionsTest {

    @Test
    @DisplayName("任务往返：字段、目标、奖励、前置全都保留")
    void questRoundTrip() {
        Quest quest = new Quest("daily_mine", "<yellow>挖矿日常",
                List.of("<gray>挖掘 64 个石头", "第二行"), "STONE_PICKAXE", "每日", QuestType.DAILY,
                List.of("p1", "p2"),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64)),
                        QuestObjective.of("chat", Map.of("target", "你好", "amount", 1))),
                List.of(QuestReward.of("exp", Map.of("amount", 200))),
                1000.0, false);

        String yaml = YamlDefinitions.writeQuests(List.of(quest));
        List<Quest> back = YamlDefinitions.readQuests(yaml);

        assertEquals(1, back.size());
        Quest loaded = back.get(0);
        assertEquals(quest.id(), loaded.id());
        assertEquals(quest.name(), loaded.name(), "颜色标签要原样保留");
        assertEquals(quest.description(), loaded.description(), "描述按行存，不能折行或丢行");
        assertEquals(quest.type(), loaded.type());
        assertFalse(loaded.enabled(), "enabled 是布尔，往返不能变字符串");
        assertEquals(1000.0, loaded.refreshCost());
        assertEquals(quest.prerequisites(), loaded.prerequisites());
        assertEquals(quest.objectives(), loaded.objectives());
        assertEquals(quest.rewards(), loaded.rewards());
    }

    @Test
    @DisplayName("看起来像别的东西的字符串必须带引号：往返后仍是字符串原值")
    void ambiguousStringsSurvive() {
        // 这些都是合法配置值：发言关键词、材质名、命令名、尺寸文本…
        List<String> tricky = List.of("yes", "no", "on", "off", "NO", "y", "n", "true", "false",
                "1.20", "123", "0x10", "012", "*", "~", "null", ".inf", "", "带 空格", "冒号: 后面");

        List<Quest> quests = new java.util.ArrayList<>();
        for (int i = 0; i < tricky.size(); i++) {
            quests.add(new Quest("q" + i, "任务" + i, List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                    List.of(QuestObjective.of("chat", Map.of("target", tricky.get(i), "amount", 1))),
                    List.of(), 0.0, true));
        }

        List<Quest> back = YamlDefinitions.readQuests(YamlDefinitions.writeQuests(quests));

        assertEquals(tricky.size(), back.size());
        for (int i = 0; i < tricky.size(); i++) {
            Object value = back.get(i).objectives().get(0).properties().get("target");
            assertEquals(tricky.get(i), value, "「" + tricky.get(i) + "」往返后变了值");
            assertEquals(String.class, value.getClass(), "「" + tricky.get(i) + "」不再是字符串");
        }
    }

    @Test
    @DisplayName("数值与布尔属性保持原类型")
    void numbersAndBooleansKeepTheirType() {
        Quest quest = new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("x",
                        Map.of("amount", 64, "ratio", 1.5, "flag", true))),
                List.of(), 0.0, true);

        Map<String, Object> properties = YamlDefinitions.readQuests(YamlDefinitions.writeQuests(List.of(quest)))
                .get(0).objectives().get(0).properties();

        assertEquals(64, properties.get("amount"));
        assertEquals(1.5, properties.get("ratio"));
        assertEquals(true, properties.get("flag"));
    }

    @Test
    @DisplayName("导出省略空的可选字段，但关键字段一定在")
    void emptyOptionalFieldsAreOmitted() {
        Quest quest = new Quest("q", "任务", List.of(), "PAPER", "", QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("chat", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);

        String yaml = YamlDefinitions.writeQuests(List.of(quest));

        assertFalse(yaml.contains("description:"), yaml);
        assertFalse(yaml.contains("category:"), yaml);
        assertFalse(yaml.contains("prerequisites:"), yaml);
        assertTrue(yaml.contains("id: q"), yaml);
        assertTrue(yaml.contains("objectives:"), yaml);
    }

    @Test
    @DisplayName("长描述不折行：折行会在读回来时改变字符串")
    void longTextIsNotFolded() {
        String longLine = "很长的描述".repeat(40);
        Quest quest = new Quest("q", "任务", List.of(longLine), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("chat", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);

        List<Quest> back = YamlDefinitions.readQuests(YamlDefinitions.writeQuests(List.of(quest)));

        assertEquals(List.of(longLine), back.get(0).description());
    }

    @Test
    @DisplayName("导入接受三种形状：列表 / quests: 包一层 / 单个定义")
    void importAcceptsThreeShapes() {
        String single = """
                id: one
                name: 单个
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """;
        String list = """
                - id: one
                  name: 单个
                  objectives:
                    - type: chat
                      properties: { target: "", amount: 1 }
                """;
        String wrapped = """
                quests:
                  - id: one
                    name: 单个
                    objectives:
                      - type: chat
                        properties: { target: "", amount: 1 }
                """;

        for (String shape : List.of(single, list, wrapped)) {
            List<Quest> quests = YamlDefinitions.readQuests(shape);
            assertEquals(1, quests.size(), "这个形状没被识别:\n" + shape);
            assertEquals("one", quests.get(0).id());
        }
    }

    @Test
    @DisplayName("wrapper 键写了但不是列表：明确报错，而不是当成一条奇怪的任务")
    void wrapperMustBeAList() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> YamlDefinitions.readQuests("quests: 1"));
        assertTrue(failure.getMessage().contains("quests"), failure.getMessage());
    }

    @Test
    @DisplayName("语法错误的 YAML：抛异常，由调用方回 400")
    void brokenYamlThrows() {
        assertThrows(RuntimeException.class, () -> YamlDefinitions.readQuests("id: [ 未闭合\n"));
    }

    @Test
    @DisplayName("缺 id 的条目照样返回，交给导入流程列进「跳过了哪些」")
    void missingIdIsReportedByCaller() {
        List<Quest> quests = YamlDefinitions.readQuests("""
                - name: 没有 id
                - id: ok
                  objectives:
                    - type: chat
                      properties: { target: "", amount: 1 }
                """);

        assertEquals(2, quests.size(), "这里不能提前丢掉，否则用户以为全导入了");
        assertTrue(quests.get(0).id() == null || quests.get(0).id().isBlank());
    }

    @Test
    @DisplayName("预设往返：kind / name / type / properties 保留，导出带 kind")
    void presetRoundTrip() {
        Preset preset = new Preset(Preset.REWARDS, "exp_100", "100 经验", "exp",
                Map.of("amount", 100), "常用");

        String yaml = YamlDefinitions.writePresets(List.of(preset));
        List<Preset> back = YamlDefinitions.readPresets(yaml, Preset.OBJECTIVES);

        assertTrue(yaml.contains("kind: rewards"), yaml);
        assertEquals(1, back.size());
        assertEquals(preset.id(), back.get(0).id());
        assertTrue(back.get(0).isReward(), "导出的 kind 必须被读回来");
        assertEquals(100, back.get(0).properties().get("amount"));
    }

    @Test
    @DisplayName("预设导入：文件里没写 kind 时按调用方给的类别兜底")
    void presetImportUsesDefaultKind() {
        List<Preset> back = YamlDefinitions.readPresets("""
                type: exp
                properties: { amount: 100 }
                """, Preset.REWARDS);

        assertEquals(1, back.size());
        assertTrue(back.get(0).isReward());
    }
}
