package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PeriodSettings;
import com.playerPlugin.playerTaskX.core.period.Periods;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家周期任务界面（每日 / 每周 / 每月 / 自定义）。
 *
 * <h2>布局</h2>
 * <pre>
 *   第 1~5 行（0~44）：当前所选周期的任务，一个任务一个物品
 *   正中（22）       ：没有任务时的提示（屏障）
 *   第 6 行          ：周期切换（45..48，只显示已启用的周期）、刷新（49）、关闭（53）
 * </pre>
 *
 * <h2>为什么按周期切换而不是全部混在一起</h2>
 * 四种周期各有一套刷新费用与刷新上限，混在一个列表里时「这个刷新按钮扣哪份钱」根本说不清；
 * 切换后按钮的语义永远是「刷新当前正在看的这种周期」。只显示已启用的周期，
 * 服务器没开每周任务时不会多出两个点了没反应的按钮。
 * <p>
 * 标题固定写「周期任务」而不写当前周期：{@code Bukkit.createInventory} 的标题只在创建时定一次，
 * 把周期写进标题就得在每次切换时重建整个菜单——底部那排标签已经标出「当前是哪个」了。
 *
 * <h2>点击语义</h2>
 * <ul>
 *   <li>左键：已完成 → 领取奖励；进行中 → 打开详情；已领取 → 什么也不做；</li>
 *   <li>右键：恒为「打开详情」——已领取的任务同样需要回看目标与奖励，
 *       而左键在那种状态下必须保持「不产生任何副作用」。</li>
 * </ul>
 *
 * <h2>数据来源</h2>
 * 任务列表取 {@code PeriodicService.currentQuests(playerId, type)}，而不是「注册表里的任务」：
 * 前者是这名玩家这一周期实际抽到的那一批（含已完成待领取），后者是全局任务池。
 */
public final class PeriodicQuestMenu extends Menu {

    private static final int SIZE = 54;

    /** 任务区：前 5 行（0~44），最后一行留给按钮与周期切换。 */
    private static final int LIST_LIMIT = SIZE - 9;
    /** 没有任何任务时的提示位（正中）。 */
    private static final int EMPTY_SLOT = 22;
    /** 底部：周期切换占 45..48，刷新与关闭固定在最右两个位置。 */
    private static final int TYPE_SLOT_START = 45;
    private static final int REFRESH_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    /** 当前正在看哪种周期；切换后整页重建。 */
    private QuestType selected;

    public PeriodicQuestMenu(Player viewer, MessageService messages) {
        this(viewer, messages, null);
    }

    /**
     * @param initial 初始显示的周期；{@code null} 表示取第一个已启用的周期（通常就是每日）
     */
    public PeriodicQuestMenu(Player viewer, MessageService messages, QuestType initial) {
        super(viewer, messages, SIZE, "gui.periodic-title");
        List<QuestType> types = PlayerTaskX.getInstance().periodicService().enabledTypes();
        this.selected = initial != null ? initial : (types.isEmpty() ? QuestType.DAILY : types.get(0));
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();

        List<PlayerQuest> quests = plugin.periodicService().currentQuests(player.getUniqueId(), selected);
        if (quests.isEmpty()) {
            // 任务池为空或这种周期被关掉：给一句明确说明，而不是丢一个空界面给玩家猜
            set(EMPTY_SLOT, MenuItem.display(Material.BARRIER, text("periodic.none"), List.of()));
        } else {
            int slot = 0;
            for (PlayerQuest playerQuest : quests) {
                if (slot >= LIST_LIMIT) {
                    break;   // 任务数量由配置控制，正常远小于上限；超出的直接不展示
                }
                Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
                if (quest == null) {
                    // 任务定义已被删除但玩家记录还在：跳过这条脏数据，不影响后面任务的展示
                    continue;
                }
                set(slot++, questItem(plugin, quest, playerQuest));
            }
        }

        buildTypeButtons(plugin, player);
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
     * 「为什么领不到」由 {@code RewardService.ClaimOutcome} 一处判定并给出措辞，
     * 这里不再自己读状态拼句子——口径与 {@code /ptx claim} 因此天然一致。
     */
    private void claim(PlayerTaskX plugin, Quest quest) {
        Player player = viewer();
        plugin.rewardService().claim(player, quest.id()).report(messages(), player);
        refresh();
    }

    // ---------- 底部按钮 ----------

    /** 周期切换按钮：点一下换成看那种周期的任务（已启用的才画）。 */
    private void buildTypeButtons(PlayerTaskX plugin, Player player) {
        int slot = TYPE_SLOT_START;
        for (QuestType type : plugin.periodicService().enabledTypes()) {
            if (slot >= REFRESH_SLOT) {
                break;   // 最多四种周期，位置够；真超了也不挤掉刷新按钮
            }
            boolean active = type == selected;
            List<String> lore = List.of(text(active ? "gui.periodic-current" : "gui.periodic-switch"));
            MenuItem item = MenuItem.of(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    text("gui.periodic-button", Periods.label(type)), lore,
                    context -> {
                        selected = type;
                        refresh();
                    });
            set(slot++, item);
        }
    }

    private void buildButtons(PlayerTaskX plugin, Player player) {
        if (plugin.periodicService().remainingRefreshes(player.getUniqueId(), selected) > 0) {
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
        PeriodSettings settings = plugin.periodicService().settings(selected);
        double cost = settings.refreshCost();
        String label = cost > 0
                ? text("gui.refresh-cost", plugin.periodicService().formatCost(cost))
                : text("gui.refresh");
        return MenuItem.of(Material.CLOCK, label, List.of(), context -> doRefresh(plugin, player));
    }

    /** 执行刷新并按结果反馈；措辞由 {@link com.playerPlugin.playerTaskX.core.period.PeriodicService} 一处定义。 */
    private void doRefresh(PlayerTaskX plugin, Player player) {
        plugin.periodicService().refresh(player, selected).report(messages(), player);
        // 不论成败都重建界面：成功换了一批任务，失败也可能是跨周期后的免费重发
        refresh();
    }
}
