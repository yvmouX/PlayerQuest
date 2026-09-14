package com.playerPlugin.playerTaskX.core.integration;

import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 读取 CustomFishing 注册表里的战利品清单，给编辑器的「鱼 id」选择器用。
 *
 * <h2>为什么这个类要被反射加载</h2>
 * 它 import 了 CustomFishing 的类型，被类加载时就会解析它们——没装 CustomFishing 的
 * 服务端上会抛 {@code NoClassDefFoundError}。因此入口只能是 {@link CustomFishingHook}：
 * 它自己不认识任何 CustomFishing 类型，只在确认插件存在后才反射调用本类。
 *
 * <h2>每次调用都重新读</h2>
 * 不缓存：{@code /cf reload} 之后注册表会被换掉，而素材目录是「人打开编辑器时算一次」的
 * 低频请求，重新读一遍比让人对着过期清单选鱼便宜得多。
 */
final class CustomFishingCatalog {

    private CustomFishingCatalog() {
    }

    /** 已注册的战利品；取不到时返回空表（编辑器退化成手打，不报错）。 */
    static List<FishLoot> loot() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();
        if (plugin == null) {
            return List.of();
        }
        LootManager manager = plugin.getLootManager();
        if (manager == null) {
            return List.of();
        }
        Collection<Loot> registered = manager.getRegisteredLoots();
        if (registered == null || registered.isEmpty()) {
            return List.of();
        }
        List<FishLoot> loot = new ArrayList<>(registered.size());
        for (Loot entry : registered) {
            if (entry == null) {
                continue;
            }
            String id = entry.id();
            if (id == null || id.isBlank()) {
                continue;
            }
            loot.add(new FishLoot(id, nick(entry)));
        }
        return loot;
    }

    /**
     * 战利品的展示名。
     * <p>
     * {@code nick} 是 CustomFishing 配置里写的那份（可能带颜色标签），取不到就退回 id：
     * 一条显示不出来的鱼不该让整份清单失败。
     */
    private static String nick(Loot entry) {
        try {
            return entry.nick();
        } catch (Throwable e) {
            return null;
        }
    }
}
