package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ItemsAdder 接入点（反射，不依赖它的 API 构件）。
 *
 * <h2>为什么容错到「找不到方法就用备用签名」</h2>
 * ItemsAdder 同时存在两套公开 API：新的 {@code dev.lone.itemsadder.api.CustomStack} /
 * {@code CustomBlock}（实例对象 + {@code getNamespacedID()}），以及更早的静态工具
 * {@code dev.lone.itemsadder.api.ItemsAdder}（{@code isCustomItem} / {@code getCustomItemName}）。
 * 两者在不同大版本里都可能在、也可能只有其一。这里在 {@link #create()} 时把能用的方法
 * <b>一次性绑好</b>，之后每次查询只剩一次 invoke——热路径（每挖一个方块）上不做方法名解析。
 *
 * <p>两套都拿不到时返回 {@code null}：调用方记一条 warn 并放弃这个来源，
 * 原版材质的目标照常工作。
 */
final class ItemsAdderHook implements CustomContentHook {

    private static final String PLUGIN = "ItemsAdder";
    private static final String PREFIX = "itemsadder:";

    /** 查物品 id 的两种签名：CustomStack.byItemStack → getNamespacedID，或 ItemsAdder.getCustomItemName。 */
    private final Method itemLookup;
    private final Method itemIdGetter;
    /** 查方块 id：CustomBlock.byAlreadyPlaced → getNamespacedID。 */
    private final Method blockLookup;
    private final Method blockIdGetter;
    /** 列出全部 id：CustomStack/CustomBlock.getNamespacedIdsInRegistry（静态）。 */
    private final Method itemRegistry;
    private final Method blockRegistry;

    private ItemsAdderHook(Method itemLookup, Method itemIdGetter, Method blockLookup, Method blockIdGetter,
                           Method itemRegistry, Method blockRegistry) {
        this.itemLookup = itemLookup;
        this.itemIdGetter = itemIdGetter;
        this.blockLookup = blockLookup;
        this.blockIdGetter = blockIdGetter;
        this.itemRegistry = itemRegistry;
        this.blockRegistry = blockRegistry;
    }

    @Nullable
    static ItemsAdderHook create() {
        Class<?> customStack = Reflect.findClass("dev.lone.itemsadder.api.CustomStack");
        Class<?> customBlock = Reflect.findClass("dev.lone.itemsadder.api.CustomBlock");
        Class<?> legacy = Reflect.findClass("dev.lone.itemsadder.api.ItemsAdder");

        Method itemLookup = null;
        Method itemIdGetter = null;
        Method itemRegistry = null;
        if (customStack != null) {
            itemLookup = Reflect.method(customStack, "byItemStack", ItemStack.class);
            itemIdGetter = Reflect.method(customStack, "getNamespacedID");
            itemRegistry = Reflect.method(customStack, "getNamespacedIdsInRegistry");
        }
        if (itemIdGetter == null && legacy != null) {
            // 旧静态 API：直接给「配置里的名字」（不含命名空间），取名字这一条就够
            itemLookup = Reflect.method(legacy, "getCustomItemName", ItemStack.class);
        }

        Method blockLookup = null;
        Method blockIdGetter = null;
        Method blockRegistry = null;
        if (customBlock != null) {
            blockLookup = Reflect.method(customBlock, "byAlreadyPlaced", Block.class);
            blockIdGetter = Reflect.method(customBlock, "getNamespacedID");
            blockRegistry = Reflect.method(customBlock, "getNamespacedIdsInRegistry");
        }

        if (itemLookup == null && blockLookup == null) {
            return null;
        }
        return new ItemsAdderHook(itemLookup, itemIdGetter, blockLookup, blockIdGetter, itemRegistry, blockRegistry);
    }

    @Override
    public String plugin() {
        return PLUGIN;
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public @Nullable String itemId(ItemStack item) {
        return namespacedId(itemLookup, itemIdGetter, item);
    }

    @Override
    public @Nullable String blockId(Block block) {
        return namespacedId(blockLookup, blockIdGetter, block);
    }

    @Override
    public List<String> itemIds() {
        return registry(itemRegistry);
    }

    @Override
    public List<String> blockIds() {
        return registry(blockRegistry);
    }

    // ------------------------------------------------------------------

    /**
     * {@code lookup(arg)} 拿到实例后取它的 id；旧 API 的 lookup 直接返回名字，因此
     * {@code idGetter} 为 null 时就用返回值本身。
     */
    private static String namespacedId(Method lookup, Method idGetter, Object argument) {
        if (lookup == null || argument == null) {
            return null;
        }
        try {
            Object result = lookup.invoke(null, argument);
            if (result == null) {
                return null;
            }
            if (idGetter == null) {
                return String.valueOf(result);
            }
            Object id = idGetter.invoke(result);
            return id == null ? null : String.valueOf(id);
        } catch (Throwable e) {
            // 单次查询失败按「不是自定义内容」处理：宁可少一个别名，也不能把事件处理带崩
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> registry(Method method) {
        if (method == null) {
            return List.of();
        }
        try {
            Object result = method.invoke(null);
            if (result instanceof List<?> list) {
                List<String> ids = new ArrayList<>(list.size());
                for (Object id : list) {
                    if (id != null) {
                        ids.add(String.valueOf(id));
                    }
                }
                return ids;
            }
        } catch (Throwable e) {
            // 编辑器目录只是便利功能，取不到就空着
            return List.of();
        }
        return List.of();
    }

}
