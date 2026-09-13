package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家每日任务界面。
 *
 * <h2>布局</h2>
 * <pre>
 *   第 1~5 行（0~44）：每日任务，一个任务一个物品
 *   正中（22）       ：没有任务时的提示（屏障）
 *   第 6 行          ：刷新（49，仅当还有剩余次数）、关闭（53）
 * </pre>
 *
 * <h2>点击语义</h2>
 * <ul>
 *   <li>左键：已完成 → 领取奖励；进行中 → 打开详情；已领取 → 什么也不做；</li>
 *   <li>右键：恒为「打开详情」——已领取的任务同样需要回看目标与奖励，
 *       而左键在那种状态下必须保持「不产生任何副作用」。</li>
 * </ul>
 *
 * <h2>数据来源</h2>
 * 任务列表取 {@code DailyService.currentQuests(playerId)}，而不是「注册表里的每日任务」：
 * 前者是这名玩家今天实际抽到的那一批（含已完成待领取），后者是全局任务池。
 */
public final class DailyQuestMenu extends Menu {

    private static final int SIZE = 54;

    /** 任务区：前 5 行（0~44），最后一行留给按钮。 */
    private static final int LIST_LIMIT = SIZE - 9;
    /** 没有任何每日任务时的提示位（正中）。 */
    private static final int EMPTY_SLOT = 22;
    /** 底部操作行。 */
    private static final int REFRESH_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    public DailyQuestMenu(Player viewer, MessageService messages) {
        super(viewer, messages, SIZE, "gui.daily-title");
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();

        List<PlayerQuest> quests = plugin.dailyService().currentQuests(player.getUniqueId());
        if (quests.isEmpty()) {
            // 任务池为空或每日任务被关掉：给一句明确说明，而不是丢一个空界面给玩家猜
            set(EMPTY_SLOT, MenuItem.display(Material.BARRIER, text("daily.none"), List.of()));
        } else {
            int slot = 0;
            for (PlayerQuest playerQuest : quests) {
                if (slot >= LIST_LIMIT) {
                    break;   // 每日任务数量由配置控制，正常远小于上限；超出的直接不展示
                }
                Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
                if (quest == null) {
                    // 任务定义已被删除但玩家记录还在：跳过这条脏数据，不影响后面任务的展示
                    continue;
                }
                set(slot++, questItem(plugin, quest, playerQuest));
            }
        }

        buildButtons(plugin, player);
        fill(MenuItem.filler());
    }

    // ---------- 任务物品 ----------

    /** 任务物品：进度行 + 状态 + 操作提示。 */
    private MenuItem questItem(PlayerTaskX plugin, Quest quest, PlayerQuest playerQuest) {
        List<String> lore = new ArrayList<>();
        // ProgressDisplay.render 返回「已渲染任务名 + & 颜色码」的混合文本，MenuItem 会统一过一遍 render，
        // 否则进度条与百分比里的 &7/&a 会被当成普通字符摆在玩家面前
        lore.add(plugin.progressDisplay().render(quest, playerQuest));
        lore.add(statusLine(playerQuest.status()));
        lore.add(text(playerQuest.status() == QuestStatus.COMPLETED ? "gui.click-to-claim" : "gui.click-to-view"));

        return MenuItem.of(MenuItem.material(quest.icon()), quest.name(), lore, context -> {
            if (context.clickType().isRightClick()) {
                openDetail(quest, playerQuest);
                return;
            }
            switch (playerQuest.status()) {
                case COMPLETED -> claim(plugin, quest);
                case IN_PROGRESS -> openDetail(quest, playerQuest);
                // 已领取（CLAIMED）与已放弃（ABANDONED）：左键不产生任何副作用
                default -> {
                }
            }
        });
    }

    /**
     * 状态行。
     * <p>
     * 只映射三种：{@code currentQuests} 已把 ABANDONED 过滤掉了，
     * 因此不需要为「已放弃」再准备一句文案。
     */
    private String statusLine(QuestStatus status) {
        return switch (status) {
            case COMPLETED -> text("gui.completed");
            case CLAIMED -> text("gui.claimed");
            default -> text("gui.in-progress");
        };
    }

    private void openDetail(Quest quest, PlayerQuest playerQuest) {
        new QuestDetailMenu(viewer(), messages(), quest, playerQuest).open();
    }

    // ---------- 领取 ----------

    /**
     * 领取奖励。
     * <p>
     * {@code RewardService.claim} 只回一个布尔值，领不到时按玩家记录的<b>最新</b>状态还原原因，
     * 口径与 {@code /ptx claim} 保持一致——同一个功能两个入口说法不同会让玩家以为遇到了 bug。
     */
    private void claim(PlayerTaskX plugin, Quest quest) {
        Player player = viewer();
        if (plugin.rewardService().claim(player, quest.id())) {
            messages().send(player, "quest.claimed", Texts.render(quest.name()));
            refresh();
            return;
        }
        PlayerQuest live = plugin.playerQuestRepository().find(player.getUniqueId(), quest.id()).orElse(null);
        if (live == null) {
            messages().send(player, "quest.unavailable");
        } else if (live.status() == QuestStatus.CLAIMED) {
            // 连点两次：第一次已经领走，第二次看到的还是旧物品
            messages().send(player, "quest.already-claimed");
        } else {
            messages().send(player, "quest.not-completed");
        }
        refresh();
    }

    // ---------- 底部按钮 ----------

    private void buildButtons(PlayerTaskX plugin, Player player) {
        if (plugin.dailyService().remainingRefreshes(player.getUniqueId()) > 0) {
            set(REFRESH_SLOT, refreshButton(plugin, player));
        }
        set(CLOSE_SLOT, MenuItem.of(Material.BARRIER, text("gui.close"), List.of(),
                context -> player.closeInventory()));
    }

    /**
     * 刷新按钮（仅当还有剩余次数时出现）。
     * <p>
     * 有费用时把<b>本次费用</b>写在按钮上：点击前就能看到代价，比一个无信息量的按钮有用。
     */
    private MenuItem refreshButton(PlayerTaskX plugin, Player player) {
        double cost = plugin.config().getDailyRefreshCost();
        String label = cost > 0
                ? text("gui.refresh-cost", plugin.dailyService().formatCost(cost))
                : text("gui.refresh");
        return MenuItem.of(Material.CLOCK, label, List.of(), context -> doRefresh(plugin, player));
    }

    /** 执行刷新并按结果反馈；措辞由 {@link com.playerPlugin.playerTaskX.core.daily.DailyService} 一处定义。 */
    private void doRefresh(PlayerTaskX plugin, Player player) {
        plugin.dailyService().refresh(player).report(messages(), player);
        // 不论成败都重建界面：成功换了一批任务，失败也可能是跨天后的免费重发
        refresh();
    }
}
