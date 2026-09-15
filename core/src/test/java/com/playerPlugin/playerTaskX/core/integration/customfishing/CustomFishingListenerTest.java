package com.playerPlugin.playerTaskX.core.integration.customfishing;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import net.momirealms.customfishing.api.event.FishingLootSpawnEvent;
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomFishing 钓获事件的翻译测试：防三类「不报错的错误」——没有 id 的钓获也被推成动作（与原版垂钓目标重复计数）、数量恒为 1（钓上一组鱼只算一条）、尺寸丢失（配了最小尺寸的目标永远不达标）。
 */
class CustomFishingListenerTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("正常钓获：id、尺寸、堆大小都如实翻译")
    void translatesLootIdSizeAndAmount() {
        ProgressService progress = collectingProgress();
        CustomFishingListener listener = new CustomFishingListener(progress, null);

        listener.onLoot(event("my_custom_fish", 42.5f, new ItemStack(Material.COD, 3)));

        ProgressContext context = captured(progress);
        assertEquals(Trigger.CUSTOM_FISH, context.trigger());
        assertEquals("my_custom_fish", context.target());
        assertEquals(3, context.amount(), "钓上一组鱼要按堆大小计");
        assertEquals("42.5", context.extra(), "尺寸要原样带过去，供「最小尺寸」判定");
    }

    @Test
    @DisplayName("没有 id 的钓获不推动作：那种情况归原版 fish 目标，推了会重复计数")
    void blankIdIsSkipped() {
        ProgressService progress = collectingProgress();
        CustomFishingListener listener = new CustomFishingListener(progress, null);

        listener.onLoot(event("", 10f, new ItemStack(Material.COD, 1)));
        listener.onLoot(event(null, 10f, new ItemStack(Material.COD, 1)));

        verify(progress, never()).apply(any());
    }

    @Test
    @DisplayName("钓获不是物品实体时数量记 1，而不是 0")
    void nonItemEntityCountsOnce() {
        ProgressService progress = collectingProgress();
        CustomFishingListener listener = new CustomFishingListener(progress, null);

        FishingLootSpawnEvent event = event("my_custom_fish", 10f, null);
        listener.onLoot(event);

        ProgressContext context = captured(progress);
        assertEquals(1, context.amount());
        assertTrue(context.amount() > 0, "数量为 0 会被上下文规范成 1，但这里不该走到那一步");
    }

    @Test
    @DisplayName("没有尺寸信息时 extra 为 null，而不是 0：配了最小尺寸的目标据此判不达标")
    void missingSizeStaysNull() {
        ProgressService progress = collectingProgress();
        CustomFishingListener listener = new CustomFishingListener(progress, null);

        listener.onLoot(event("my_custom_fish", null, new ItemStack(Material.COD, 1)));

        assertNull(captured(progress).extra());
    }

    @Test
    @DisplayName("没有玩家上下文时不推动作（不 NPE）")
    void missingPlayerIsIgnored() {
        ProgressService progress = collectingProgress();
        CustomFishingListener listener = new CustomFishingListener(progress, null);

        FishingLootSpawnEvent event = mock(FishingLootSpawnEvent.class);
        when(event.getPlayer()).thenReturn(null);

        listener.onLoot(event);

        verify(progress, never()).apply(any());
    }

    // ---------- 辅助 ----------

    private static ProgressService collectingProgress() {
        ProgressService progress = mock(ProgressService.class);
        when(progress.apply(any())).thenReturn(ApplyResult.NONE);
        return progress;
    }

    private static ProgressContext captured(ProgressService progress) {
        ArgumentCaptor<ProgressContext> captor = ArgumentCaptor.forClass(ProgressContext.class);
        verify(progress).apply(captor.capture());
        return captor.getValue();
    }

    /** 构造一次钓获事件；{@code stack} 为 null 表示掉落不是物品实体。 */
    private static FishingLootSpawnEvent event(String lootId, Float size, ItemStack stack) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);

        @SuppressWarnings("unchecked")
        Context<Player> context = mock(Context.class);
        when(context.arg(ContextKeys.ID)).thenReturn(lootId);
        when(context.arg(ContextKeys.SIZE)).thenReturn(size);

        FishingLootSpawnEvent event = mock(FishingLootSpawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getContext()).thenReturn(context);
        if (stack != null) {
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(stack);
            when(event.getEntity()).thenReturn(item);
        }
        return event;
    }
}
