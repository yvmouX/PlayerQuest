package com.playerPlugin.playerTaskX.core.gui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 界面布局表：把「第几行第几格」写成一张文本图，槽位名 → 下标。
 * 重名、一行超过 9 格、超出容器的行都当场抛——「两个功能抢同一个槽位、后者静默覆盖」这类错误必须在写的时候就炸。
 */
public final class SlotLayout {

    /** 空位占位符（格数照算，只是不登记名字）。 */
    public static final String EMPTY = ".";

    /** 一行的格数：与箱子界面一致。 */
    private static final int COLUMNS = 9;

    private final Map<String, Integer> slots;

    private SlotLayout(Map<String, Integer> slots) {
        this.slots = Map.copyOf(slots);
    }

    /**
     * 解析布局：每个词是一个槽位名（{@link #EMPTY} 表示空位），每行最多 9 格、最多 {@code size / 9} 行。
     *
     * @param size 容器大小（9 的倍数），用来挡「表比容器还长」
     */
    public static SlotLayout parse(int size, String... rows) {
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("布局表是空的");
        }
        int rowLimit = size / COLUMNS;
        if (rows.length > rowLimit) {
            throw new IllegalArgumentException(
                    "布局表写了 " + rows.length + " 行，容器大小 " + size + " 只放得下 " + rowLimit + " 行");
        }
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (int row = 0; row < rows.length; row++) {
            String[] cells = rows[row].trim().split("\\s+");
            if (cells.length > COLUMNS) {
                throw new IllegalArgumentException(
                        "布局表第 " + (row + 1) + " 行有 " + cells.length + " 格，一行最多 " + COLUMNS + " 格");
            }
            for (int column = 0; column < cells.length; column++) {
                String name = cells[column];
                if (name.isEmpty() || EMPTY.equals(name)) {
                    continue;
                }
                if (!name.matches("[A-Za-z0-9_-]+")) {
                    throw new IllegalArgumentException("槽位名 " + name + " 只能用字母、数字、下划线与连字符");
                }
                Integer previous = slots.putIfAbsent(name, row * COLUMNS + column);
                if (previous != null) {
                    throw new IllegalArgumentException("槽位名 " + name + " 出现了两次（第 "
                            + (previous / COLUMNS + 1) + " 行与第 " + (row + 1) + " 行）：一个槽位只能放一样东西");
                }
            }
        }
        return new SlotLayout(slots);
    }

    /** 槽位名 → 下标；没声明过就抛，并把已声明的名字列出来（写错名字不该静默摆到 0 号位）。 */
    public int slot(String name) {
        Integer slot = slots.get(name);
        if (slot == null) {
            throw new IllegalArgumentException("布局表里没有槽位「" + name + "」，只有 " + slots.keySet());
        }
        return slot;
    }

    /** 已声明的槽位名 → 下标（按声明顺序）。 */
    public Map<String, Integer> slots() {
        return slots;
    }
}
