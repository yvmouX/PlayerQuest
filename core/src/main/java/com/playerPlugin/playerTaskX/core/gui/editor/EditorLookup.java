package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 编辑器问注册表的那几件事：按 id 找类型、显示名、图标，以及按类别取预设。
 * 类型列表、节点列表、字段编辑三处问的是同一件事，集中一处免得显示名与图标的口径分叉。
 */
final class EditorLookup {

    private EditorLookup() {
    }

    /** 按 id 找目标（或奖励）类型；没注册返回 {@code null}（插件被移除、id 写错）。 */
    static ConfigurableType type(boolean reward, String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (reward) {
            return plugin.rewardTypes().find(id).orElse(null);
        }
        return plugin.objectiveTypes().find(id).orElse(null);
    }

    /** 类型显示名：优先语言键，缺失时退回类型自己写的显示名。 */
    static String typeName(MessageService messages, Player viewer, boolean reward, String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (reward) {
            return Texts.typeName(messages, viewer, "reward", id, plugin.rewardTypes().displayName(id));
        }
        return Texts.typeName(messages, viewer, "objective", id, plugin.objectiveTypes().displayName(id));
    }

    /**
     * 类型图标：按第一个带值域的字段推，让列表能一眼扫出「这是挖方块的还是杀怪物的」。
     * 没有值域（关键词、命令名这类自由字段）的类型统一用书。
     */
    static Material icon(ConfigurableType type) {
        for (ConfigField field : type.schema()) {
            if (!field.kinds().isEmpty()) {
                return icon(field.kinds().get(0));
            }
        }
        return Material.WRITABLE_BOOK;
    }

    /** 单个值域的图标（字段行也用同一套，免得同一种值域两处图标不一样）。 */
    static Material icon(ValueKind kind) {
        return switch (kind) {
            case BLOCK, PLACEABLE -> Material.STONE;
            case ITEM -> Material.DIAMOND;
            case ENTITY, LIVING, BREEDABLE, TAMEABLE, SHEARABLE -> Material.ZOMBIE_HEAD;
            case FISH -> Material.TROPICAL_FISH;
            case ENCHANTMENT -> Material.ENCHANTED_BOOK;
        };
    }

    /** 该类别（目标/奖励）的全部预设，按 id 排序（注册表顺序来自存储，翻页与列表都要稳定）。 */
    static List<Preset> presets(boolean reward) {
        String kind = reward ? Preset.REWARDS : Preset.OBJECTIVES;
        List<Preset> presets = new ArrayList<>();
        for (Preset preset : PlayerTaskX.getInstance().presetDefinitions().findAll()) {
            if (kind.equals(preset.kind())) {
                presets.add(preset);
            }
        }
        presets.sort(Comparator.comparing(Preset::id));
        return presets;
    }
}
