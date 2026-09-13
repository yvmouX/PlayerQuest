package com.playerPlugin.playerTaskX.core.daily;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每日任务的核心不变量测试。
 * <p>
 * 这些是纯函数（周期计算、确定性抽取），因此不需要服务端环境。
 * 重点验证「同一玩家同一天结果一致」——这是每日任务最基本也最容易写错的性质。
 */
class DailyServiceTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Test
    @DisplayName("重置点之前的时间归属前一天")
    void periodBelongsToPreviousDayBeforeReset() {
        // 重置点 04:00：03:30 仍算前一天
        assertEquals("2026-09-09",
                DailyService.periodOf(LocalDateTime.of(2026, 9, 10, 3, 30), 4));
        // 04:00 整点起算新的一天
        assertEquals("2026-09-10",
                DailyService.periodOf(LocalDateTime.of(2026, 9, 10, 4, 0), 4));
        assertEquals("2026-09-10",
                DailyService.periodOf(LocalDateTime.of(2026, 9, 10, 23, 59), 4));
    }

    @Test
    @DisplayName("重置点为 0 时按自然日划分")
    void periodWithMidnightReset() {
        assertEquals("2026-09-10",
                DailyService.periodOf(LocalDateTime.of(2026, 9, 10, 0, 0), 0));
        assertEquals("2026-09-09",
                DailyService.periodOf(LocalDateTime.of(2026, 9, 10, 0, 0).minusMinutes(1), 0));
    }

    @Test
    @DisplayName("下一次重置时刻永远在未来")
    void nextResetIsAlwaysInFuture() {
        LocalDateTime beforeReset = LocalDateTime.of(2026, 9, 10, 2, 0);
        long next = DailyService.nextResetMillis(beforeReset, 4);
        assertTrue(next > beforeReset.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "重置点之前应返回当天 04:00");

        LocalDateTime afterReset = LocalDateTime.of(2026, 9, 10, 5, 0);
        long nextAfter = DailyService.nextResetMillis(afterReset, 4);
        assertTrue(nextAfter > afterReset.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "重置点之后应返回次日 04:00");
    }

    @Test
    @DisplayName("同一玩家同一周期抽取结果完全一致（重登不会换任务）")
    void drawIsDeterministicForSamePlayerAndPeriod() {
        List<Quest> pool = pool(30);

        List<Quest> first = DailyService.draw(pool, ALICE, "2026-09-10", 0, 5);
        List<Quest> second = DailyService.draw(pool, ALICE, "2026-09-10", 0, 5);

        assertEquals(ids(first), ids(second), "同一 (玩家, 周期, 刷新次数) 必须得到同一批任务");
    }

    @Test
    @DisplayName("不同玩家拿到不同批次（避免全服任务雷同）")
    void differentPlayersGetDifferentDraws() {
        List<Quest> pool = pool(30);
        assertNotEquals(ids(DailyService.draw(pool, ALICE, "2026-09-10", 0, 5)),
                ids(DailyService.draw(pool, BOB, "2026-09-10", 0, 5)));
    }

    @Test
    @DisplayName("刷新次数变化会得到另一批任务")
    void refreshChangesDraw() {
        List<Quest> pool = pool(30);
        assertNotEquals(ids(DailyService.draw(pool, ALICE, "2026-09-10", 0, 5)),
                ids(DailyService.draw(pool, ALICE, "2026-09-10", 1, 5)));
    }

    @Test
    @DisplayName("跨天后抽取结果改变")
    void newPeriodChangesDraw() {
        List<Quest> pool = pool(30);
        assertNotEquals(ids(DailyService.draw(pool, ALICE, "2026-09-10", 0, 5)),
                ids(DailyService.draw(pool, ALICE, "2026-09-11", 0, 5)));
    }

    @Test
    @DisplayName("抽取不重复，且数量受池大小与请求量约束")
    void drawHasNoDuplicatesAndRespectsBounds() {
        List<Quest> pool = pool(30);
        List<Quest> drawn = DailyService.draw(pool, ALICE, "2026-09-10", 0, 5);

        assertEquals(5, drawn.size());
        assertEquals(5, drawn.stream().map(Quest::id).distinct().count(), "不应抽到重复任务");

        // 请求数量超过池大小时按池大小截断，而不是抛异常
        List<Quest> all = DailyService.draw(pool, ALICE, "2026-09-10", 0, 100);
        assertEquals(pool.size(), all.size());
    }

    @Test
    @DisplayName("空池抽取返回空列表而不是抛异常")
    void emptyPoolDrawsNothing() {
        assertTrue(DailyService.draw(List.of(), ALICE, "2026-09-10", 0, 5).isEmpty());
    }

    @Test
    @DisplayName("抽取结果不影响候选池顺序（纯函数、无副作用）")
    void drawDoesNotMutatePool() {
        List<Quest> pool = pool(10);
        List<String> before = ids(pool);
        DailyService.draw(pool, ALICE, "2026-09-10", 0, 5);
        assertEquals(before, ids(pool), "候选池顺序必须保持不变，否则结果不再确定");
        assertFalse(pool.isEmpty());
    }

    // ---------- 辅助 ----------

    private List<Quest> pool(int size) {
        List<Quest> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            pool.add(new Quest("quest_" + i, "任务 " + i, List.of(), "PAPER", "每日",
                    QuestType.DAILY, List.of(),
                    List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 10))),
                    List.of(), 0.0, true));
        }
        return pool;
    }

    private List<String> ids(List<Quest> quests) {
        return quests.stream().map(Quest::id).toList();
    }
}
