package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.integration.FakeMythicMobsHook;
import com.playerPlugin.playerTaskX.core.integration.MythicMobsHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 击杀事件的翻译测试：原版实体类型 + MythicMobs 别名。
 *
 * <p>三件事都只能靠测试钉住：没装 MythicMobs 时行为必须与从前完全一致；
 * 装了时怪物 id 要进别名（而不是再推一次动作）；MythicMobs 自己抛异常时
 * 玩家的这次击杀不能被吞掉。</p>
 */
class EntityListenerTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("未接入 MythicMobs：只推原版实体类型，不带别名")
    void withoutHookOnlyVanillaTarget() {
        ProgressService progress = collectingProgress();
        EntityListener listener = new EntityListener(progress, null, null);

        listener.onDeath(deathEvent());

        ProgressContext context = captured(progress);
        assertEquals("ZOMBIE", context.target());
        assertTrue(context.aliases().isEmpty(), "没装 MythicMobs 时不该凭空多出别名");
    }

    @Test
    @DisplayName("MythicMobs 怪物：一次动作带两个等价标识，而不是推两次")
    void mythicMobAddsAliasToTheSameAction() {
        ProgressService progress = collectingProgress();
        MythicMobsHook hook = FakeMythicMobsHook.ofMobId("SkeletalKnight");
        EntityListener listener = new EntityListener(progress, null, hook);

        listener.onDeath(deathEvent());

        ProgressContext context = captured(progress);
        assertEquals("ZOMBIE", context.target(), "主标识仍是原版实体类型");
        assertEquals(List.of("mythic:SkeletalKnight"), context.aliases(),
                "自定义怪的原版类型名与内部名必须同时可用");
        verify(progress).apply(any());
    }

    @Test
    @DisplayName("实体不是 MythicMobs 怪物时别名仍为空")
    void nonMythicEntityHasNoAlias() {
        ProgressService progress = collectingProgress();
        EntityListener listener = new EntityListener(progress, null, FakeMythicMobsHook.ofNothing());

        listener.onDeath(deathEvent());

        assertTrue(captured(progress).aliases().isEmpty());
    }

    @Test
    @DisplayName("MythicMobs 侧抛异常时击杀照常计入原版目标")
    void hookFailureDoesNotSwallowTheKill() {
        ProgressService progress = collectingProgress();
        MythicMobsHook hook = FakeMythicMobsHook.failing();
        EntityListener listener = new EntityListener(progress, null, hook);

        listener.onDeath(deathEvent());

        ProgressContext context = captured(progress);
        assertEquals("ZOMBIE", context.target());
        assertTrue(context.aliases().isEmpty(), "解析失败时退化为「没有别名」，而不是让事件失败");
    }

    @Test
    @DisplayName("非玩家致死不计入任何人的进度")
    void nonPlayerKillIsIgnored() {
        ProgressService progress = mock(ProgressService.class);
        EntityListener listener = new EntityListener(progress, null, FakeMythicMobsHook.ofMobId("Boss"));
        EntityDeathEvent event = deathEvent();
        when(event.getEntity().getKiller()).thenReturn(null);

        listener.onDeath(event);

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

    private static EntityDeathEvent deathEvent() {
        Player killer = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(PLAYER_ID);

        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getKiller()).thenReturn(killer);
        when(entity.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);
        when(event.getEntityType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);
        return event;
    }
}
