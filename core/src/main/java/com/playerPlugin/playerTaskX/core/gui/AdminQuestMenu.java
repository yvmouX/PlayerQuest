package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 管理员任务管理界面：分页列表 + 启用/禁用 + 重载 + 只读预览。
 *
 * <h2>布局</h2>
 * <pre>
 *   第 1~5 行（0~44）：任务列表，每页 45 个
 *   第 6 行          ：上一页（45）、重载（47）、页码（49）、下一页（53）
 * </pre>
 *
 * <h2>操作</h2>
 * <ul>
 *   <li>左键任务：切换启用状态（落库 + 同步内存注册表 + 立刻刷新界面）；</li>
 *   <li>右键任务：打开只读预览（目标与奖励的定义）；</li>
 *   <li>重载：重新从存储载入全部任务定义。</li>
 * </ul>
 *
 * <h2>为什么管理界面里的标签是中文直写</h2>
 * 语言文件里没有「类型」「目标」「启用」这类管理字段的键（本次不新增语言键），
 * 而这些是<b>给管理员看的排错信息</b>，不是玩家文案；管理员命令 {@code /ptxa list}
 * 用的是同一套措辞与同一套校验判断，两个入口口径保持一致比强行走语言键更重要。
 * 玩家侧界面（每日任务、任务详情）没有任何硬编码文案，全部走语言键。
 */
public final class AdminQuestMenu extends Menu {

    static final int SIZE = 54;

    /** 每页 45 个：前 5 行放任务，最后一行留给分页与重载。 */
    static final int PAGE_SIZE = 45;

    private static final int PREVIOUS_SLOT = 45;
    private static final int RELOAD_SLOT = 47;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    /** 请求的页码（0 基）；实际展示时会被夹到合法范围。 */
    private final int page;

    /** 打开任务管理界面（第一页）。 */
    public AdminQuestMenu(Player viewer, MessageService messages) {
        this(viewer, messages, 0);
    }

