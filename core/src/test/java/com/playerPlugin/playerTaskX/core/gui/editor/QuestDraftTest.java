package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 草稿的读写语义测试：界面只是壳子，真正会写坏数据的都在这里。
 * 最要紧的一条是「预设引用不能被展开成生效值再存回去」——那样存一次就把引用关系弄丢了，而任务表面上还照常跑。
 */
class QuestDraftTest {

    @Test
    @DisplayName("新建草稿：id 与名称待填、类型 NORMAL、默认启用、没有目标与奖励")
    void creatingDefaults() {
        QuestDraft draft = QuestDraft.creating();

        assertEquals("", draft.id());
        assertEquals("", draft.name());
        assertEquals("", draft.category());
        assertEquals(QuestType.NORMAL, draft.type());
        assertTrue(draft.enabled());
        assertEquals(0.0, draft.refreshCost());
        assertTrue(draft.nodes(false).isEmpty());
        assertTrue(draft.nodes(true).isEmpty());
        assertTrue(!draft.toQuest().isUsable(), "没有目标的任务不该被当成可用");
    }

    @Test
    @DisplayName("编辑现有任务：原样往返所有基本字段")
    void roundTripsBasicFields() {
        Quest quest = new Quest("miner", "挖矿", List.of("挖 64 个石头"), "DIAMOND", "采集",
                QuestType.DAILY, List.of(new QuestObjective("break_block", Map.of("target", "STONE"))),
                List.of(new QuestReward("money", Map.of("amount", 100))), 500.0, false);

        assertEquals(quest, QuestDraft.of(quest).toQuest());
    }

    @Test
    @DisplayName("预设引用保持为引用：不把展开后的生效值写回去")
    void keepsPresetReference() {
        // 注册表里放的是展开后的生效值，作者写的只有 preset 键；草稿必须按 authored 入手
        QuestObjective resolved = new QuestObjective("break_block", Map.of("target", "STONE"),
                Map.of(QuestObjective.PRESET_KEY, "stone"));
        Quest quest = new Quest("miner", "挖矿", List.of(), "DIAMOND", "", QuestType.NORMAL,
                List.of(resolved), List.of(), 0, true);

        QuestDraft draft = QuestDraft.of(quest);

        assertEquals("stone", draft.nodes(false).get(0).authored().get(QuestObjective.PRESET_KEY),
                "草稿里应当只剩引用，而不是 STONE");
        assertEquals(Map.of(QuestObjective.PRESET_KEY, "stone"), draft.nodes(false).get(0).authored());
        assertEquals("stone", draft.toQuest().objectives().get(0).presetId(),
                "存回去时引用必须还在，否则这次编辑就把预设关系降级成了写死的字段");
    }

    @Test
    @DisplayName("新增节点是追加：不能顶掉第一个")
    void addNodeAppends() {
        QuestDraft draft = QuestDraft.creating();
        draft.addNode(false, new QuestDraft.Node("break_block", Map.of("target", "STONE")));
        draft.addNode(false, new QuestDraft.Node("craft", Map.of("target", "DIAMOND")));
        draft.addNode(true, new QuestDraft.Node("money", Map.of("amount", 5)));

        List<QuestDraft.Node> objectives = draft.nodes(false);
        assertEquals(2, objectives.size());
        assertEquals("break_block", objectives.get(0).type());
        assertEquals("craft", objectives.get(1).type());
        assertEquals(1, draft.nodes(true).size());
    }

    @Test
    @DisplayName("目标与奖励各自独立：改一个不影响另一个")
    void objectivesAndRewardsAreSeparate() {
        QuestDraft draft = QuestDraft.creating();
        draft.addNode(false, new QuestDraft.Node("break_block", Map.of()));
        draft.addNode(true, new QuestDraft.Node("money", Map.of()));
        draft.removeNode(true, 0);

        assertEquals(1, draft.nodes(false).size(), "删奖励不该动到目标");
        assertTrue(draft.nodes(true).isEmpty());
    }

    @Test
    @DisplayName("移动节点：交换相邻位置，越界不动")
    void swapNodes() {
        QuestDraft draft = QuestDraft.creating();
        draft.addNode(false, new QuestDraft.Node("a", Map.of()));
        draft.addNode(false, new QuestDraft.Node("b", Map.of()));
        draft.addNode(false, new QuestDraft.Node("c", Map.of()));

        draft.swapNodes(false, 0, 1);
        assertEquals(List.of("b", "a", "c"), types(draft));

        draft.swapNodes(false, 1, 2);
        assertEquals(List.of("b", "c", "a"), types(draft));

        // 越界（第一项上移、末项下移都靠它兜住，否则点一下就是一个 IndexOutOfBounds）
        draft.swapNodes(false, 0, -1);
        draft.swapNodes(false, 2, 3);
        draft.swapNodes(false, 9, 0);
        assertEquals(List.of("b", "c", "a"), types(draft));
    }

    @Test
    @DisplayName("删除节点：删掉的是指定那一个，顺序保持")
    void removeNode() {
        QuestDraft draft = QuestDraft.creating();
        draft.addNode(false, new QuestDraft.Node("a", Map.of()));
        draft.addNode(false, new QuestDraft.Node("b", Map.of()));
        draft.addNode(false, new QuestDraft.Node("c", Map.of()));

        draft.removeNode(false, 1);

        assertEquals(List.of("a", "c"), types(draft));
    }

    @Test
    @DisplayName("替换节点配置：改字段就是换一份配置，类型不变")
    void replaceNode() {
        QuestDraft draft = QuestDraft.creating();
        draft.addNode(false, new QuestDraft.Node("break_block", Map.of("target", "STONE")));

        draft.node(false, 0, new QuestDraft.Node("break_block", Map.of("target", "DIRT", "amount", 3)));

        assertEquals(Map.of("target", "DIRT", "amount", 3), draft.nodes(false).get(0).authored());
        assertEquals(3, draft.toQuest().objectives().get(0).integer("amount", 0));
    }

    @Test
    @DisplayName("空字段一律归一成空串，界面不必到处判 null")
    void blankFieldsBecomeEmptyStrings() {
        Quest quest = new Quest("miner", null, null, null, null, null,
                List.of(new QuestObjective("break_block", Map.of("target", "STONE"))), null, 0, true);

        QuestDraft draft = QuestDraft.of(quest);

        assertNotNull(draft.id());
        assertNotNull(draft.name());
        assertNotNull(draft.category());
        assertNotNull(draft.icon());
        assertEquals("", draft.name());
        assertEquals("", draft.category());
        assertEquals(QuestType.NORMAL, draft.type());
        assertNull(draft.nodes(true).isEmpty() ? null : "奖励应当为空");
    }

    private static List<String> types(QuestDraft draft) {
        return draft.nodes(false).stream().map(QuestDraft.Node::type).toList();
    }
}
