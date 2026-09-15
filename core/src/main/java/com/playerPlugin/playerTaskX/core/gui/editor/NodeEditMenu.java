package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import cn.yvmou.ylib.gui.Menu;
import cn.yvmou.ylib.gui.MenuItem;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑一个目标/奖励的字段：每行一个字段，左键改（候选字段进清单挑，其余在聊天栏打字）、右键清除。
 * 引用了预设的节点不给改字段——字段由预设提供，多写的键不生效（{@code PresetRefs} 会报出来），因此这里只给「展开为独立配置」和「更换预设」。
 */
public final class NodeEditMenu extends Menu {

    private static final int SIZE = 54;
    /** 字段区：前 3 行（内置类型最多 3 个字段）；字段是逐个往下排的，因此仍用数字下标。 */
    private static final int FIELD_LIMIT = 27;

    /** 界面布局（见 {@code SlotLayout}）：字段区是往下排的（用数字下标），预设与返回按名字摆。 */
    private static final String[] SHAPE = {
            "    `hint`",
            "",
            "",
            "  `preset` `action` `swap`",
            "",
            "    `back`",
    };

    private final QuestDraft draft;
    private final boolean reward;
    private final int index;
    private final Runnable onBack;

    public NodeEditMenu(Player viewer, MessageService messages, QuestDraft draft, boolean reward,
                        int index, Runnable onBack) {
        super(viewer, messages, SIZE, "gui.editor-fields");
        this.draft = draft;
        this.reward = reward;
        this.index = index;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        layout(SHAPE);
        set("back", MenuItem.of(Material.ARROW, text("gui.back"), List.of(), context -> onBack.run()));

        List<QuestDraft.Node> nodes = draft.nodes(reward);
        if (index < 0 || index >= nodes.size()) {
            // 节点在别处被删了：说清楚而不是开出一个空界面
            set("hint", MenuItem.display(Material.BARRIER, "&c这个" + label() + "已经不在了",
                    List.of("&7可能在列表里被删掉了，返回即可")));
            fill(MenuItem.filler());
            return;
        }
        QuestDraft.Node node = nodes.get(index);
        String presetId = presetId(node);
        if (presetId != null) {
            buildPreset(presetId);
            fill(MenuItem.filler());
            return;
        }

        ConfigurableType type = EditorLookup.type(reward, node.type());
        if (type == null) {
            set("hint", MenuItem.display(Material.BARRIER, "&c类型 " + node.type() + " 没有注册",
                    List.of("&7插件被移除或 id 写错时会出现这种情况", "&7请回列表删掉这个" + label() + "或改配置文件")));
        } else {
            buildFields(type, node);
        }
        set("preset", MenuItem.of(Material.BOOK, "&e引用预设",
                List.of("&7把字段交给一份预设来提供", "&7引用后本" + label() + "就不能再单独写字段",
                        "&8注意：当前已填的字段会被预设替换"),
                context -> pickPreset()));
        fill(MenuItem.filler());
    }

    /** 字段行：当前值、说明、控件、值域，以及「配了也永远不会命中」的当场判定。 */
    private void buildFields(ConfigurableType type, QuestDraft.Node node) {
        List<ConfigField> schema = type.schema();
        List<String> fishLoot = FieldLore.fishLoot(schema);
        int slot = 0;
        for (ConfigField field : schema) {
            if (slot >= FIELD_LIMIT) {
                break;
            }
            Object value = node.authored().get(field.key());
            String current = FieldValue.display(value);
            List<String> lore = new ArrayList<>();
            lore.add("&7当前: &f" + (current.isEmpty() ? "&8未设置" : current));
            if (field.hint() != null && !field.hint().isBlank()) {
                lore.add("&7" + field.hint());
            }
            String kinds = FieldLore.kindsText(field);
            lore.add("&8" + FieldLore.shapeText(field) + (kinds.isEmpty() ? "" : "　值域: " + kinds));
            String problem = FieldLore.problem(field, current, fishLoot);
            if (problem != null) {
                // 校验在保存时才会报的东西，编辑时就摆在眼前
                lore.add("&c! " + problem);
            }
            lore.add("&7左键: &f修改");
            if (value != null) {
                lore.add("&7右键: &f清除（留空 = 任意）");
            }
            set(slot++, MenuItem.of(iconOf(field), "&f" + field.label(), lore,
                    context -> {
                        if (context.clickType().isRightClick()) {
                            setValue(field, null);
                        } else {
                            edit(field, current);
                        }
                    }));
        }
    }

