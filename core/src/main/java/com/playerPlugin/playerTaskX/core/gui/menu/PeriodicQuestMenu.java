package com.playerPlugin.playerTaskX.core.gui.menu;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PeriodSettings;
import cn.yvmou.ylib.gui.SlotLayout;
import com.playerPlugin.playerTaskX.core.period.Periods;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import cn.yvmou.ylib.gui.Menu;
import cn.yvmou.ylib.gui.MenuItem;

/**
 * 玩家周期任务界面：按周期切换查看自己的任务，可领取奖励或打开详情。
 * 四种周期各有刷新费用与上限，混在一起说不清扣哪份，故按周期切换；标题固定不写周期名（{@code createInventory} 的标题只在创建时定一次）；任务列表取 {@code PeriodicService.currentQuests}，不是全局任务池。
 * 界面用 {@link SlotLayout} 的文本图画出来：{@code #} 是任务槽（动态，见 {@code fill(名字, 物品)}），周期标签与刷新/关闭是固定槽位。
 */
public final class PeriodicQuestMenu extends Menu {

    /** 任务列表占的格子名（动态槽位：按玩家实际任务数填）。 */
    private static final String TASKS = "#";

    /** 界面布局（见 {@link SlotLayout}）：第 1~5 行是任务槽，最后一行是周期标签、刷新与关闭。 */
    private static final String[] SHAPE = {
            "#########",
            "#########",
            "#########",
            "#########",
            "#########",
            "`daily``weekly``monthly``custom``refresh`   `close`",
    };

    /** 当前正在看哪种周期；切换后整页重建。 */
    private QuestType selected;

    public PeriodicQuestMenu(Player viewer, MessageService messages) {
        this(viewer, messages, null);
    }

    /**
     * @param initial 初始显示的周期；{@code null} 表示取第一个已启用的周期（通常就是每日）
     */
    public PeriodicQuestMenu(Player viewer, MessageService messages, QuestType initial) {
        super(viewer, messages, 54, "gui.periodic-title");
        List<QuestType> types = PlayerTaskX.getInstance().periodicService().enabledTypes();
        this.selected = initial != null ? initial : (types.isEmpty() ? QuestType.DAILY : types.get(0));
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();
        layout(SHAPE);

        List<MenuItem> items = questItems(plugin,
                plugin.periodicService().currentQuests(player.getUniqueId(), selected));
        if (items.isEmpty()) {
            // 任务池为空或这种周期被关掉：给一句明确说明，而不是丢一个空界面给玩家猜
            set(slots(TASKS).get(0), MenuItem.display(Material.BARRIER, text("periodic.none"), List.of()));
        } else {
            // 动态槽位：有几个任务就占几格，多出来的任务没有位置（不显示）
            fill(TASKS, items);
        }
        fillButtons(plugin, player);
        fill(MenuItem.filler());
    }

    /** 玩家自己的任务 → 菜单项；任务定义已被删除的脏数据跳过（不占格）。 */
    private List<MenuItem> questItems(PlayerTaskX plugin, List<PlayerQuest> quests) {
        List<MenuItem> items = new ArrayList<>(quests.size());
        for (PlayerQuest playerQuest : quests) {
            Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
            if (quest != null) {
                items.add(questItem(plugin, quest, playerQuest));
            }
        }
        return items;
    }

    /** 周期标签与刷新 / 关闭：布局里没写的位置就不摆（周期可能被关掉、刷新次数可能用完）。 */
    private void fillButtons(PlayerTaskX plugin, Player player) {
        for (QuestType type : QuestType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            if (declared(name) && plugin.periodicService().enabledTypes().contains(type)) {
                set(name, typeItem(type));
            }
        }
        if (declared("refresh")
                && plugin.periodicService().remainingRefreshes(player.getUniqueId(), selected) > 0) {
            set("refresh", refreshButton(plugin, player));
        }
        // 关闭按钮是必须的：布局里漏了它就该当场炸，而不是让玩家找不到出口
        set("close", MenuItem.of(Material.BARRIER, text("gui.close"), List.of(),
                context -> player.closeInventory()));
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
    }

    // ---------- 底部按钮 ----------

    /** 周期切换按钮：点一下换成看那种周期的任务；当前正在看的那一种用亮色。 */
    private MenuItem typeItem(QuestType type) {
        boolean active = type == selected;
        List<String> lore = List.of(text(active ? "gui.periodic-current" : "gui.periodic-switch"));
        return MenuItem.of(active ? Material.LIME_DYE : Material.GRAY_DYE,
                text("gui.periodic-button", Periods.label(type)), lore,
                context -> {
                    selected = type;
                    refresh();
                });
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
    }
}
