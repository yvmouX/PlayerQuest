package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.core.gui.Menu;
import com.playerPlugin.playerTaskX.core.gui.MenuItem;
import com.playerPlugin.playerTaskX.core.gui.editor.CandidateCatalog.Candidate;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 候选值选择器：把调用方算好的候选清单摆成分页箱子界面，点一下把值回传。
 * 文案中文直写——编辑器只给管理员用，与 {@code /ptxa list}、{@code /ptxa info} 保持同一套措辞。
 */
public final class CandidateMenu extends Menu {

    private static final int SIZE = 54;

    /** 每页 45 个：最后一行留返回、手动输入、翻页与当前值（列表区用数字下标，见 {@code Menu#layout}）。 */
    private static final int PAGE_SIZE = 45;

    /** 界面布局（见 {@code SlotLayout}）：底部一排按钮的位置一眼可见。 */
    private static final String[] SHAPE = {
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    empty .   .    .    .",
            ".    .    .    .    .    .    .    .    .",
            ".    .    .    .    .    .    .    .    .",
            "back .    manual .    prev .    current .    next",
    };

    private final List<Candidate> candidates;
    private final String current;
    private final Consumer<String> onPick;
    private final Runnable onManual;
    private final Runnable onBack;

    /** 当前页码（0 基）：翻页就地改它再 refresh()，不必重建整个菜单。 */
    private int page;

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
        super(viewer, messages, SIZE, titleKey, titleArg);
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
        this.current = current;
        this.onPick = onPick == null ? value -> {
        } : onPick;
        this.onManual = onManual;
        this.onBack = onBack;
        // 框架硬性要求放在构造最后一行：基类构造期回调会读到上面这些还没赋值的字段
        refresh();
    }

    @Override
    protected void build() {
        int totalPages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        // 清单换了之后页码可能越界：夹回合法范围，而不是展示一页空白
        page = Math.min(Math.max(page, 0), totalPages - 1);
        layout(SHAPE);

        if (candidates.isEmpty()) {
            set("empty", MenuItem.display(Material.BARRIER, "&c没有可选项",
                    List.of("&7现成的清单取不到，请用手动输入")));
        } else {
            int from = page * PAGE_SIZE;
            for (int offset = 0; offset < PAGE_SIZE && from + offset < candidates.size(); offset++) {
                set(offset, candidateItem(candidates.get(from + offset)));
            }
        }

        buildFooter(totalPages);
        fill(MenuItem.filler());
    }

    // ---------- 候选项 ----------

    /** 候选物品：图标与备注来自清单（备注已写进 lore），这里只补一行操作提示。 */
    private MenuItem candidateItem(Candidate candidate) {
        ItemStack stack = candidate.icon() == null ? new ItemStack(Material.PAPER) : candidate.icon().clone();
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                lore.add(Texts.render("&7左键选择"));
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
            // 补不上提示行只损失一行字，候选本身照常可选（本类不在无服务端环境里构建）
        }
        return new MenuItem(stack, context -> onPick.accept(candidate.value()));
    }

    // ---------- 底部一行 ----------

    private void buildFooter(int totalPages) {
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

        set("current", MenuItem.display(Material.NAME_TAG, "当前值: " + shownValue(),
                List.of("&7留空 / &f* &7表示任意")));

        if (page > 0) {
            set("prev", MenuItem.of(Material.ARROW, "上一页", List.of(), context -> {
                page--;
                refresh();
            }));
        } else {
            set("prev", MenuItem.display(Material.GRAY_DYE, "上一页", List.of()));
        }

        if (page < totalPages - 1) {
            set("next", MenuItem.of(Material.ARROW, "下一页", List.of(), context -> {
                page++;
                refresh();
            }));
        } else {
            set("next", MenuItem.display(Material.GRAY_DYE, "下一页", List.of()));
        }
    }

    /** 当前值的展示形态：空值说「未设置」，别让人以为界面没刷新。 */
    private String shownValue() {
        return current == null || current.isBlank() ? "未设置" : current;
    }

    /** 回调允许缺省：缺省时点下去什么都不做，而不是在事件里抛 NPE。 */
    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