    /**
     * @param viewer   打开界面的玩家（需要管理员权限——权限校验在命令层完成）
     * @param messages 语言服务
     * @param page     页码，从 0 开始
     */
    public AdminQuestMenu(Player viewer, MessageService messages, int page) {
        super(viewer, messages, SIZE, "gui.admin-title");
        this.page = Math.max(0, page);
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();

        List<Quest> quests = sorted(plugin);
        int totalPages = Math.max(1, (quests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        // 页码越界（例如重载后任务变少）时夹到最后一页，而不是展示一页空白
        int current = Math.min(page, totalPages - 1);

        int from = current * PAGE_SIZE;
        for (int offset = 0; offset < PAGE_SIZE; offset++) {
            int index = from + offset;
            if (index >= quests.size()) {
                break;
            }
            set(offset, questItem(plugin, player, quests.get(index), current));
        }

        buildPager(plugin, player, current, totalPages, quests.size());
        fill(MenuItem.filler());
    }

    /**
     * 任务列表按 id 排序。
     * <p>
     * 注册表顺序来自数据库读取顺序，本来就不保证稳定。翻页界面必须保证
     * 「同一份数据每次得到同样的顺序」，否则切换启用状态刷新后任务会跳到别的页。
     */
    private static List<Quest> sorted(PlayerTaskX plugin) {
        List<Quest> quests = new ArrayList<>(plugin.quests().all());
        quests.sort(Comparator.comparing(quest -> String.valueOf(quest.id())));
        return quests;
    }

    // ---------- 任务物品 ----------

    /**
     * 任务物品。
     * <p>
     * 管理员的每一项都是诊断信息：id、类型、分类、目标/奖励构成、启用状态、校验问题。
     * id 原样显示是刻意的——管理员要靠它定位到具体配置项，内部标识在管理界面里不是秘密。
     */
    private MenuItem questItem(PlayerTaskX plugin, Player player, Quest quest, int page) {
        List<String> lore = new ArrayList<>(diagnostics(plugin, player, quest));
        lore.add("&7左键: &f切换启用状态");
        lore.add("&7右键: &f预览目标与奖励");

        return MenuItem.of(MenuItem.material(quest.icon()), quest.name(), lore, context -> {
            if (context.clickType().isRightClick()) {
                new PreviewMenu(player, messages(), quest, page).open();
                return;
            }
            toggle(plugin, quest);
        });
    }

    /** 诊断信息行：任务构成与校验问题。 */
    private List<String> diagnostics(PlayerTaskX plugin, Player player, Quest quest) {
        List<String> lore = new ArrayList<>();
        lore.add("&8" + quest.id());
        lore.add("&7类型: &f" + quest.type());
        String category = quest.category();
        lore.add("&7分类: &f" + (category == null || category.isBlank()
                ? textOr("common.none", "-") : category));
        lore.add("&7目标: &f" + describeObjectives(plugin, player, quest));
        lore.add("&7奖励: &f" + describeRewards(plugin, player, quest));
        lore.add("&7启用: &f" + text(quest.enabled() ? "common.yes" : "common.no"));
        for (String problem : problems(plugin, quest)) {
            lore.add("&c! " + problem);
        }
        return lore;
    }

    /** 目标构成：逐条「类型显示名 × 数量」；混合类型也如实展开，不只看第一个。 */
    private String describeObjectives(PlayerTaskX plugin, Player player, Quest quest) {
        if (quest.objectives().isEmpty()) {
            return textOr("common.none", "-");
        }
        StringBuilder builder = new StringBuilder();
        for (QuestObjective objective : quest.objectives()) {
            if (builder.length() > 0) {
                builder.append("&7, &f");
            }
            builder.append(localized(messages(), player, "objective." + objective.type(),
                    plugin.objectiveTypes().displayName(objective.type())));
            builder.append("&7 ×&f").append(objective.amount());
        }
        return builder.toString();
    }

    /** 奖励构成：逐条「类型显示名 × 数量」；没有 amount 字段的类型（如命令）不显示数量。 */
    private String describeRewards(PlayerTaskX plugin, Player player, Quest quest) {
        if (quest.rewards().isEmpty()) {
            return textOr("common.none", "-");
        }
        StringBuilder builder = new StringBuilder();
        for (QuestReward reward : quest.rewards()) {
            if (builder.length() > 0) {
                builder.append("&7, &f");
            }
            builder.append(localized(messages(), player, "reward." + reward.type(),
                    plugin.rewardTypes().displayName(reward.type())));
            if (reward.properties().containsKey("amount")) {
                builder.append("&7 ×&f").append(reward.integer("amount", 1));
            }
        }
        return builder.toString();
    }

    /**
     * 校验问题：与 {@code /ptxa list} 的判断完全一致。
     * <p>
     * 奖励除了「类型是否存在」还要看「是否可用」：Vault 没装时金币奖励配置完全合法，
     * 但玩家一分钱也拿不到——这种情况必须在这里暴露出来，否则只能靠翻日志发现。
     */
    private static List<String> problems(PlayerTaskX plugin, Quest quest) {
        List<String> problems = new ArrayList<>();
        if (quest.objectives().isEmpty()) {
            problems.add("任务没有配置任何目标");
        }
        for (QuestObjective objective : quest.objectives()) {
            if (!plugin.objectiveTypes().contains(objective.type())) {
                problems.add("未知目标类型 " + objective.type());
            }
        }
        for (QuestReward reward : quest.rewards()) {
            RewardType type = plugin.rewardTypes().find(reward.type()).orElse(null);
            if (type == null) {
                problems.add("未知奖励类型 " + reward.type());
            } else if (!type.available()) {
                problems.add("奖励类型 " + reward.type() + " 不可用（" + type.unavailableReason() + "）");
            }
        }
        return problems;
    }

    // ---------- 启用 / 禁用 ----------

    /**
     * 切换启用状态并持久化。
     * <p>
     * {@link Quest} 是 record，没有 setter，所以按字段整份复制出一个新对象再保存。
     * 落库成功后<b>必须</b>同步内存注册表（{@code upsert}）：注册表才是引擎与界面的数据源，
     * 只落库不更新内存的话，{@code refresh()} 读到的还是旧定义，按钮看起来「点了没反应」。
     * <p>
     * 这里用单条 {@code upsert} 而不是 {@code reloadQuests()}：后者要把整张表重新读一遍，
     * 而管理界面里连点几次开关就会触发多次全量读库，代价与收益不成比例；
     * 需要全量重载时底部另有重载按钮。
     * <p>
     * 注意：注册表里的任务即使被禁用也仍然存在（{@code find} 照样命中），
     * 因此已经在线并持有该任务的玩家不会立刻停止累计进度——这与命令行的
     * {@code enable/disable} 行为一致，属于既有设计，不在界面层单独处理。
     */
    private void toggle(PlayerTaskX plugin, Quest quest) {
        Quest updated = new Quest(quest.id(), quest.name(), quest.description(), quest.icon(), quest.category(),
                quest.type(), quest.objectives(), quest.rewards(), quest.refreshCost(), !quest.enabled());
        try {
            plugin.questRepository().save(updated);
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("保存任务启用状态失败: " + quest.id(), e);
            messages().send(viewer(), "error.internal");
            return;
        }
        plugin.quests().upsert(updated);
        refresh();
    }

    // ---------- 分页与重载 ----------

    private void buildPager(PlayerTaskX plugin, Player player, int current, int totalPages, int total) {
        if (current > 0) {
            set(PREVIOUS_SLOT, MenuItem.of(Material.ARROW, text("gui.previous"), List.of(),
                    context -> new AdminQuestMenu(player, messages(), current - 1).open()));
        } else {
            // 首页把「上一页」摆成灰色不可点，而不是干脆不显示：位置固定，翻页时按钮不会跳来跳去
            set(PREVIOUS_SLOT, MenuItem.display(Material.GRAY_DYE, text("gui.previous"), List.of()));
        }

        if (current < totalPages - 1) {
            set(NEXT_SLOT, MenuItem.of(Material.ARROW, text("gui.next"), List.of(),
                    context -> new AdminQuestMenu(player, messages(), current + 1).open()));
        } else {
            set(NEXT_SLOT, MenuItem.display(Material.GRAY_DYE, text("gui.next"), List.of()));
        }

        set(INFO_SLOT, MenuItem.display(Material.PAPER, text("gui.page-info", current + 1, totalPages),
                List.of("&7任务总数: &f" + total)));
        set(RELOAD_SLOT, MenuItem.of(Material.REDSTONE, reloadLabel(total), List.of(),
                context -> reload(plugin)));
    }

    /**
     * 重载按钮文案。
     * <p>
     * 优先 {@code gui.reload}（语言文件里暂时没有这个键，管理员补上即自动生效）；
     * 否则沿用 {@code command.reloaded} 这句「带任务数的重载结果」，把当前任务数填进去——
     * 它描述的动作与按钮完全一致，比在界面里另写一句同义文案更可控。
     */
    private String reloadLabel(int total) {
        if (hasText("gui.reload")) {
            return text("gui.reload");
        }
        return hasText("command.reload") ? text("command.reload", total) : text("command.reloaded", total);
    }

    /**
     * 重新从存储载入任务定义。
     * <p>
     * {@code reloadQuests()} 会读库并重建注册表，数据库异常必须显式兜住：
     * 让它冒到事件层的话，管理员只会看到「点了没反应」。
     */
    private void reload(PlayerTaskX plugin) {
        try {
            plugin.reloadQuests();
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            PlayerTaskX.log().error("重载任务失败: " + reason, e);
            messages().send(viewer(), "command.reload-failed", reason);
            return;
        }
        // 语言键是 command.reloaded（带任务数量），不是 command.reload —— 与 AdminCommand 保持一致
        messages().send(viewer(), hasText("command.reload") ? "command.reload" : "command.reloaded",
                plugin.quests().all().size());
        // 重载后任务集合与总页数都可能变，界面必须重建（build 内部会把页码夹回合法范围）
        refresh();
    }

    /**
     * 只读预览：目标与奖励的<b>定义</b>，复用 {@link QuestDetailMenu} 的布局常量与物品构造方法。
     * <p>
     * 做成 AdminQuestMenu 的内部类而不是直接跳 QuestDetailMenu，是因为「返回」的落点不同：
     * 管理员要回到原来那一页管理列表，而 QuestDetailMenu 的返回固定回玩家界面。
     * 进度参数传 {@code null}：管理员看的是配置，不是某个玩家的完成情况。
     */
    static final class PreviewMenu extends Menu {

        private final Quest quest;
        private final int page;

        PreviewMenu(Player viewer, MessageService messages, Quest quest, int page) {
            super(viewer, messages, QuestDetailMenu.SIZE, "gui.quest-detail-title",
                    quest == null ? "" : quest.name());
            this.quest = quest;
            this.page = page;
            refresh();
        }

        @Override
        protected void build() {
            PlayerTaskX plugin = PlayerTaskX.getInstance();
            Player player = viewer();

            set(QuestDetailMenu.HEADER_SLOT, QuestDetailMenu.headerItem(quest));

            int slot = QuestDetailMenu.OBJECTIVE_START;
            List<QuestObjective> objectives = quest.objectives();
            for (int index = 0; index < objectives.size() && index < QuestDetailMenu.OBJECTIVE_LIMIT; index++) {
                set(slot++, QuestDetailMenu.objectiveItem(messages(), player, plugin.objectiveTypes(),
                        objectives.get(index), index, null));
            }

            slot = QuestDetailMenu.REWARD_START;
            List<QuestReward> rewards = quest.rewards();
            for (int index = 0; index < rewards.size() && index < QuestDetailMenu.REWARD_LIMIT; index++) {
                set(slot++, QuestDetailMenu.rewardItem(messages(), player, plugin.rewardTypes(), rewards.get(index)));
            }

            set(QuestDetailMenu.BACK_SLOT, MenuItem.of(Material.ARROW, text("gui.back"), List.of(),
                    context -> new AdminQuestMenu(player, messages(), page).open()));
            set(QuestDetailMenu.CLOSE_SLOT, MenuItem.of(Material.BARRIER, text("gui.close"), List.of(),
                    context -> player.closeInventory()));
            fill(MenuItem.filler());
        }
    }
}
