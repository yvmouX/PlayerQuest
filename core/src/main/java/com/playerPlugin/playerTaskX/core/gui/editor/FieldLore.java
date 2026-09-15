package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingHook;
import com.playerPlugin.playerTaskX.core.integration.customfishing.FishLoot;
import com.playerPlugin.playerTaskX.core.schema.ValueKinds;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段在界面里的描述口径：形状、值域、以及「配了也永远不会命中」的判定。
 * 类型列表与字段编辑两处共用同一份措辞，schema 才不会在界面里出现两种说法。
 */
final class FieldLore {

    private FieldLore() {
    }

    /** 控件的说法：候选字段去清单里选，其余在聊天栏打字。 */
    static String shapeText(ConfigField field) {
        return switch (field.shape()) {
            case TEXT -> "文本（聊天栏输入）";
            case INTEGER -> "整数（聊天栏输入）";
            case DECIMAL -> "小数（聊天栏输入）";
            case BOOLEAN -> "是/否（聊天栏输入）";
            case CANDIDATES -> "从清单选择（也可手打）";
        };
    }

    /** 值域的显示名（「方块或实体」）；没有值域返回空串。 */
    static String kindsText(ConfigField field) {
        if (field.kinds().isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>(field.kinds().size());
        for (ValueKind kind : field.kinds()) {
            labels.add(kind.label());
        }
        return String.join("或", labels);
    }

    /**
     * 这个值在当前环境下能不能命中；不能命中时返回原因，否则 {@code null}。
     * {@code fishLoot} 为 {@code null} 表示拿不到鱼 id 清单（没装 CustomFishing），此时一律放行。
     */
    static String problem(ConfigField field, String value, List<String> fishLoot) {
        return ValueKinds.check(field.kinds(), value, fishLoot);
    }

    /** 取该类型所有字段需要的鱼 id 清单：没有一个字段用到 FISH 值域时返回 {@code null}（表示不必查，也判断不了）。 */
    static List<String> fishLoot(List<ConfigField> schema) {
        boolean needed = false;
        for (ConfigField field : schema) {
            needed = needed || field.kinds().contains(ValueKind.FISH);
        }
        if (!needed || !CustomFishingHook.supported()) {
            return null;
        }
        return CustomFishingHook.loot().stream().map(FishLoot::id).toList();
    }
}
