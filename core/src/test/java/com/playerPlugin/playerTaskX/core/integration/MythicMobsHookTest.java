package com.playerPlugin.playerTaskX.core.integration;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mythic:} 目标语法的校验测试。
 *
 * <p>没装 MythicMobs（或装的是不支持的 4.x）时，这类目标<b>永远</b>命中不了，
 * 玩家侧只表现为「杀了不涨进度」。因此必须由校验报出来——本测试环境没有任何插件，
 * 正好是「未安装」这一支的天然场景。</p>
 */
class MythicMobsHookTest {

    @Test
    @DisplayName("识别 mythic: 目标：单独写、混在多值里、大小写不同都算")
    void detectsMythicTargets() {
        assertTrue(MythicMobsHook.isMythicTarget("mythic:SkeletalKnight"));
        assertTrue(MythicMobsHook.isMythicTarget("ZOMBIE, mythic:Boss"));
        assertTrue(MythicMobsHook.isMythicTarget("MYTHIC:Boss"), "前缀比较应忽略大小写");
    }

    @Test
    @DisplayName("普通目标、空值、纯原版多值都不算 mythic 目标")
    void doesNotFlagOrdinaryTargets() {
        assertFalse(MythicMobsHook.isMythicTarget("ZOMBIE"));
        assertFalse(MythicMobsHook.isMythicTarget("ZOMBIE,SKELETON"));
        assertFalse(MythicMobsHook.isMythicTarget(""));
        assertFalse(MythicMobsHook.isMythicTarget(null));
        // 只是名字里含 mythic 的普通实体名不该被误判
        assertFalse(MythicMobsHook.isMythicTarget("MYTHICAL_BEAST"));
    }

    @Test
    @DisplayName("未安装 MythicMobs 时，含 mythic: 目标的任务会被校验标出来")
    void reportsProblemsWhenUnsupported() {
        List<String> problems = MythicMobsHook.targetProblems(questWithTarget("ZOMBIE,mythic:Boss"));

        assertFalse(MythicMobsHook.supported(), "单测环境没有 MythicMobs");
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("mythic:Boss")
                        && problem.contains("MythicMobs")),
                "应报告「该目标需要 MythicMobs」，实际: " + problems);
    }

    @Test
    @DisplayName("纯原版目标不会被误报")
    void ordinaryQuestsHaveNoProblems() {
        assertTrue(MythicMobsHook.targetProblems(questWithTarget("ZOMBIE")).isEmpty());
        assertTrue(MythicMobsHook.targetProblems(null).isEmpty());
    }

    @Test
    @DisplayName("未安装时不创建接入实例（null 是正常状态）")
    void createReturnsNullWhenAbsent() {
        assertFalse(MythicMobsHook.supported());
        org.junit.jupiter.api.Assertions.assertNull(MythicMobsHook.create());
    }

    private static Quest questWithTarget(String target) {
        return new Quest("q1", "任务", List.of(), "PAPER", null, QuestType.NORMAL, List.of(),
                List.of(QuestObjective.of("kill", Map.of("target", target, "amount", 1))),
                List.of(), 0.0, true);
    }
}
