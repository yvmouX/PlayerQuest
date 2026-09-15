package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.gui.MenuItem;
import cn.yvmou.ylib.gui.PagedMenu;
import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.gui.menu.QuestDetailMenu;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 任务编辑器入口：分页列出全部任务，可进编辑器、开关、删除、新建、重载。
 * 文件来源（quests/ 下的 YAML）的任务只读：左键进只读预览而不是编辑器，写入仍会由仓储抛错兜住（这里只是不让管理员白点）。
 * 翻页与页码由 {@link PagedMenu} 管，这里只管任务卡片与「排序 / 新建 / 重载」三个额外按钮。
 */
public final class QuestBrowserMenu extends PagedMenu<Quest> {

    /** 界面布局：前 5 行是任务区（每页 45 个），最后一行是按钮（6 个按钮 + 3 个空格刚好 9 格）。 */
    private static final String[] SHAPE = {
            "#########",
            "#########",
            "#########",
            "#########",
            "#########",
            "`prev` `sort``new` `reload``pages` `next`",
    };

    /**
     * 列表的排序方式：点一下换下一种。
     * 都按现有字段排（任务没有创建时间这类元数据）；比较结果相同的任务由注册表稳定的 id 序兜底，翻页不会抖。
     */
    private enum Order {
        ID("按 id", Comparator.comparing((Quest quest) -> String.valueOf(quest.id()))),
        NAME("按名称", Comparator.comparing((Quest quest) -> String.valueOf(quest.name()))),
        TYPE("按类型", Comparator.comparing((Quest quest) -> String.valueOf(quest.type()))),
        ENABLED("按启用", Comparator.comparing((Quest quest) -> quest.enabled() ? 0 : 1));

        private final String label;
        private final Comparator<Quest> comparator;

