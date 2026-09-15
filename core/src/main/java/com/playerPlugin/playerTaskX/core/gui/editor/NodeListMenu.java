package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.gui.MenuItem;
import cn.yvmou.ylib.gui.PagedMenu;
import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
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
 * 翻页与页码由 {@link PagedMenu} 管，这里只管节点卡片与「返回 / 新增」两个额外按钮。
 */
public final class NodeListMenu extends PagedMenu<QuestDraft.Node> {

    /** 界面布局：前 5 行是节点区（每页 45 个），最后一行是按钮。 */
    private static final String[] SHAPE = {
            "#########",
            "#########",
            "#########",
            "#########",
            "#########",
            "`prev` `back` `add` `pages` `next`",
    };

    private final QuestDraft draft;
    private final boolean reward;
    private final Runnable onBack;

    public NodeListMenu(Player viewer, MessageService messages, QuestDraft draft, boolean reward, Runnable onBack) {
        this(viewer, messages, draft, reward, 0, onBack);
    }

    /** @param page 页码（0 基）；增删后由调用方传回原来那一页 */
    public NodeListMenu(Player viewer, MessageService messages, QuestDraft draft, boolean reward,
                        int page, Runnable onBack) {
        super(viewer, messages, SHAPE.length * 9, reward ? "gui.editor-rewards" : "gui.editor-objectives");
        this.draft = draft;
        this.reward = reward;
        this.onBack = onBack;
        page(page);
    }

    @Override
    protected String[] shape() {
        return SHAPE;
    }

    @Override
    protected List<QuestDraft.Node> items() {
        return draft.nodes(reward);
    }

    @Override
    protected MenuItem whenEmpty() {
        return MenuItem.display(Material.PAPER, "&7还没有" + label(),
                List.of("&7点下面的 &f新增" + label() + " &7开始配"));
    }

    /** 返回 / 新增：翻页那三个按钮由基类摆。 */
    @Override
    protected void decorate() {
        set("add", MenuItem.of(Material.LIME_DYE, "&a新增" + label(),
                List.of("&7从一个类型开始配"), context -> add()));
        set("back", MenuItem.of(Material.ARROW, text("gui.back"), List.of(), context -> onBack.run()));
    }

    /** 节点卡片；{@code index} 是它在整个列表里的位置（上下移动与删除都按它算）。 */
    @Override
    protected MenuItem render(QuestDraft.Node node, int index) {
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
        lore.add("&7第 &f" + (index + 1) + "&7/&f" + items().size() + " &7个");
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

    /** 原样重开（带上当前页码：基类记着它）。 */
    private void reopen() {
        new NodeListMenu(viewer(), messages(), draft, reward, page(), onBack).open();
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
