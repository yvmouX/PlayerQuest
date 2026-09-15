package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.gui.Menu;
import com.playerPlugin.playerTaskX.core.gui.MenuItem;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 任务编辑面板：基本信息一行一个字段，下面是目标/奖励入口与保存。
 * 校验问题实时显示在界面上（保存后才会生效的定义，问题不该等到 /ptxa reload 才被发现）；只有「存下去也用不了」的两种情况挡保存：id 空、没有目标。
 */
public final class QuestEditMenu extends Menu {

    private static final int SIZE = 54;

    /** 界面布局（见 {@code SlotLayout}）：文本图里那一格就是它的位置，改布局不用再心算数字。 */
    private static final String[] SHAPE = {
            "id    name  desc  icon  cate  type  cost  on    .",
            ".     .     .     .     verify .    .     .     .",
            "obj   .     rew   .     .     .    .     .     .",
            ".     .     .     .     .     .    .     .     .",
            ".     .     .     .     .     .    .     .     .",
            ".     .     save  .     back  .    .     .     .",
    };

    private static final ConfigField ID_FIELD = ConfigField.text("id", "任务 id",
            "唯一标识，同时是数据库主键；别和现有任务重名");
    private static final ConfigField NAME_FIELD = ConfigField.text("name", "任务名称",
            "玩家看到的任务名，支持 & 色码");
    private static final ConfigField DESCRIPTION_FIELD = ConfigField.text("description", "描述",
            "玩家在详情里看到的一行说明；可以加多行");
    private static final ConfigField ICON_FIELD = ConfigField.of("icon", "图标",
            "物品名，如 DIAMOND；解析不了会显示成纸", ValueKind.ITEM);
    private static final ConfigField CATEGORY_FIELD = ConfigField.text("category", "分类",
            "自由分组名，便于 /ptxa list 与界面筛选；留空表示不分类");
    private static final ConfigField REFRESH_COST_FIELD = ConfigField.decimal("refresh-cost", "刷新费用",
            "花多少金币可以刷新这个任务；0 表示不可刷新");

    private final QuestDraft draft;
    private final Runnable onBack;
    /** 是不是「新建」进来的：新建时 id 可改，保存时挡 id 撞车；存过之后它就变成普通编辑。 */
    private boolean creating;

    public QuestEditMenu(Player viewer, MessageService messages, QuestDraft draft, boolean creating,
                         Runnable onBack) {
        super(viewer, messages, SIZE, "gui.editor-quest");
        this.draft = draft;
        this.creating = creating;
        this.onBack = onBack;
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Quest quest = draft.toQuest();
        boolean readOnly = !creating && plugin.questDefinitions().isReadOnly(quest.id());
        layout(SHAPE);

        set("id", idItem(readOnly));
        set("name", textItem(Material.NAME_TAG, NAME_FIELD, draft.name(), draft::name));
        set("desc", descriptionItem());
        set("icon", MenuItem.of(MenuItem.material(draft.icon()), "&f" + ICON_FIELD.label(),
                fieldLore(ICON_FIELD, draft.icon()), context -> {
                    if (context.clickType().isRightClick()) {
                        draft.icon("PAPER");
                        refresh();
                    } else {
                        editField(ICON_FIELD, draft.icon(), draft::icon);
                    }
                }));
        set("cate", textItem(Material.BOOKSHELF, CATEGORY_FIELD, draft.category(), draft::category));
        set("type", typeItem());
        set("cost", textItem(Material.GOLD_INGOT, REFRESH_COST_FIELD,
                Texts.number(draft.refreshCost()),
                value -> draft.refreshCost(value.isEmpty() ? 0 : Double.parseDouble(value))));
        set("on", MenuItem.of(draft.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&f启用: " + (draft.enabled() ? "&a是" : "&c否"),
                List.of("&7禁用后玩家不会被抽到这个任务", "&7左键: &f切换"),
                context -> {
                    draft.enabled(!draft.enabled());
                    refresh();
                }));

        set("verify", problemItem(plugin, quest));
        set("obj", nodeEntry(false));
        set("rew", nodeEntry(true));
        set("save", readOnly ? readOnlyItem() : saveItem(quest));
        set("back", MenuItem.of(Material.ARROW, "&7返回列表",
                List.of("&7未保存的改动会丢掉"), context -> onBack.run()));
        fill(MenuItem.filler());
    }

    // ---------- 基本信息 ----------

