package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import com.playerPlugin.playerTaskX.core.gui.Menu;
import com.playerPlugin.playerTaskX.core.gui.MenuItem;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 新增目标/奖励时选类型：每行一个类型，带它的字段摘要与不可用原因；选中后由调用方建节点并进字段编辑。
 * 类型图标按目标字段的值域推（见 {@link #iconOfType}），因此与节点列表里的图标是同一套。
 */
public final class TypePickMenu extends Menu {

    private static final int SIZE = 54;
    /** 类型区：第 1~4 行；第 5 行放返回。 */
    private static final int LIMIT = 44;
    private static final int BACK_SLOT = 49;

    private final boolean reward;
    private final Consumer<String> onPick;
    private final Runnable onBack;

    /** @param onPick 选中类型的 id（由调用方建节点、开字段编辑） */
    public TypePickMenu(Player viewer, MessageService messages, boolean reward,
                        Consumer<String> onPick, Runnable onBack) {
        super(viewer, messages, SIZE, reward ? "gui.editor-rewards-type" : "gui.editor-objectives-type");
        this.reward = reward;
        this.onPick = onPick;
        this.onBack = onBack;
        refresh();
    }

    @Override
    protected void build() {
        List<ConfigurableType> types = types();
        int shown = Math.min(types.size(), LIMIT);
        for (int slot = 0; slot < shown; slot++) {
            set(slot, typeItem(types.get(slot)));
        }
        // 类型多到放不下（别的插件注册了一堆）时明说剩下多少，而不是悄悄截断
        if (types.size() > LIMIT) {
            set(LIMIT, MenuItem.display(Material.BARRIER, "&7还有 " + (types.size() - LIMIT) + " 个类型未显示",
                    List.of("&7这里只放得下 " + LIMIT + " 个")));
        }
        set(BACK_SLOT, MenuItem.of(Material.ARROW, text("gui.back"), List.of(), context -> onBack.run()));
        fill(MenuItem.filler());
    }

    private List<ConfigurableType> types() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        return new ArrayList<>(reward ? plugin.rewardTypes().all() : plugin.objectiveTypes().all());
    }

    private MenuItem typeItem(ConfigurableType type) {
        String name = "&f" + EditorLookup.typeName(messages(), viewer(), reward, type.id());
        String unavailable = unavailable(type);
        List<String> lore = new ArrayList<>();
        lore.add("&8" + type.id());
        lore.addAll(fieldLines(type.schema()));
        if (unavailable != null) {
            // 不可用的类型照样能选：先配好、装回软依赖即可生效，但要一眼看出现在不生效
            lore.add("&c当前不可用：" + unavailable);
        }
        lore.add("&7左键: &f新增这个" + (reward ? "奖励" : "目标"));
        return MenuItem.of(EditorLookup.icon(type), name, lore, context -> onPick.accept(type.id()));
    }

    /** 字段摘要：一行一个字段，写明控件与值域，省得进编辑界面才发现要填什么。 */
    private List<String> fieldLines(List<ConfigField> schema) {
        List<String> lines = new ArrayList<>(schema.size());
        for (ConfigField field : schema) {
            String kinds = FieldLore.kindsText(field);
            lines.add("&7- &f" + field.label() + (kinds.isEmpty() ? "" : " &8(" + kinds + ")")
                    + " &8" + FieldLore.shapeText(field));
        }
        return lines;
    }

    /** 不可用原因；可用时返回 {@code null}（available/unavailableReason 两种类型各自声明，ConfigurableType 上没有）。 */
    private static String unavailable(ConfigurableType type) {
        if (type instanceof RewardType rewardType) {
            return rewardType.available() ? null : reasonOf(rewardType.unavailableReason());
        }
        ObjectiveType objective = (ObjectiveType) type;
        return objective.available() ? null : reasonOf(objective.unavailableReason());
    }

    /** 类型自己没写原因时兜一句，免得界面上出现「当前不可用：」这种半句话。 */
    private static String reasonOf(String reason) {
        return reason == null || reason.isBlank() ? "软依赖未就绪" : reason;
    }
}
