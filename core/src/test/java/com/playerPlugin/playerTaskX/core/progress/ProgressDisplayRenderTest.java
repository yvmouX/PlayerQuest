package com.playerPlugin.playerTaskX.core.progress;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.objective.BreakBlockObjective;
import com.playerPlugin.playerTaskX.core.quest.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进度行渲染测试。
 * <p>
 * 来源：玩家截图显示 actionbar 上出现了 {@code &7&a&8||||} 颜色码字面量与
 * {@code <yellow>} 标签原文。根因是进度行由「任务名（可能是 MiniMessage）」
 * 与「拼接段（{@code &} 码）」混合而成，而渲染只做了一半。
 * 这里固化「交给玩家之前必须是纯 § 码、无标签、无 & 码」这条不变量。
 */
class ProgressDisplayRenderTest {

    private Quest quest() {
        return new Quest("example_daily_mine", "<yellow>挖矿日常",
                List.of("<gray>挖掘 64 个石头"), "STONE_PICKAXE", "每日", QuestType.DAILY,
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64))),
                List.of(QuestReward.of("money", Map.of("amount", 500))), 1000.0, true);
    }

    private ProgressDisplay display() {
        ObjectiveRegistryImpl objectives = new ObjectiveRegistryImpl();
        objectives.register(new BreakBlockObjective());
        // 消息服务与仓储不参与 render()，这里允许为 null
        return new ProgressDisplay(new PluginConfig(), new QuestRegistryImpl(), null, null, objectives);
    }

    @Test
    @DisplayName("进度行不含 & 颜色码与 MiniMessage 标签，只含 § 格式码")
    void renderIsFullyRendered() {
        Quest quest = quest();
        PlayerQuest playerQuest = PlayerQuest.assign(UUID.randomUUID(), quest, 0L, 0L);

        String line = display().render(quest, playerQuest);

        assertFalse(line.contains("&"),
                "进度行不应含 & 颜色码字面量，否则玩家会在 actionbar 上看到它们: " + line);
        assertFalse(line.contains("<"),
                "进度行不应含 MiniMessage 标签原文: " + line);
        assertTrue(line.contains("\u00A7"),
                "进度行应含 § 格式码用于着色: " + line);
    }

    @Test
    @DisplayName("进度行包含任务名、进度条、百分比与目标进度")
    void renderContainsAllParts() {
        Quest quest = quest();
        PlayerQuest playerQuest = PlayerQuest.assign(UUID.randomUUID(), quest, 0L, 0L);

        String line = display().render(quest, playerQuest);

        assertTrue(line.contains("挖矿日常"), "应含任务名: " + line);
        assertTrue(line.contains("0%"), "应含完成百分比: " + line);
        assertTrue(line.contains("0/64"), "应含目标进度: " + line);
        assertTrue(line.contains("|"), "应含进度条: " + line);
    }

    @Test
    @DisplayName("进度变化后百分比与目标计数都随之更新（曾经数字不动、只有条会涨）")
    void renderReflectsProgress() {
        Quest quest = quest();
        UUID playerId = UUID.randomUUID();
        PlayerQuest playerQuest = PlayerQuest.assign(playerId, quest, 0L, 0L);
        ProgressDisplay display = display();

        String before = display.render(quest, playerQuest);

        playerQuest.addProgress(0, 32, 64);
        String after = display.render(quest, playerQuest);

        assertTrue(before.contains("0%"), "初始应为 0%: " + before);
        assertTrue(before.contains("0/64"), "初始目标计数应为 0/64: " + before);

        assertTrue(after.contains("50%"), "进度过半后应为 50%: " + after);
        // 这条断言针对线上 bug：进度条会涨但数字停在 0/64
        assertTrue(after.contains("32/64"), "目标计数必须同步更新: " + after);
        assertFalse(after.contains("0/64"), "不应再显示旧的 0/64: " + after);
    }

    @Test
    @DisplayName("目标类型缺失时回退到类型自带显示名，不把内部 id 抛给玩家")
    void labelFallsBackToTypeDisplayName() {
        Quest quest = quest();
        PlayerQuest playerQuest = PlayerQuest.assign(UUID.randomUUID(), quest, 0L, 0L);

        String line = display().render(quest, playerQuest);

        assertFalse(line.contains("break_block"),
                "不应把内部目标类型 id 直接展示给玩家: " + line);
        assertTrue(line.contains("挖掘方块"), "应使用目标类型的显示名: " + line);
    }

    @Test
    @DisplayName("未知目标类型时才退化为显示原始 id（保证可见性而非静默丢失）")
    void unknownTypeShowsRawId() {
        Quest quest = new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("nonexistent_type", Map.of("amount", 5))),
                List.of(), 0.0, true);
        // 注意断言的是「不崩溃且包含数量」，而不是具体文案
        String line = display().render(quest, PlayerQuest.assign(UUID.randomUUID(), quest, 0L, 0L));
        assertTrue(line.contains("0/5"), "未知类型仍应显示进度: " + line);
        assertEquals(false, line.contains("&"), "即使类型未知也不应泄漏 & 码: " + line);
    }
}