    /**
     * id 是任务的身份：编辑现有任务时不给改（改 id 等于新建一条，旧记录还在，管理员多半不是这个意思）。
     * 另外 id 只读时也说明来源，因为文件里的定义写不进去。
     */
    private MenuItem idItem(boolean readOnly) {
        List<String> lore = new ArrayList<>();
        lore.add("&7当前: &f" + (draft.id().isEmpty() ? "&8未设置" : draft.id()));
        lore.add("&7" + ID_FIELD.hint());
        if (readOnly) {
            lore.add("&8来源: &7YAML 文件（只读，改文件后 /ptxa reload）");
        } else if (creating) {
            lore.add("&7左键: &f设置 id");
        } else {
            lore.add("&8id 是任务的身份，编辑时不给改；要改名请新建一个再删掉旧的");
        }
        return MenuItem.of(readOnly ? Material.BARRIER : Material.PAPER,
                "&f" + ID_FIELD.label(), lore, context -> {
                    if (creating && !readOnly) {
                        editField(ID_FIELD, draft.id(), draft::id);
                    }
                });
    }

    /** 描述可以多行：左键加一行，右键去掉最后一行，够用且不必为多行文本另造界面。 */
    private MenuItem descriptionItem() {
        List<String> lore = new ArrayList<>();
        if (draft.description().isEmpty()) {
            lore.add("&7当前: &8未设置");
        } else {
            for (String line : draft.description()) {
                lore.add("&7- &f" + line);
            }
        }
        lore.add("&7" + DESCRIPTION_FIELD.hint());
        lore.add("&7左键: &f加一行");
        if (!draft.description().isEmpty()) {
            lore.add("&7右键: &f删掉最后一行");
        }
        return MenuItem.of(Material.WRITABLE_BOOK, "&f描述", lore, context -> {
            if (context.clickType().isRightClick()) {
                if (!draft.description().isEmpty()) {
                    draft.description().remove(draft.description().size() - 1);
                    refresh();
                }
                return;
            }
            EditorInput.ask(viewer(), DESCRIPTION_FIELD.label(), DESCRIPTION_FIELD, "", raw -> {
                if (!raw.isBlank()) {
                    draft.description().add(raw);
                }
                reopen();
            }, this::reopen);
        });
    }

    /** 类型用左右键循环切换：只有 5 个值，比再开一个选择界面少两次点击。 */
    private MenuItem typeItem() {
        QuestType[] types = QuestType.values();
        List<String> lore = new ArrayList<>();
        lore.add("&7当前: &f" + draft.type());
        lore.add("&7左键: &f下一个　&7右键: &f上一个");
        lore.add("&8NORMAL 常驻可重复；其余四种是周期任务");
        for (QuestType type : types) {
            lore.add((type == draft.type() ? "&a▶ " : "&8- ") + type);
        }
        return MenuItem.of(Material.CLOCK, "&f类型", lore, context -> {
            int index = draft.type().ordinal();
            int step = context.clickType().isRightClick() ? types.length - 1 : 1;
            draft.type(types[(index + step) % types.length]);
            refresh();
        });
    }

    private MenuItem textItem(Material icon, ConfigField field, String current, Consumer<String> setter) {
        List<String> lore = fieldLore(field, current);
        lore.add("&7左键: &f修改");
        return MenuItem.of(icon, "&f" + field.label(), lore,
                context -> editField(field, current, setter));
    }

    private List<String> fieldLore(ConfigField field, String current) {
        List<String> lore = new ArrayList<>();
        lore.add("&7当前: &f" + (current == null || current.isEmpty() ? "&8未设置" : current));
        if (field.hint() != null && !field.hint().isBlank()) {
            lore.add("&7" + field.hint());
        }
        String kinds = FieldLore.kindsText(field);
        lore.add("&8" + FieldLore.shapeText(field) + (kinds.isEmpty() ? "" : "　值域: " + kinds));
        return lore;
    }

    /** 候选字段进清单挑，其余在聊天栏打字；两个入口最后都回到这里刷新。 */
    private void editField(ConfigField field, String current, Consumer<String> setter) {
        if (field.shape() != ConfigField.Shape.CANDIDATES) {
            EditorInput.ask(viewer(), field.label(), field, current,
                    raw -> accept(setter, field, raw), this::reopen);
            return;
        }
        new CandidateMenu(viewer(), messages(), "gui.editor-pick", field.label(),
                CandidateCatalog.of(field.kinds()), current,
                value -> {
                    setter.accept(value);
                    reopen();
                },
                () -> EditorInput.ask(viewer(), field.label(), field, current,
                        raw -> accept(setter, field, raw), this::reopen),
                this::reopen).open();
    }

