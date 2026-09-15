package com.playerPlugin.playerTaskX.core.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局表测试：这张表是「界面长什么样」的唯一声明，写错的表现以前是「两个功能悄悄抢同一个槽位」，
 * 因此每条错误路径都必须炸，并且要说清是哪一行哪一格。
 */
class SlotLayoutTest {

    @Test
    @DisplayName("槽位名 → 下标：按行按格算，等于第 (行-1)*9+列")
    void mapsNameToSlot() {
        SlotLayout layout = SlotLayout.parse(54,
                "a    .    .    .    .    .    .    .    .",
                ".    .    .    .    b    .    .    .    .",
                ".    .    .    .    .    .    .    .    .",
                ".    .    .    .    .    .    .    .    .",
                ".    .    .    .    .    .    .    .    .",
                ".    .    save .    back .    .    .    .");

        assertEquals(0, layout.slot("a"));
        assertEquals(13, layout.slot("b"), "第 2 行第 5 格");
        assertEquals(47, layout.slot("save"), "第 6 行第 3 格");
        assertEquals(49, layout.slot("back"), "第 6 行第 5 格");
        assertEquals(Map.of("a", 0, "b", 13, "save", 47, "back", 49), layout.slots());
    }

    @Test
    @DisplayName("`.` 与空白不算槽位名，短行照常按格算")
    void dotsAreEmpty() {
        SlotLayout layout = SlotLayout.parse(54,
                "a . . . . . . . b",
                "",
                ". . c");

        assertEquals(8, layout.slot("b"), "一行可以少写几格，位置按顺序算");
        assertEquals(20, layout.slot("c"));
        assertEquals(3, layout.slots().size());
    }

    @Test
    @DisplayName("重名抛错：一个槽位只能放一样东西，静默覆盖正是这张表要根除的 bug")
    void duplicateNameThrows() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SlotLayout.parse(54, "save . . . . . . . .", ". . save . . . . . ."));

        assertTrue(failure.getMessage().contains("save"), failure.getMessage());
        assertTrue(failure.getMessage().contains("两次"), failure.getMessage());
    }

    @Test
    @DisplayName("一行超过 9 格 / 行数超过容器 / 空表都抛")
    void malformedTablesThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> SlotLayout.parse(54, "a a2 a3 a4 a5 a6 a7 a8 a9 a10"));
        assertThrows(IllegalArgumentException.class,
                () -> SlotLayout.parse(27, "a . . . . . . . .", ". . . . . . . . .",
                        ". . . . . . . . .", ". . . . . . . . ."));
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.parse(54));
    }

    @Test
    @DisplayName("名字不存在时抛错并列出已声明的名字（写错名字不该静默摆到 0 号位）")
    void unknownNameThrows() {
        SlotLayout layout = SlotLayout.parse(54, "save . . . . . . . .");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> layout.slot("sav"));
        assertTrue(failure.getMessage().contains("sav"), failure.getMessage());
        assertTrue(failure.getMessage().contains("save"), "错误信息要给出可选的名字: " + failure.getMessage());
    }

    @Test
    @DisplayName("槽位名只允许字母数字下划线连字符（防手滑写成标点或中文）")
    void invalidNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.parse(54, "保存 . . . . . . . ."));
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.parse(54, "save,back . . . . . . ."));
    }
}
