package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.text.Texts;
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
 *   <li>右键任务：打开只读预览（复用 {@link QuestDetailMenu}，进度传 {@code null}）；</li>
 *   <li>重载：重新从存储载入全部任务定义。</li>
 * </ul>
 *
 * <h2>为什么管理界面里的标签是中文直写</h2>
 * 「类型」「目标」「启用」这些是<b>给管理员看的排错信息</b>，不是玩家文案；
 * {@code /ptxa list} 与 {@code /ptxa info} 用的是同一套措辞与同一套校验判断，
 * 两个入口口径一致比强行走语言键更重要。玩家侧界面没有任何硬编码文案。
 */
public final class AdminQuestMenu extends Menu {

    private static final int SIZE = 54;

    /** 每页 45 个：前 5 行放任务，最后一行留给分页与重载。 */
    private static final int PAGE_SIZE = 45;

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

        // 注册表顺序来自存储读取顺序，本来就不保证稳定。翻页界面必须保证
        // 「同一份数据每次得到同样的顺序」，否则切换启用状态刷新后任务会跳到别的页。
        List<Quest> quests = new ArrayList<>(plugin.quests().all());
        quests.sort(Comparator.comparing(quest -> String.valueOf(quest.id())));

        int totalPages = Math.max(1, (quests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        // 页码越界（例如重载后任务变少）时夹到最后一页，而不是展示一页空白
        int current = Math.min(page, totalPages - 1);

        int from = current * PAGE_SIZE;
        for (int offset = 0; offset < PAGE_SIZE && from + offset < quests.size(); offset++) {
            set(offset, questItem(plugin, player, quests.get(from + offset), current));
        }

        buildPager(plugin, player, current, totalPages, quests.size());
        fill(MenuItem.filler());
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
                new QuestDetailMenu(player, messages(), quest, null,
                        () -> new AdminQuestMenu(player, messages(), page).open()).open();
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
        lore.add("&7分类: &f" + (category == null || category.isBlank() ? text("common.none") : category));
        lore.add("&7目标: &f" + describeObjectives(plugin, player, quest));
        lore.add("&7奖励: &f" + describeRewards(plugin, player, quest));
        // 前置原样列 id：管理员要靠它定位到具体任务，与「目标」「奖励」两行同一套诊断口径
        if (quest.hasPrerequisites()) {
            lore.add("&7前置: &f" + String.join("&7, &f", quest.prerequisites()));
        }
        lore.add("&7启用: &f" + text(quest.enabled() ? "common.yes" : "common.no"));
        // 只读定义（quests/ 下的 YAML）在管理界面里要一眼看出，否则管理员会反复点开关以为坏了
        if (plugin.questDefinitions().isReadOnly(quest.id())) {
            lore.add("&8来源: &7YAML 文件（只读，改文件后 /ptxa reload）");
        }
        for (String problem : plugin.questAdmin().validate(quest)) {
            lore.add("&c! " + problem);
        }
        return lore;
    }

    /** 目标构成：逐条「类型显示名 × 数量」；混合类型也如实展开，不只看第一个。 */
    private String describeObjectives(PlayerTaskX plugin, Player player, Quest quest) {
        if (quest.objectives().isEmpty()) {
            return text("common.none");
        }
        StringBuilder builder = new StringBuilder();
        for (QuestObjective objective : quest.objectives()) {
            if (builder.length() > 0) {
                builder.append("&7, &f");
            }
            builder.append(Texts.typeName(messages(), player, "objective", objective.type(),
                    plugin.objectiveTypes().displayName(objective.type())));
            builder.append("&7 ×&f").append(objective.amount());
        }
        return builder.toString();
    }

    /** 奖励构成：逐条「类型显示名 × 数量」；没有 amount 字段的类型（如命令）不显示数量。 */
    private String describeRewards(PlayerTaskX plugin, Player player, Quest quest) {
        if (quest.rewards().isEmpty()) {
            return text("common.none");
        }
        StringBuilder builder = new StringBuilder();
        for (QuestReward reward : quest.rewards()) {
            if (builder.length() > 0) {
                builder.append("&7, &f");
            }
            builder.append(Texts.typeName(messages(), player, "reward", reward.type(),
                    plugin.rewardTypes().displayName(reward.type())));
            if (reward.properties().containsKey("amount")) {
                builder.append("&7 ×&f").append(reward.integer("amount", 1));
            }
        }
        return builder.toString();
    }

    // ---------- 启用 / 禁用 ----------

    /**
     * 切换启用状态。
     * <p>
     * 「落库 + 同步内存注册表 + 重建在线玩家索引」三者成对发生，由
     * {@code QuestAdminService#setEnabled} 一处担保——注册表才是引擎与界面的数据源，
     * 只落库不更新内存的话 {@code refresh()} 读到的还是旧定义，按钮看起来「点了没反应」。
     */
    private void toggle(PlayerTaskX plugin, Quest quest) {
        try {
            plugin.questAdmin().setEnabled(quest.id(), !quest.enabled());
        } catch (DefinitionReadOnlyException e) {
            // 该任务定义在 quests/ 的 YAML 文件里：把原因原样告诉管理员，而不是「内部错误」
            messages().sendRaw(viewer(), Texts.render("&c" + e.getMessage()));
            return;
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("保存任务启用状态失败: " + quest.id(), e);
            messages().send(viewer(), "error.internal");
            return;
        }
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
        set(RELOAD_SLOT, MenuItem.of(Material.REDSTONE, text("command.reloaded", total), List.of(),
                context -> reload(plugin)));
    }

    /**
     * 重新从存储载入任务定义。
     * <p>
     * 数据库异常必须显式兜住：让它冒到事件层的话，管理员只会看到「点了没反应」。
     */
    private void reload(PlayerTaskX plugin) {
        try {
            plugin.questAdmin().reload();
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            PlayerTaskX.log().error("重载任务失败: " + reason, e);
            messages().send(viewer(), "command.reload-failed", reason);
            return;
        }
        messages().send(viewer(), "command.reloaded", plugin.quests().all().size());
        // 重载后任务集合与总页数都可能变，界面必须重建（build 内部会把页码夹回合法范围）
        refresh();
    }
}