    /** 把聊天栏输入按字段形状解析成值再交给 setter：解析结果为空（清除）时传空串，别把 null 写成字符串。 */
    private void accept(Consumer<String> setter, ConfigField field, String raw) {
        Object value = FieldValue.parse(field, raw);
        setter.accept(value == null ? "" : String.valueOf(value));
        reopen();
    }

    // ---------- 目标 / 奖励 / 保存 ----------

    private MenuItem nodeEntry(boolean reward) {
        List<QuestDraft.Node> nodes = draft.nodes(reward);
        List<String> lore = new ArrayList<>();
        if (nodes.isEmpty()) {
            lore.add("&7还没有" + (reward ? "奖励" : "目标"));
        } else {
            for (QuestDraft.Node node : nodes) {
                lore.add("&7- &f" + EditorLookup.typeName(messages(), viewer(), reward, node.type()));
            }
        }
        lore.add("&7左键: &f打开列表（增删改）");
        return MenuItem.of(reward ? Material.CHEST : Material.WRITABLE_BOOK,
                "&f" + (reward ? "奖励" : "目标") + " (&f" + nodes.size() + "&f)",
                lore, context -> new NodeListMenu(viewer(), messages(), draft, reward, this::reopen).open());
    }

    /** 校验提示：把 {@code QuestAdminService.validate} 的话原样摆出来，避免「保存了却不生效」查不出原因。 */
    private MenuItem problemItem(PlayerTaskX plugin, Quest quest) {
        List<String> problems = plugin.questAdmin().validate(quest);
        List<String> lore = new ArrayList<>();
        if (problems.isEmpty()) {
            lore.add("&a没有发现问题");
        } else {
            for (String problem : problems) {
                lore.add("&c! " + problem);
            }
            lore.add("&8这些问题不挡保存，但会影响实际效果");
        }
        return MenuItem.display(problems.isEmpty() ? Material.LIME_DYE : Material.ORANGE_DYE,
                "&f校验: " + (problems.isEmpty() ? "&a通过" : "&c" + problems.size() + " 个问题"), lore);
    }

    private MenuItem saveItem(Quest quest) {
        List<String> lore = new ArrayList<>();
        if (quest.id().isBlank()) {
            lore.add("&c任务 id 还没填");
        }
        if (quest.objectives().isEmpty()) {
            lore.add("&c至少要有一个目标，否则载入时会被跳过");
        }
        if (lore.isEmpty()) {
            lore.add("&7保存后立刻生效（在线玩家的进度索引会一起重建）");
        }
        return MenuItem.of(Material.EMERALD, "&a保存", lore, context -> save());
    }

    /** 文件来源的任务写不进去：把保存按钮摆成不可点并说明出路，而不是让管理员存完才看到报错。 */
    private MenuItem readOnlyItem() {
        return MenuItem.display(Material.BARRIER, "&8只读任务",
                List.of("&7这个任务定义在 quests/ 的 YAML 文件里",
                        "&7改文件后 /ptxa reload 生效；数据库里的任务才能在此编辑"));
    }

    /**
     * 保存：只挡「存下去也用不了」的两种情况（id 空、没有目标），其余问题照存——管理员可能正想先存一半。
     * 文件来源的定义由仓储抛 {@link DefinitionReadOnlyException}，这里把原因原样说给管理员。
     */
    private void save() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Quest quest = draft.toQuest();
        if (quest.id().isBlank()) {
            warn("任务 id 不能为空：它同时是数据库主键");
            return;
        }
        if (quest.objectives().isEmpty()) {
            warn("至少要有一个目标，否则这个任务载入后会被跳过");
            return;
        }
        if (creating && plugin.questDefinitions().exists(quest.id())) {
            warn("id " + quest.id() + " 已被占用：换个 id，或从列表里打开它编辑");
            return;
        }
        try {
            plugin.questAdmin().save(quest);
        } catch (DefinitionReadOnlyException e) {
            warn(e.getMessage());
            return;
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("保存任务失败: " + quest.id(), e);
            messages().send(viewer(), "error.internal");
            return;
        }
        creating = false;
        messages().sendRaw(viewer(), Texts.render("&a已保存任务 &f" + quest.id()));
        refresh();
    }

    private void warn(String message) {
        messages().sendRaw(viewer(), Texts.render("&c" + message));
    }

    private void reopen() {
        new QuestEditMenu(viewer(), messages(), draft, creating, onBack).open();
    }
}
