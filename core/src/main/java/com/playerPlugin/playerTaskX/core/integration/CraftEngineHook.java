package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CraftEngine 接入点（反射，不依赖它的构件）。
 *
 * <h2>用的是哪几个方法</h2>
 * <ul>
 *   <li>物品：{@code CraftEngineItems.byItemStack(ItemStack)} 或 {@code getCustomItemId(ItemStack)}
 *       ——后者直接给 {@code Key}，{@code Key.toString()} 就是 {@code namespace:path}；</li>
 *   <li>方块：{@code CraftEngineBlocks.isCustomBlock(Block)} +
 *       {@code getCustomBlockState(Block)} → {@code ImmutableBlockState.owner().value().id()}；</li>
 *   <li>目录：{@code CraftEngineItems.loadedItems()} / {@code CraftEngineBlocks.loadedBlocks()}，
 *       键就是 id。</li>
 * </ul>
 * 与方法绑定时一次解析、之后只 invoke：这两条查询在「挖一个方块」的热路径上。
 *
 * <p>CraftEngine 的自定义方块在服务端仍是原版方块（靠方块状态 + 资源包呈现），
 * 因此它的方块破坏/放置照常触发 Bukkit 的 {@code BlockBreakEvent} / {@code BlockPlaceEvent}，
 * 我们不必监听它自己的事件——只需要在那些事件里多问一句「这块是不是自定义方块」。
 */
final class CraftEngineHook implements CustomContentHook {

    private static final String PLUGIN = "CraftEngine";
    private static final String PREFIX = "craftengine:";

    private final Method itemIdLookup;
    private final Method itemByStack;
    private final Method blockIsCustom;
    private final Method blockStateLookup;
    private final Method blockOwnerOfState;
    private final Method loadedItems;
    private final Method loadedBlocks;

    private CraftEngineHook(Method itemIdLookup, Method itemByStack, Method blockIsCustom,
                            Method blockStateLookup, Method blockOwnerOfState,
                            Method loadedItems, Method loadedBlocks) {
        this.itemIdLookup = itemIdLookup;
        this.itemByStack = itemByStack;
        this.blockIsCustom = blockIsCustom;
        this.blockStateLookup = blockStateLookup;
        this.blockOwnerOfState = blockOwnerOfState;
        this.loadedItems = loadedItems;
        this.loadedBlocks = loadedBlocks;
    }

    @Nullable
    static CraftEngineHook create() {
        Class<?> items = findClass("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
        Class<?> blocks = findClass("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
        if (items == null || blocks == null) {
            return null;
        }

        // 物品 id：优先 getCustomItemId（直接返回 Key），退回 byItemStack + ItemDefinition.id()
        Method itemIdLookup = method(items, "getCustomItemId", ItemStack.class);
        Method itemByStack = method(items, "byItemStack", ItemStack.class);
        if (itemIdLookup == null && itemByStack == null) {
            return null;
        }

        // 方块 id：isCustomBlock 先挡一道，再 getCustomBlockState → owner().value().id()
        Method blockIsCustom = method(blocks, "isCustomBlock", Block.class);
        Method blockStateLookup = method(blocks, "getCustomBlockState", Block.class);
        Method blockOwnerOfState = blockStateLookup == null
                ? null
                : method(blockStateLookup.getReturnType(), "owner");
        if (blockStateLookup == null || blockOwnerOfState == null) {
            // 取不到方块状态时仍然保留物品能力：能接多少接多少，别整家放弃
            blockStateLookup = null;
        }

        return new CraftEngineHook(itemIdLookup, itemByStack, blockIsCustom, blockStateLookup,
                blockOwnerOfState, method(items, "loadedItems"), method(blocks, "loadedBlocks"));
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

    @Override
    public List<String> itemIds() {
        return keys(loadedItems);
    }

    @Override
    public List<String> blockIds() {
        return keys(loadedBlocks);
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

    /** {@code loadedItems()} / {@code loadedBlocks()} 返回 Map，键就是 id。 */
    private static List<String> keys(Method method) {
        if (method == null) {
            return List.of();
        }
        try {
            Object result = method.invoke(null);
            if (result instanceof Map<?, ?> map) {
                List<String> ids = new ArrayList<>(map.size());
                for (Object key : map.keySet()) {
                    if (key != null) {
                        ids.add(String.valueOf(key));
                    }
                }
                return ids;
            }
        } catch (Throwable e) {
            return List.of();
        }
        return List.of();
    }

    @Nullable
    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }
}
