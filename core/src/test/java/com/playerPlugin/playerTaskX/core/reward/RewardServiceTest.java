package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.InMemoryPlayerQuestRepository;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 领取奖励的门禁测试：状态机。
 *
 * <p>只放行「已完成待领取」，其余状态各有各的措辞。
 * 领取成功必须把状态落成「已领取」，否则玩家能重复领奖。
 *
 * <p>奖励列表刻意留空：{@code grant} 在空奖励下不会碰 {@link Player}，
 * 因此这里只需要一个 {@code getUniqueId()} 可用的假玩家，不必起服务端。
 * 奖励的实际发放由 {@code CurrencyTypeTest} 等负责。</p>
 */
class RewardServiceTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private QuestRegistryImpl quests;
    private InMemoryPlayerQuestRepository playerQuests;
    private RewardService service;
    private Player player;

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        playerQuests = new InMemoryPlayerQuestRepository();
        service = new RewardService(quests, new RewardRegistryImpl(), playerQuests);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ALICE);
    }

    @Test
    @DisplayName("没有任务记录：未接取，且不写任何东西")
    void notAssignedWhenNoRecord() {
        upsert(quest("q1"));

        assertEquals(RewardService.ClaimOutcome.Status.NOT_ASSIGNED, service.claim(player, "q1").status());
    }

    @Test
    @DisplayName("进行中：不能领")
    void inProgressCannotClaim() {
        upsert(quest("q1"));
        playerQuests.save(record("q1", QuestStatus.IN_PROGRESS));

        assertEquals(RewardService.ClaimOutcome.Status.NOT_COMPLETED, service.claim(player, "q1").status());
    }

    @Test
    @DisplayName("已完成：领取成功，状态落成已领取")
    void claimWritesStatus() {
        upsert(quest("q1"));
        playerQuests.save(record("q1", QuestStatus.COMPLETED));

        RewardService.ClaimOutcome outcome = service.claim(player, "q1");
        assertTrue(outcome.claimed(), outcome.status().toString());
        assertEquals(QuestStatus.CLAIMED, playerQuests.find(ALICE, "q1").orElseThrow().status());
    }

    @Test
    @DisplayName("重复领取会被状态挡住")
    void alreadyClaimed() {
        upsert(quest("q1"));
        playerQuests.save(record("q1", QuestStatus.CLAIMED));

        assertEquals(RewardService.ClaimOutcome.Status.ALREADY_CLAIMED, service.claim(player, "q1").status());
    }

    // ---------- 辅助 ----------

    /** 注册若干任务定义（注册表接口是单条 upsert，这里只是省掉循环）。 */
    private void upsert(Quest... definitions) {
        for (Quest quest : definitions) {
            quests.upsert(quest);
        }
    }

    private static PlayerQuest record(String questId, QuestStatus status) {
        return new PlayerQuest(ALICE, questId, QuestType.DAILY, 0L, 0L, status);
    }

    private static Quest quest(String id) {
        return new Quest(id, "任务 " + id, List.of(), "PAPER", null, QuestType.DAILY,
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }
}
