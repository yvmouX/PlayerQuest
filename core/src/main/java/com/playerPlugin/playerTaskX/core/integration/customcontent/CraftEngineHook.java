package com.playerPlugin.playerTaskX.core.integration.customcontent;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import com.playerPlugin.playerTaskX.core.integration.Reflect;

/**
 * CraftEngine 接入点（全反射）：查物品与方块的自定义 id，创建时一次性绑好方法。
 * <p>它的自定义方块在服务端仍是原版方块（靠方块状态 + 资源包呈现），破坏/放置照常触发原版事件，不必监听它自己的事件。
 */
final class CraftEngineHook implements CustomContentHook {

    private static final String PLUGIN = "CraftEngine";
    private static final String PREFIX = "craftengine:";

    private final Method itemIdLookup;
    private final Method itemByStack;
    private final Method blockIsCustom;
    private final Method blockStateLookup;
    private final Method blockOwnerOfState;

    private CraftEngineHook(Method itemIdLookup, Method itemByStack, Method blockIsCustom,
                            Method blockStateLookup, Method blockOwnerOfState) {
        this.itemIdLookup = itemIdLookup;
        this.itemByStack = itemByStack;
        this.blockIsCustom = blockIsCustom;
        this.blockStateLookup = blockStateLookup;
        this.blockOwnerOfState = blockOwnerOfState;
    }

    @Nullable
    static CraftEngineHook create() {
        Class<?> items = Reflect.findClass("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
        Class<?> blocks = Reflect.findClass("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
        if (items == null || blocks == null) {
            return null;
        }

        // 物品 id：优先 getCustomItemId（直接返回 Key），退回 byItemStack + ItemDefinition.id()
        Method itemIdLookup = Reflect.method(items, "getCustomItemId", ItemStack.class);
        Method itemByStack = Reflect.method(items, "byItemStack", ItemStack.class);
        if (itemIdLookup == null && itemByStack == null) {
            return null;
        }

        // 方块 id：isCustomBlock 先挡一道，再 getCustomBlockState → owner().value().id()
        Method blockIsCustom = Reflect.method(blocks, "isCustomBlock", Block.class);
        Method blockStateLookup = Reflect.method(blocks, "getCustomBlockState", Block.class);
        Method blockOwnerOfState = blockStateLookup == null
                ? null
                : Reflect.method(blockStateLookup.getReturnType(), "owner");
        if (blockStateLookup == null || blockOwnerOfState == null) {
            // 取不到方块状态时仍然保留物品能力：能接多少接多少，别整家放弃
            blockStateLookup = null;
        }

        return new CraftEngineHook(itemIdLookup, itemByStack, blockIsCustom, blockStateLookup,
                blockOwnerOfState);
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
        if (item == null) {
            return null;
        }
        try {
            if (itemIdLookup != null) {
                Object key = itemIdLookup.invoke(null, item);
                // Key.toString() 就是 namespace:path
                return key == null ? null : String.valueOf(key);
            }
            Object definition = itemByStack.invoke(null, item);
            Object id = invokeNoArg(definition, "id");
            return id == null ? null : String.valueOf(id);
        } catch (Throwable e) {
            // 单次查询失败按「不是自定义内容」处理：宁可少一个别名，也不能把事件处理带崩
            return null;
        }
    }

    @Override
    public @Nullable String blockId(Block block) {
        if (block == null || blockStateLookup == null) {
            return null;
        }
        try {
            if (blockIsCustom != null && Boolean.FALSE.equals(blockIsCustom.invoke(null, block))) {
                return null;
            }
            Object state = blockStateLookup.invoke(null, block);
            if (state == null) {
                return null;
            }
            Object holder = blockOwnerOfState.invoke(state);
            Object definition = invokeNoArg(holder, "value");
            if (definition == null) {
                return null;
            }
            Object id = invokeNoArg(definition, "id");
            return id == null ? null : String.valueOf(id);
        } catch (Throwable e) {
            return null;
        }
    }

    // ------------------------------------------------------------------

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable e) {
            return null;
        }
    }
}
