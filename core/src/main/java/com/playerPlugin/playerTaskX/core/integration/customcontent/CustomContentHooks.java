package com.playerPlugin.playerTaskX.core.integration.customcontent;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import com.playerPlugin.playerTaskX.core.integration.SoftDependency;

/**
 * 全部已接入的自定义内容来源（ItemsAdder / CraftEngine）：目标写 {@code itemsadder:<id>} / {@code craftengine:<id>}，也可只写裸 id。
 * <p>自定义 id 作为别名随原版材质名一起推，因此现有目标类型的判定与进度记录都不用改。
 */
public final class CustomContentHooks {

    /** 已支持的前缀，顺序即探测顺序。 */
    private static final List<String> PREFIXES = List.of("itemsadder:", "craftengine:");

    /** 前缀 → 对应的软依赖名。 */
    private static final Map<String, String> PLUGINS = Map.of(
            "itemsadder:", "ItemsAdder",
            "craftengine:", "CraftEngine");

    private final List<CustomContentHook> hooks;

    private CustomContentHooks(List<CustomContentHook> hooks) {
        this.hooks = List.copyOf(hooks);
    }

    /**
     * 探测并接入全部可用的来源。
     * <p>
     * 未安装的插件<b>不记日志</b>（这是最常见的情况，不该刷屏）；装了但接不上的记一条 warn，
     * 因为那意味着配置里写了 {@code itemsadder:} 也不会生效。
     */
    public static CustomContentHooks create() {
        List<CustomContentHook> hooks = new ArrayList<>();
        addIfPresent(hooks, "ItemsAdder", ItemsAdderHook::create);
        addIfPresent(hooks, "CraftEngine", CraftEngineHook::create);
        return new CustomContentHooks(hooks);
    }

    /** 空实现：没装任何一家时的状态，测试与「不接入」都用它。 */
    public static CustomContentHooks empty() {
        return new CustomContentHooks(List.of());
    }

    private static void addIfPresent(List<CustomContentHook> hooks, String plugin,
                                     java.util.function.Supplier<CustomContentHook> factory) {
        if (!SoftDependency.isPresent(plugin)) {
            return;
        }
        String version = SoftDependency.versionOf(plugin);
        try {
            CustomContentHook hook = factory.get();
            if (hook != null) {
                hooks.add(hook);
                log(Level.INFO, "已接入 " + plugin + " " + version
                        + "：自定义物品/方块可写 " + hook.prefix() + "<id>（也可只写裸 id）");
            } else {
                log(Level.WARNING, "接入 " + plugin + " " + version
                        + " 失败：API 签名对不上，自定义物品/方块目标将不可用");
            }
        } catch (Throwable e) {
            // 反射解析失败只该损失这一个功能
            log(Level.WARNING, "接入 " + plugin + " " + version + " 时出错，"
                    + "自定义物品/方块目标将不可用: " + e);
        }
    }

    /** 已接入的来源。 */
    public List<CustomContentHook> hooks() {
        return hooks;
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    // ------------------------------------------------------------------
    // 别名
    // ------------------------------------------------------------------

    /**
     * 物品的全部等价标识，形如 {@code [itemsadder:myitems:ruby, myitems:ruby]}。
     * <p>
     * 带前缀与裸 id 各给一份是刻意的：前缀形式不会与别家的同名 id 混淆，
     * 裸 id 形式是「我只想按插件里的 id 写」的常见诉求，两种都能命中同一个物品。
     */
    public List<String> aliases(ItemStack item) {
        return collect(item == null ? null : hook -> hook.itemId(item));
    }

    /** 方块的全部等价标识；见 {@link #aliases(ItemStack)}。 */
    public List<String> aliases(Block block) {
        return collect(block == null ? null : hook -> hook.blockId(block));
    }

    private List<String> collect(java.util.function.Function<CustomContentHook, String> lookup) {
        if (lookup == null) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        for (CustomContentHook hook : hooks) {
            String id;
            try {
                id = lookup.apply(hook);
            } catch (Throwable e) {
                // 一次查询失败不该影响这次动作的其它别名（也不能把事件处理带崩）
                continue;
            }
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!aliases.contains(id)) {
                aliases.add(id);
            }
            String prefixed = hook.prefix() + id;
            if (!aliases.contains(prefixed)) {
                aliases.add(prefixed);
            }
        }
        return aliases;
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 任务里写了自定义内容前缀、但那一家的插件不在（或接不上）时的问题清单，没有则空表。
     * 这类目标永远命中不了，玩家侧只表现为「挖了不涨进度」，因此必须让管理员看到。
     */
    public List<String> problems(Quest quest) {
        if (quest == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        for (QuestObjective objective : quest.objectives()) {
            for (String prefix : usedPrefixes(objective.string("target", ""))) {
                if (available(prefix)) {
                    continue;
                }
                String plugin = PLUGINS.get(prefix);
                problems.add("目标 " + objective.string("target", "") + " 需要 " + plugin + "（当前"
                        + (SoftDependency.isPresent(plugin) ? "接入失败" : "未安装") + "）");
            }
        }
        return problems;
    }

    /** 这个 target 值用到了哪些前缀（可能写在逗号分隔的多值里）。 */
    static List<String> usedPrefixes(String target) {
        if (target == null || target.isBlank()) {
            return List.of();
        }
        List<String> used = new ArrayList<>();
        for (String candidate : target.split(",")) {
            String trimmed = candidate.trim().toLowerCase(Locale.ROOT);
            for (String prefix : PREFIXES) {
                if (trimmed.startsWith(prefix) && !used.contains(prefix)) {
                    used.add(prefix);
                }
            }
        }
        return used;
    }

    private boolean available(String prefix) {
        for (CustomContentHook hook : hooks) {
            if (hook.prefix().equalsIgnoreCase(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void log(Level level, String message) {
        try {
            if (org.bukkit.Bukkit.getLogger() != null) {
                org.bukkit.Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
            }
        } catch (Throwable ignored) {
            // 引导阶段日志出口可能不可用，记不了日志不该影响启动
        }
    }

    /** 供测试构造：直接给一组来源。 */
    public static CustomContentHooks of(CustomContentHook... hooks) {
        return new CustomContentHooks(List.of(hooks));
    }
}
