package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.core.gui.Menu;
import com.playerPlugin.playerTaskX.core.gui.MenuItem;
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
 */
public final class QuestBrowserMenu extends Menu {

    private static final int SIZE = 54;
    /** 每页 45 个：前 5 行放任务（列表区用数字下标，见 {@code Menu#layout}），最后一行留分页与新建。 */
    private static final int PAGE_SIZE = 45;

    /** 界面布局（见 {@code SlotLayout}）：底部一排按钮的位置一眼可见。 */
    private static final String[] SHAPE = {
            ".    .    .    .    note .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            "prev .    new  .    reload .  info .    next",
    };

    private final int page;
    /** 已被要求删除、等着再确认一次的任务 id；换页或刷新后作废，免得隔了很久误删。 */
    private String pendingDelete;

    public QuestBrowserMenu(Player viewer, MessageService messages) {
        this(viewer, messages, 0);
    }

    public QuestBrowserMenu(Player viewer, MessageService messages, int page) {
        super(viewer, messages, SIZE, "gui.editor-title");
        this.page = Math.max(0, page);
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        // 注册表顺序来自存储读取顺序，本来就不保证稳定；翻页界面必须保证同一份数据每次得到同样的顺序，
        // 否则切换启用状态刷新后任务会跳到别的页
        List<Quest> quests = new ArrayList<>(plugin.quests().all());
        quests.sort(Comparator.comparing(quest -> String.valueOf(quest.id())));

        int totalPages = Math.max(1, (quests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, totalPages - 1);
        int from = current * PAGE_SIZE;
        layout(SHAPE);
        for (int offset = 0; offset < PAGE_SIZE && from + offset < quests.size(); offset++) {
            set(offset, questItem(plugin, quests.get(from + offset), current));
        }
        if (quests.isEmpty()) {
            set("note", MenuItem.display(Material.PAPER, "&7还没有任何任务",
                    List.of("&7点下面的 &f新建任务 &7开始")));
        }

        buildPager(plugin, current, totalPages, quests.size());
        fill(MenuItem.filler());
    }

    /**
     * 任务物品：管理员的每一项都是诊断信息（id、类型、分类、目标/奖励构成、启用、校验问题）。
     * id 原样显示是刻意的——管理员要靠它定位到具体配置项，内部标识在管理界面里不是秘密。
     */
    private MenuItem questItem(PlayerTaskX plugin, Quest quest, int page) {
        Player player = viewer();
        boolean readOnly = plugin.questDefinitions().isReadOnly(quest.id());
        List<String> lore = new ArrayList<>();
        lore.add("&8" + quest.id());
        lore.add("&7类型: &f" + quest.type());
        String category = quest.category();
        lore.add("&7分类: &f" + (category == null || category.isBlank() ? text("common.none") : category));
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
                context -> click(plugin, quest, page, readOnly, context.clickType()));
    }

    private void click(PlayerTaskX plugin, Quest quest, int page, boolean readOnly, ClickType click) {
        if (readOnly) {
            new QuestDetailMenu(viewer(), messages(), quest, null,
                    () -> new QuestBrowserMenu(viewer(), messages(), page).open()).open();
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
        new QuestEditMenu(viewer(), messages(), QuestDraft.of(quest), false,
                () -> new QuestBrowserMenu(viewer(), messages(), page).open()).open();
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
        refresh();
    }

    // ---------- 分页 ----------

    private void buildPager(PlayerTaskX plugin, int current, int totalPages, int total) {
        if (current > 0) {
            set("prev", MenuItem.of(Material.ARROW, text("gui.previous"), List.of(),
                    context -> new QuestBrowserMenu(viewer(), messages(), current - 1).open()));
        } else {
            // 首页把「上一页」摆成灰色不可点而不是干脆不显示：位置固定，翻页时按钮不会跳来跳去
            set("prev", MenuItem.display(Material.GRAY_DYE, text("gui.previous"), List.of()));
        }
        if (current < totalPages - 1) {
            set("next", MenuItem.of(Material.ARROW, text("gui.next"), List.of(),
                    context -> new QuestBrowserMenu(viewer(), messages(), current + 1).open()));
        } else {
            set("next", MenuItem.display(Material.GRAY_DYE, text("gui.next"), List.of()));
        }
        set("info", MenuItem.display(Material.PAPER, text("gui.page-info", current + 1, totalPages),
                List.of("&7任务总数: &f" + total)));
        set("new", MenuItem.of(Material.LIME_DYE, "&a新建任务",
                List.of("&7新建的任务默认启用、类型为 NORMAL", "&7id 与至少一个目标是必填"),
                context -> new QuestEditMenu(viewer(), messages(), QuestDraft.creating(), true,
                        () -> new QuestBrowserMenu(viewer(), messages(), current).open()).open()));
        set("reload", MenuItem.of(Material.REDSTONE, text("command.reloaded", total),
                List.of("&7从存储重新载入任务定义"), context -> reload(plugin)));
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