    /** 引用了预设：展示预设内容，只留「展开」与「更换」两个动作。 */
    private void buildPreset(String presetId) {
        Preset preset = PlayerTaskX.getInstance().presetDefinitions().findById(presetId).orElse(null);
        List<String> lore = new ArrayList<>();
        lore.add("&7预设 id: &f" + presetId);
        if (preset == null) {
            lore.add("&c这个预设不存在（已删除或 id 写错），本" + label() + "当前不生效");
            lore.add("&7改配置文件里的 presets/ 或换一个预设");
        } else {
            lore.add("&7类型: &f" + EditorLookup.typeName(messages(), viewer(), reward, preset.type()));
            lore.add("&7配置: &f" + Texts.properties(preset.properties()));
        }
        set("preset", MenuItem.display(Material.BOOK, "&e引用的预设", lore));

        if (preset == null) {
            set("action", MenuItem.display(Material.BARRIER, "&8展开为独立配置",
                    List.of("&7预设不存在，展开不出字段")));
        } else {
            set("action", MenuItem.of(Material.CRAFTING_TABLE, "&e展开为独立配置",
                    List.of("&7把预设的字段抄成本" + label() + "自己的配置",
                            "&7之后就可以单独调值（不再跟着预设变）"),
                    context -> {
                        draft.node(reward, index, new QuestDraft.Node(preset.type(), preset.properties()));
                        reopen();
                    }));
        }
        set("swap", MenuItem.of(Material.BOOK, "&e更换预设",
                List.of("&7另选一份预设来引用"), context -> pickPreset()));
    }

    // ---------- 改值 ----------

    /** 候选字段进清单挑，其余形状在聊天栏打字；聊天栏里手打的值同样按字段形状校验。 */
    private void edit(ConfigField field, String current) {
        if (field.shape() == ConfigField.Shape.CANDIDATES) {
            new CandidateMenu(viewer(), messages(), "gui.editor-pick", field.label(),
                    CandidateCatalog.of(field.kinds()), current,
                    value -> setValue(field, value),
                    () -> EditorInput.ask(viewer(), field.label(), field, current,
                            raw -> setValue(field, FieldValue.parse(field, raw)), this::reopen),
                    this::reopen).open();
            return;
        }
        EditorInput.ask(viewer(), field.label(), field, current,
                raw -> setValue(field, FieldValue.parse(field, raw)), this::reopen);
    }

    /** 写回一个字段：{@code null} 表示清除该键（留空 = 任意），改完刷新界面让新值立刻可见。 */
    private void setValue(ConfigField field, Object value) {
        QuestDraft.Node node = draft.nodes(reward).get(index);
        Map<String, Object> authored = new LinkedHashMap<>(node.authored());
        if (value == null) {
            authored.remove(field.key());
        } else {
            authored.put(field.key(), value);
        }
        draft.node(reward, index, new QuestDraft.Node(node.type(), authored));
        reopen();
    }

    /** 选一份同类别的预设来引用；预设 id 是它的身份，因此选择器里只列 id 与内容。 */
    private void pickPreset() {
        List<Preset> presets = EditorLookup.presets(reward);
        List<CandidateCatalog.Candidate> candidates = new ArrayList<>(presets.size());
        for (Preset preset : presets) {
            candidates.add(new CandidateCatalog.Candidate(preset.id(), new ItemStack(Material.BOOK),
                    preset.type() + " " + Texts.properties(preset.properties())));
        }
        new CandidateMenu(viewer(), messages(), "gui.editor-pick", "预设", candidates, "",
                id -> {
                    // 预设不存在时类型留空：这与 PresetRefs.resolve 对悬空引用的处理一致，问题由校验报出来
                    Preset preset = PlayerTaskX.getInstance().presetDefinitions().findById(id).orElse(null);
                    draft.node(reward, index, new QuestDraft.Node(preset == null ? "" : preset.type(),
                            Map.of(QuestObjective.PRESET_KEY, id)));
                    reopen();
                },
                null, this::reopen).open();
    }

    // ---------- 展示 ----------

    /** 字段图标：按它的值域给（没值域的形状各有各的图标），让一行行字段能一眼分辨。 */
    private static Material iconOf(ConfigField field) {
        return switch (field.shape()) {
            case TEXT -> Material.NAME_TAG;
            case INTEGER -> Material.PAPER;
            case DECIMAL -> Material.GOLD_NUGGET;
            case BOOLEAN -> Material.LEVER;
            case CANDIDATES -> EditorLookup.icon(field.kinds().get(0));
        };
    }

    private static String presetId(QuestDraft.Node node) {
        Object value = node.authored().get(QuestObjective.PRESET_KEY);
        return value == null ? null : String.valueOf(value);
    }

    private void reopen() {
        new NodeEditMenu(viewer(), messages(), draft, reward, index, onBack).open();
    }

    private String label() {
        return reward ? "奖励" : "目标";
    }
}
