package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.gui.MenuItem;
import cn.yvmou.ylib.gui.PagedMenu;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.text.TextRenderer;
import com.playerPlugin.playerTaskX.core.gui.editor.CandidateCatalog.Candidate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 候选值选择器：把调用方算好的候选清单摆成分页箱子界面，点一下把值回传。
 * 翻页与页码由 {@link PagedMenu} 管（{@code #} 是条目区、{@code prev}/{@code pages}/{@code next} 是翻页），
 * 这里只管「一条候选长什么样」与「返回 / 手动输入 / 当前值」三个额外按钮。
 * 文案中文直写——编辑器只给管理员用，与 {@code /ptxa list}、{@code /ptxa info} 保持同一套措辞。
 */
public final class CandidateMenu extends PagedMenu<Candidate> {

    /** 界面布局：上 4 行是候选区（每页 36 条），下 2 行是按钮。 */
    private static final String[] SHAPE = {
            "#########",
            "#########",
            "#########",
            "#########",
            "`back` `manual` `value`",
            "`prev` `pages` `next`",
    };

    private final List<Candidate> candidates;
    private final String current;
    private final Consumer<String> onPick;
    private final Runnable onManual;
    private final Runnable onBack;

    /**
     * @param titleKey   标题语言键
     * @param titleArg   标题占位符参数（没有就传 null）
     * @param candidates 已经算好的候选清单——选择器只负责渲染，清单由调用方给（预设选择器复用同一个界面）
     * @param current    字段当前值，仅展示
     * @param onPick     左键候选的回调；界面切换与刷新由调用方负责
     * @param onManual   手动输入入口；null 表示这个字段不支持手打，按钮摆成灰色
     * @param onBack     返回回调
     */
    public CandidateMenu(Player viewer, MessageService messages, String titleKey, Object titleArg,
                         List<Candidate> candidates, String current,
                         Consumer<String> onPick, Runnable onManual, Runnable onBack) {
        super(viewer, messages, 54, titleKey, titleArg);
        this.candidates = candidates == null ? Collections.<Candidate>emptyList() : candidates;
        this.current = current;
        this.onPick = onPick == null ? value -> {
        } : onPick;
        this.onManual = onManual;
        this.onBack = onBack;
    }

    @Override
    protected String[] shape() {
        return SHAPE;
    }

    @Override
    protected List<Candidate> items() {
        return candidates;
    }

    /** 候选物品：图标与备注来自清单（备注已写进 lore），这里只补一行操作提示。 */
    @Override
    protected MenuItem render(Candidate candidate, int index) {
        ItemStack stack = candidate.icon() == null ? new ItemStack(Material.PAPER) : candidate.icon().clone();
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null
                        ? new ArrayList<String>() : new ArrayList<String>(meta.getLore());
                lore.add(TextRenderer.render("&7左键选择"));
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
            // 补不上提示行只损失一行字，候选本身照常可选
        }
        return new MenuItem(stack, context -> onPick.accept(candidate.value()));
    }

    @Override
    protected MenuItem whenEmpty() {
        return MenuItem.display(Material.BARRIER, "&c没有可选项",
                List.of("&7现成的清单取不到，请用手动输入"));
    }

    /** 返回 / 手动输入 / 当前值：翻页那三个按钮由基类摆。 */
    @Override
    protected void decorate() {
        set("back", MenuItem.of(Material.ARROW, "返回", List.of("&7回到上一层"),
                context -> run(onBack)));

        if (onManual == null) {
            // 不支持手打的字段把按钮摆成灰色而不是不显示：位置固定，管理员才不会以为界面坏了
            set("manual", MenuItem.display(Material.GRAY_DYE, "手动输入",
                    List.of("&7这个字段只能从清单里挑")));
        } else {
            set("manual", MenuItem.of(Material.WRITABLE_BOOK, "手动输入",
                    List.of("&7左键在聊天栏里打（别家插件的自定义 id 走这里）"), context -> run(onManual)));
        }

        set("value", MenuItem.display(Material.NAME_TAG, "当前值: " + shownValue(),
                List.of("&7留空 / &f* &7表示任意")));
    }

    /** 当前值的展示形态：空值说「未设置」，别让人以为界面没刷新。 */
    private String shownValue() {
        return current == null || current.trim().isEmpty() ? "未设置" : current;
    }

    /** 回调允许缺省：缺省时点下去什么都不做，而不是在事件里抛 NPE。 */
    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
