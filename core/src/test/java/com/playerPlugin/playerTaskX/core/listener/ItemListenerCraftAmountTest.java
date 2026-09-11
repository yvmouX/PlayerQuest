package com.playerPlugin.playerTaskX.core.listener;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 合成数量计算：重点是 Shift+点击的「一次事件连做多批」。
 *
 * <p>曾经的 bug：只按单批产物数量计数（火把 4 个/批），玩家 Shift 一点
 * 连做 64 批（256 个）却只记 4 个进度，火把工坊示例要做出约 4 组火把
 * 才算完成。该问题不抛异常、不报错，只有真玩才会发现，必须用测试钉住。</p>
 */
class ItemListenerCraftAmountTest {

    @Test
    @DisplayName("普通点击按单批产物数量计")
    void singleClickCountsOneBatch() {
        ItemStack[] matrix = {new ItemStack(Material.COAL), new ItemStack(Material.STICK)};
        assertEquals(4, ItemListener.craftedAmount(false, matrix, 4));
        // 产物为 1 的配方至少记 1
        assertEquals(1, ItemListener.craftedAmount(false, matrix, 1));
    }

    @Test
    @DisplayName("Shift+点击按批数×单批产物计：64 煤+64 木棍一点 = 256 个火把")
    void shiftClickCountsAllBatches() {
        ItemStack[] matrix = {
                new ItemStack(Material.COAL, 64), new ItemStack(Material.STICK, 64),
                null, null, null, null, null, null, null
        };
        assertEquals(256, ItemListener.craftedAmount(true, matrix, 4));
    }

    @Test
    @DisplayName("批数由原料最少的格子决定")
    void batchesLimitedByScarcestIngredient() {
        ItemStack[] matrix = {
                new ItemStack(Material.COAL, 3), new ItemStack(Material.STICK, 64),
                null, null, null, null, null, null, null
        };
        assertEquals(12, ItemListener.craftedAmount(true, matrix, 4));
    }

    @Test
    @DisplayName("产物为 1 的配方 Shift+点击按原料数计")
    void resultOneRecipeCountsBatches() {
        ItemStack[] matrix = {
                new ItemStack(Material.IRON_INGOT, 32),
                null, null, null, null, null, null, null, null
        };
        assertEquals(32, ItemListener.craftedAmount(true, matrix, 1));
    }

    @Test
    @DisplayName("AIR 格子与 null 格子一样忽略，不把批数算成 0")
    void airSlotsIgnored() {
        ItemStack[] matrix = {
                new ItemStack(Material.AIR), new ItemStack(Material.COAL, 2),
                new ItemStack(Material.STICK, 8), null, null, null, null, null, null
        };
        assertEquals(8, ItemListener.craftedAmount(true, matrix, 4));
    }

    @Test
    @DisplayName("拿不到有效合成格时退回单批数量，不会计 0")
    void emptyMatrixFallsBackToResultAmount() {
        assertEquals(4, ItemListener.craftedAmount(true, new ItemStack[0], 4));
        ItemStack[] airOnly = {new ItemStack(Material.AIR)};
        assertEquals(4, ItemListener.craftedAmount(true, airOnly, 4));
    }
}
