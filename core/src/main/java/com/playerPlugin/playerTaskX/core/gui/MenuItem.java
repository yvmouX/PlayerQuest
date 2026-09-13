package com.playerPlugin.playerTaskX.core.gui;

import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 菜单项：一个图标 + 一条点击动作。
 *
 * <h2>为什么是 record</h2>
 * 菜单每次 {@code build()} 都重建整套物品，菜单项天然是「一次性快照」。
 * 若做成可变对象并跨次复用，上一次的状态（改过的数量、旧的进度文案）
 * 就会被带进新界面，这类 bug 极难排查。record 让「重新构造」成为唯一写法。
 *
 * <h2>图标为什么在构造里克隆</h2>
 * {@code ItemStack} 是可变对象，而同一份图标常被多个槽位共用（背景板、分页按钮）。
 * 在构造里克隆一份，调用方后续改数量/名字都不会串味。
 *
 * @param icon   图标（会被克隆，不足的数量按 1 处理）
 * @param action 点击动作，{@code null} 视为「什么也不做」
 */
public record MenuItem(ItemStack icon, Consumer<ClickContext> action) {

    /** 点击上下文：动作只需要知道「谁点的」与「怎么点的」。 */
    public record ClickContext(Player player, ClickType clickType) {
    }

    public MenuItem {
        icon = (icon == null ? new ItemStack(Material.PAPER) : icon).clone();
        // 空动作而不是 null：调用方（含背景板）不必再判空，避免每次都写 context -> { }
        action = action == null ? context -> {
        } : action;
    }

    // ---------- 静态工厂 ----------

    /**
     * 构造一个可点击的菜单项。
     *
     * @param material 图标材质，{@code null} 或非法时回退 {@link Material#PAPER}
     * @param name     显示名（MiniMessage 或 &amp; 颜色码，这里统一渲染成 {@code §} 形式）
     * @param lore     描述行，可为 {@code null}
     * @param action   点击动作，可为 {@code null}
     */
    public static MenuItem of(Material material, String name, List<String> lore, Consumer<ClickContext> action) {
        return new MenuItem(icon(material, name, lore), action);
    }

    /** 无描述行的便捷重载。 */
    public static MenuItem of(Material material, String name, Consumer<ClickContext> action) {
        return of(material, name, List.of(), action);
    }

    /** 只展示、不响应点击的物品（头部信息、禁用态的分页按钮）。 */
    public static MenuItem display(Material material, String name, List<String> lore) {
        return of(material, name, lore, null);
    }

    /**
     * 背景填充物：灰色玻璃板 + 一个空格名字。
     * <p>
     * 名字用空格而不是空串或材质默认名：「空串」在部分客户端版本下会回退显示材质名，
     * 「空格」则稳定地什么都不显示。
     */
    public static MenuItem filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), null);
    }

    /**
     * 材质名 → 材质，解析失败回退 {@link Material#PAPER}。
     * <p>
     * 配置里把材质名写错（或写了新版本才有的方块）不该让整个界面开不出来，
     * 显示成纸反而能让管理员一眼看出「这条配置的图标有问题」。
     */
    public static Material material(String name) {
        if (name == null || name.isBlank()) {
            return Material.PAPER;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    // ---------- 实例方法 ----------

    /**
     * 复制一份并改成指定数量，用于让图标本身承载信息（例如目标进度 3/64 显示成 3 个物品）。
     * <p>
     * 数量会被夹到 {@code [1, getMaxStackSize()]}：超出堆叠上限的数量在客户端上
     * 会显示成乱码般的数字，也不符合原版渲染规则。
     */
    public MenuItem withAmount(int amount) {
        ItemStack copy = icon.clone();
        copy.setAmount(Math.max(1, Math.min(amount, Math.max(1, copy.getMaxStackSize()))));
        return new MenuItem(copy, action);
    }

    // ---------- 内部实现 ----------

    /** 构建带名字与描述的图标；名字与描述都按菜单的统一规则渲染成 {@code §} 形式。 */
    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            // 理论上不会发生；真发生了也只用原始材质，不让界面开不出来
            return stack;
        }
        if (name != null) {
            meta.setDisplayName(Texts.render(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                if (line != null) {
                    lines.add(Texts.render(line));
                }
            }
            meta.setLore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
