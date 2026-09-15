package com.playerPlugin.playerTaskX.core.integration.customcontent;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import com.playerPlugin.playerTaskX.core.integration.Reflect;

/** ItemsAdder 接入点（全反射）：兼容新的 CustomStack/CustomBlock 与旧的静态 ItemsAdder 两套签名，创建时一次性绑好方法（查询在挖掘热路径上），都拿不到则返回 null。 */
final class ItemsAdderHook implements CustomContentHook {

    private static final String PLUGIN = "ItemsAdder";
    private static final String PREFIX = "itemsadder:";

    /** 查物品 id 的两种签名：CustomStack.byItemStack → getNamespacedID，或 ItemsAdder.getCustomItemName。 */
    private final Method itemLookup;
    private final Method itemIdGetter;
    /** 查方块 id：CustomBlock.byAlreadyPlaced → getNamespacedID。 */
    private final Method blockLookup;
    private final Method blockIdGetter;

    private ItemsAdderHook(Method itemLookup, Method itemIdGetter, Method blockLookup, Method blockIdGetter) {
        this.itemLookup = itemLookup;
        this.itemIdGetter = itemIdGetter;
        this.blockLookup = blockLookup;
        this.blockIdGetter = blockIdGetter;
    }

    @Nullable
    static ItemsAdderHook create() {
        Class<?> customStack = Reflect.findClass("dev.lone.itemsadder.api.CustomStack");
        Class<?> customBlock = Reflect.findClass("dev.lone.itemsadder.api.CustomBlock");
        Class<?> legacy = Reflect.findClass("dev.lone.itemsadder.api.ItemsAdder");

        Method itemLookup = null;
        Method itemIdGetter = null;
        if (customStack != null) {
            itemLookup = Reflect.method(customStack, "byItemStack", ItemStack.class);
            itemIdGetter = Reflect.method(customStack, "getNamespacedID");
        }
        if (itemIdGetter == null && legacy != null) {
            // 旧静态 API：直接给「配置里的名字」（不含命名空间），取名字这一条就够
            itemLookup = Reflect.method(legacy, "getCustomItemName", ItemStack.class);
        }

        Method blockLookup = null;
        Method blockIdGetter = null;
        if (customBlock != null) {
            blockLookup = Reflect.method(customBlock, "byAlreadyPlaced", Block.class);
            blockIdGetter = Reflect.method(customBlock, "getNamespacedID");
        }

        if (itemLookup == null && blockLookup == null) {
            return null;
        }
        return new ItemsAdderHook(itemLookup, itemIdGetter, blockLookup, blockIdGetter);
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

}