        Order(String label, Comparator<Quest> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        /** 下一种排序方式（列表里循环）。 */
        Order next() {
            Order[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private final Order order;
    /** 已被要求删除、等着再确认一次的任务 id；换页或刷新后作废，免得隔了很久误删。 */
    private String pendingDelete;

    public QuestBrowserMenu(Player viewer, MessageService messages) {
        this(viewer, messages, 0, Order.ID);
    }

    /** @param order 从列表进来时带上的排序方式，翻页 / 返回都要原样带回去 */
    public QuestBrowserMenu(Player viewer, MessageService messages, int page, Order order) {
        super(viewer, messages, 54, "gui.editor-title");
        this.order = order == null ? Order.ID : order;
        page(page);
    }

    @Override
    protected String[] shape() {
        return SHAPE;
    }

    /** 全部任务按当前排序方式给出（顺序由注册表担保，见 {@code QuestRegistry#all}）。 */
    @Override
    protected List<Quest> items() {
        return PlayerTaskX.getInstance().quests().all(order.comparator);
    }

    @Override
    protected MenuItem whenEmpty() {
        return MenuItem.display(Material.PAPER, "&7还没有任何任务",
                List.of("&7点下面的 &f新建任务 &7开始"));
    }

    /** 页码多带两行信息：任务总数与当前排序（基类只管「第几页 / 共几页」）。 */
    @Override
    protected MenuItem pageInfo(int current, int totalPages) {
        return MenuItem.display(Material.PAPER, text("gui.page-info", current + 1, totalPages),
                List.of("&7任务总数: &f" + items().size(), "&7当前排序: &f" + order.label));
    }

    /** 排序 / 新建 / 重载：翻页那三个按钮由基类摆。 */
    @Override
    protected void decorate() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        // 排序方式带在界面上循环：翻页 / 进出编辑器都要保持，不然看一眼别的任务回来顺序就变了
        set("sort", MenuItem.of(Material.HOPPER, "&e排序: &f" + order.label,
                List.of("&7按现有字段排（任务没有创建时间这类元数据）",
                        "&7左键: &f换成「" + order.next().label + "」"),
                context -> reopen(page(), order.next())));
        set("new", MenuItem.of(Material.LIME_DYE, "&a新建任务",
                List.of("&7新建的任务默认启用、类型为 NORMAL", "&7id 与至少一个目标是必填"),
                context -> new QuestEditMenu(viewer(), messages(), QuestDraft.creating(), true,
                        () -> reopen(page(), order)).open()));
        set("reload", MenuItem.of(Material.REDSTONE, text("command.reloaded", items().size()),
                List.of("&7从存储重新载入任务定义"), context -> reload(plugin)));
    }

    /**
     * 任务物品：管理员的每一项都是诊断信息（id、类型、分类、目标/奖励构成、启用、校验问题）。
     * id 原样显示是刻意的——管理员要靠它定位到具体配置项，内部标识在管理界面里不是秘密。
     */
    @Override
    protected MenuItem render(Quest quest, int index) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();
        boolean readOnly = plugin.questDefinitions().isReadOnly(quest.id());
        List<String> lore = new ArrayList<>();
        lore.add("&8" + quest.id());
        lore.add("&7类型: &f" + quest.type());
        String category = quest.category();
        lore.add("&7分类: &f" + (category == null || category.trim().isEmpty() ? text("common.none") : category));
        lore.add("&7目标: &f" + describeObjectives(player, quest.objectives()));
        lore.add("&7奖励: &f" + describeRewards(player, quest.rewards()));
        lore.add("&7启用: &f" + text(quest.enabled() ? "common.yes" : "common.no"));
        if (readOnly) {
            lore.add("&8来源: &7YAML 文件（只读，改文件后 /ptxa reload）");
        }
        for (String problem : plugin.questAdmin().validate(quest)) {
            lore.add("&c! " + problem);
        }

        if (readOnly) {
            lore.add("&7左键: &f预览目标与奖励（只读，不能在此编辑）");
        } else if (quest.id().equals(pendingDelete)) {
            lore.add("&c再按一次 &fShift+右键 &c就删除，不可撤销");
        } else {
            lore.add("&7左键: &f编辑任务");
            lore.add("&7右键: &f切换启用状态");
            lore.add("&7Shift+右键: &f删除任务");
        }

        return MenuItem.of(MenuItem.material(quest.icon()), quest.name(), lore,
                context -> click(plugin, quest, readOnly, context.clickType()));
    }

    private void click(PlayerTaskX plugin, Quest quest, boolean readOnly, ClickType click) {
        if (readOnly) {
            new QuestDetailMenu(viewer(), messages(), quest, null, this::reopen).open();
            return;
        }
        if (click.isShiftClick() && click.isRightClick()) {
            delete(plugin, quest);
            return;
        }
        if (click.isRightClick()) {
            toggle(plugin, quest);
            return;
        }
        new QuestEditMenu(viewer(), messages(), QuestDraft.of(quest), false, this::reopen).open();
    }

    // ---------- 写操作 ----------

    /** 删除要按两次 Shift+右键：一次误点不至于把任务删掉，也不必为此单开一个确认界面。 */
    private void delete(PlayerTaskX plugin, Quest quest) {
        if (!quest.id().equals(pendingDelete)) {
            pendingDelete = quest.id();
            refresh();
            return;
        }
        pendingDelete = null;
        try {
            plugin.questAdmin().delete(quest.id());
        } catch (DefinitionReadOnlyException e) {
            messages().sendRaw(viewer(), Texts.render("&c" + e.getMessage()));
            return;
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("删除任务失败: " + quest.id(), e);
            messages().send(viewer(), "error.internal");
            return;
        }
        messages().sendRaw(viewer(), Texts.render("&a已删除任务 &f" + quest.id()));
        refresh();
    }

    /** 切换启用状态；落库、更新内存注册表与重建玩家索引都由 {@code QuestAdminService#setEnabled} 一处担保。 */
    private void toggle(PlayerTaskX plugin, Quest quest) {
        try {
            plugin.questAdmin().setEnabled(quest.id(), !quest.enabled());
        } catch (DefinitionReadOnlyException e) {
            messages().sendRaw(viewer(), Texts.render("&c" + e.getMessage()));
            return;
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("保存任务启用状态失败: " + quest.id(), e);
            messages().send(viewer(), "error.internal");
            return;
        }
        refresh();
    }

    /** 重新从存储载入任务定义；数据库异常必须显式兜住，否则管理员只会看到「点了没反应」。 */
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
        // 重载后任务集合与总页数都可能变，界面必须重建（页码由基类夹回合法范围）
        refresh();
    }

    /** 回到这个列表的当前页（排序与页码都原样带回去）。 */
    private void reopen() {
        reopen(page(), order);
    }

    private void reopen(int page, Order order) {
        new QuestBrowserMenu(viewer(), messages(), page, order).open();
    }

    /** 目标构成：逐条「类型显示名 × 数量」；混合类型也如实展开，不只看第一个。 */
    private String describeObjectives(Player player, List<QuestObjective> objectives) {
        StringBuilder builder = new StringBuilder();
        for (QuestObjective objective : objectives) {
            appendNode(builder, EditorLookup.typeName(messages(), player, false, objective.type()),
                    objective.amount());
        }
        return builder.length() == 0 ? text("common.none") : builder.toString();
    }

    /** 奖励构成：没有 amount 字段的类型（如命令奖励）不显示数量。 */
    private String describeRewards(Player player, List<QuestReward> rewards) {
        StringBuilder builder = new StringBuilder();
        for (QuestReward reward : rewards) {
            appendNode(builder, EditorLookup.typeName(messages(), player, true, reward.type()),
                    reward.integer("amount", 0));
        }
        return builder.length() == 0 ? text("common.none") : builder.toString();
    }

    private static void appendNode(StringBuilder builder, String name, int amount) {
        if (builder.length() > 0) {
            builder.append("&7, &f");
        }
        builder.append(name);
        if (amount > 0) {
            builder.append("&7 ×&f").append(amount);
        }
    }
}
