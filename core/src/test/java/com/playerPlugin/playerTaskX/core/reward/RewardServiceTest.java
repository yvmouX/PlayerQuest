package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.quest.PrerequisiteService;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.InMemoryPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.InMemoryQuestClaimRepository;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 领取奖励的门禁测试：状态机 + 前置 + 永久账本。
 *
 * <p>三件事必须同时成立，任何一件单独看都「看起来没问题」：</p>
 * <ul>
 *   <li>状态必须是「已完成待领取」；</li>
 *   <li>前置必须全部<b>已领取</b>（不是已完成）；</li>
 *   <li>领取成功要同时写状态与账本——只写状态则跨天后前置判定失去依据，
 *       只写账本则玩家能重复领奖。</li>
 * </ul>
 *
 * <p>奖励列表刻意留空：{@code grant} 在空奖励下不会碰 {@link Player}，
 * 因此这里只需要一个 {@code getUniqueId()} 可用的假玩家，不必起服务端。
 * 奖励的实际发放由 {@code CurrencyTypeTest} 等负责。</p>
 */
class RewardServiceTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private QuestRegistryImpl quests;
    private InMemoryPlayerQuestRepository playerQuests;
    private InMemoryQuestClaimRepository claims;
    private RewardService service;
    private Player player;

    @BeforeEach
    void setUp() {
        quests = new QuestRegistryImpl();
        playerQuests = new InMemoryPlayerQuestRepository();
        claims = new InMemoryQuestClaimRepository();
        PrerequisiteService prerequisites = new PrerequisiteService(quests, claims);
        service = new RewardService(quests, new RewardRegistryImpl(), playerQuests, claims, prerequisites);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ALICE);
    }

    @Test
    @DisplayName("没有任务记录：未接取，且不写任何东西")
    void notAssignedWhenNoRecord() {
        upsert(quest("q1"));

        assertEquals(RewardService.ClaimOutcome.Status.NOT_ASSIGNED, service.claim(player, "q1").status());
        assertTrue(claims.claimedQuestIds(ALICE).isEmpty());
    }

    @Test
    @DisplayName("进行中：不能领")
    void inProgressCannotClaim() {
        upsert(quest("q1"));
        playerQuests.save(record("q1", QuestStatus.IN_PROGRESS));

        assertEquals(RewardService.ClaimOutcome.Status.NOT_COMPLETED, service.claim(player, "q1").status());
    }

    @Test
    @DisplayName("已完成且前置已领取：领取成功，状态与账本一起写")
    void claimWritesStatusAndLedger() {
        upsert(quest("p1"), quest("q1", "p1"));
        playerQuests.save(record("q1", QuestStatus.COMPLETED));
        claims.given(ALICE, "p1");

        RewardService.ClaimOutcome outcome = service.claim(player, "q1");
        assertTrue(outcome.claimed(), outcome.status().toString());
        assertEquals(QuestStatus.CLAIMED, playerQuests.find(ALICE, "q1").orElseThrow().status());
        assertEquals(java.util.Set.of("p1", "q1"), claims.claimedQuestIds(ALICE),
                "领取成功必须留下永久账本，否则前置判定跨天就失效");
    }

    @Test
    @DisplayName("已完成但前置未领取：领不到，状态不变、账本不写")
    void lockedByUnclaimedPrerequisite() {
        upsert(quest("p1"), quest("q1", "p1"));
        playerQuests.save(record("q1", QuestStatus.COMPLETED));

        RewardService.ClaimOutcome outcome = service.claim(player, "q1");
        assertEquals(RewardService.ClaimOutcome.Status.LOCKED, outcome.status());
        assertEquals(List.of("任务 p1"), outcome.missing(), "未满足的前置要给玩家看得懂的名字");
        assertEquals(QuestStatus.COMPLETED, playerQuests.find(ALICE, "q1").orElseThrow().status(),
                "被前置挡住时不该改动状态，玩家补完前置后仍能领");
        assertTrue(claims.claimedQuestIds(ALICE).isEmpty());
    }

    @Test
    @DisplayName("前置「已完成但未领取」不算数：判定标准是已领奖")
    void completedButUnclaimedPrerequisiteDoesNotUnlock() {
        upsert(quest("p1"), quest("q1", "p1"));
        playerQuests.save(record("p1", QuestStatus.COMPLETED));
        playerQuests.save(record("q1", QuestStatus.COMPLETED));

        assertEquals(RewardService.ClaimOutcome.Status.LOCKED, service.claim(player, "q1").status());
    }

    @Test
    @DisplayName("重复领取会被状态挡住")
    void alreadyClaimed() {
        upsert(quest("q1"));
        playerQuests.save(record("q1", QuestStatus.CLAIMED));

        assertEquals(RewardService.ClaimOutcome.Status.ALREADY_CLAIMED, service.claim(player, "q1").status());
    }

    @Test
    @DisplayName("前置任务名里的颜色标签会剥掉：否则会把整句提示染色")
    void missingPrerequisiteNameIsStripped() {
        upsert(new Quest("p1", "<yellow>挖矿日常", List.of(), "PAPER", null, QuestType.DAILY,
                        List.of(), List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                        List.of(), 0.0, true),
                quest("q1", "p1"));
        playerQuests.save(record("q1", QuestStatus.COMPLETED));

        assertEquals(List.of("挖矿日常"), service.claim(player, "q1").missing());
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

    private static Quest quest(String id, String... prerequisites) {
        return new Quest(id, "任务 " + id, List.of(), "PAPER", null, QuestType.DAILY,
                List.of(prerequisites),
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(), 0.0, true);
    }
}
