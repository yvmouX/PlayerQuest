package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import cn.yvmou.ylib.gui.Menu;
import cn.yvmou.ylib.gui.MenuItem;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一个任务的目标（或奖励）列表：左键进字段编辑，右键删，Shift+左/右键上下移动，底部新增与返回。
 * 顺序就是它们在任务里的顺序（奖励按顺序发放与展示），因此移动是真实调整列表，不是只改显示。
 */
public final class NodeListMenu extends Menu {

    private static final int SIZE = 54;
    /** 每页 45 个：前 5 行是节点列表（列表区仍用数字下标，见 {@code Menu#layout}），最后一行是按钮。 */
    private static final int PAGE_SIZE = 45;

    /** 界面布局（见 {@code SlotLayout}）：一个字符一格，空格是空位；节点列表区是翻页的，用数字下标摆。 */
    private static final String[] SHAPE = {
            "    `note`",
            "",
            "",
            "",
            "",
            "`prev` `back` `add` `info` `next`",
    };

    private final QuestDraft draft;
    private final boolean reward;
    private final Runnable onBack;
    private final int page;

    public NodeListMenu(Player viewer, MessageService messages, QuestDraft draft, boolean reward, Runnable onBack) {
        this(viewer, messages, draft, reward, 0, onBack);
    }

    /** @param page 页码（0 基）；增删后由调用方传回原来那一页 */
    public NodeListMenu(Player viewer, MessageService messages, QuestDraft draft, boolean reward,
                        int page, Runnable onBack) {
        super(viewer, messages, SIZE, reward ? "gui.editor-rewards" : "gui.editor-objectives");
        this.draft = draft;
        this.reward = reward;
        this.page = Math.max(0, page);
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<QuestDraft.Node> nodes = draft.nodes(reward);
        int totalPages = Math.max(1, (nodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, totalPages - 1);
        int from = current * PAGE_SIZE;
        layout(SHAPE);

        for (int offset = 0; offset < PAGE_SIZE && from + offset < nodes.size(); offset++) {
            set(offset, nodeItem(nodes.get(from + offset), from + offset, nodes.size()));
        }
        if (nodes.isEmpty()) {
            set("note", MenuItem.display(Material.PAPER, "&7还没有" + label(),
                    List.of("&7点下面的 &f新增" + label() + " &7开始配")));
        }

        buildPager(nodes.size(), current, totalPages);
        set("add", MenuItem.of(Material.LIME_DYE, "&a新增" + label(),
                List.of("&7从一个类型开始配"), context -> add()));
        set("back", MenuItem.of(Material.ARROW, text("gui.back"), List.of(), context -> onBack.run()));
        fill(MenuItem.filler());
    }

    private MenuItem nodeItem(QuestDraft.Node node, int index, int total) {
        ConfigurableType type = EditorLookup.type(reward, node.type());
        List<String> lore = new ArrayList<>();
        lore.add("&8" + node.type());
        if (type == null) {
            lore.add("&c这个类型当前没有注册（插件被移除或 id 写错），这个" + label() + "不会生效");
        }
        lore.add("&7配置: &f" + (node.authored().isEmpty()
                ? text("common.none") : Texts.properties(node.authored())));
        String preset = presetId(node);
        if (preset != null) {
            lore.add("&7引用预设: &f" + preset);
        }
        lore.add("&7第 &f" + (index + 1) + "&7/&f" + total + " &7个");
        lore.add("&7左键: &f编辑字段");
        lore.add("&7右键: &f删除");
        lore.add("&7Shift+左键: &f上移　&7Shift+右键: &f下移");

        // 类型没注册时用屏障图标，别让界面看起来一切正常
        Material icon = type == null ? Material.BARRIER : EditorLookup.icon(type);
        return MenuItem.of(icon, "&f" + EditorLookup.typeName(messages(), viewer(), reward, node.type()),
                lore, context -> click(context.clickType(), index));
    }

    private void click(ClickType click, int index) {
        if (click.isShiftClick() && click.isLeftClick()) {
            draft.swapNodes(reward, index, index - 1);
            refresh();
        } else if (click.isShiftClick()) {
            draft.swapNodes(reward, index, index + 1);
            refresh();
        } else if (click.isRightClick()) {
            draft.removeNode(reward, index);
            refresh();
        } else {
            openFields(index);
        }
    }

    private void buildPager(int total, int current, int totalPages) {
        if (current > 0) {
            set("prev", MenuItem.of(Material.ARROW, text("gui.previous"), List.of(),
                    context -> reopen(current - 1)));
        } else {
            // 首页与末页把按钮摆成灰色不可点而不是不显示：位置固定，翻页时按钮不会跳来跳去
            set("prev", MenuItem.display(Material.GRAY_DYE, text("gui.previous"), List.of()));
        }
        if (current < totalPages - 1) {
            set("next", MenuItem.of(Material.ARROW, text("gui.next"), List.of(),
                    context -> reopen(current + 1)));
        } else {
            set("next", MenuItem.display(Material.GRAY_DYE, text("gui.next"), List.of()));
        }
        set("info", MenuItem.display(Material.PAPER, text("gui.page-info", current + 1, totalPages),
                List.of("&7共 &f" + total + " &7个" + label())));
    }

    // ---------- 动作 ----------

    /** 新增：先选类型，建好空节点后直接进字段编辑（不然新增完还得再点一次）。 */
    private void add() {
        new TypePickMenu(viewer(), messages(), reward, typeId -> {
            draft.addNode(reward, new QuestDraft.Node(typeId, Map.of()));
            openFields(draft.nodes(reward).size() - 1);
        }, this::reopen).open();
    }

    private void openFields(int index) {
        new NodeEditMenu(viewer(), messages(), draft, reward, index, this::reopen).open();
    }

    private void reopen() {
        reopen(page);
    }

    private void reopen(int page) {
        new NodeListMenu(viewer(), messages(), draft, reward, page, onBack).open();
    }

    // ---------- 类型信息 ----------

    private static String presetId(QuestDraft.Node node) {
        Object value = node.authored().get("preset");
        return value == null ? null : String.valueOf(value);
    }

    private String label() {
        return reward ? "奖励" : "目标";
    }
}
