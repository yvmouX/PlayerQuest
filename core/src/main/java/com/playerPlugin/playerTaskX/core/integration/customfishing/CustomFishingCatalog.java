package com.playerPlugin.playerTaskX.core.integration.customfishing;

import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 读取 CustomFishing 注册表里的战利品清单，校验鱼 id 时用。
 * <p>import 了 CustomFishing 的类型，只能由 {@link CustomFishingHook} 反射加载；每次调用都重读，因为 {@code /cf reload} 会换掉注册表。
 */
final class CustomFishingCatalog {

    private CustomFishingCatalog() {
    }

    /** 已注册的战利品；取不到时返回空表（校验退化为放行，不报错）。 */
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
