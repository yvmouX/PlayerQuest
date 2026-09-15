package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.integration.customfishing.CustomFishingHook;
import com.playerPlugin.playerTaskX.core.integration.customfishing.FishLoot;
import com.playerPlugin.playerTaskX.core.schema.ValueKinds;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 候选值清单：按值域把原版材质/实体/附魔/鱼摊成「写进配置的值 + 图标」，供游戏内选择器渲染。
 * 别家插件的自定义 id（ItemsAdder / CraftEngine / MythicMobs）这一轮不列，只能靠聊天栏手打（写法在字段 hint 里）；要列的话给 CustomContentHook 加一份 id 清单（反射调那两家的注册表）再接进来即可。
 */
public final class CandidateCatalog {

    private CandidateCatalog() {
    }

    /** 一条候选项：写进配置的值 + 图标 + 备注（枚举名或来源）。 */
    public record Candidate(String value, ItemStack icon, String note) {
    }

    /** 按值域列候选：kinds 为空返回空表；顺序稳定、按 value 去重；这个环境里取不到的清单跳过，绝不抛异常。 */
    public static List<Candidate> of(List<ValueKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return List.of();
        }
        // LinkedHashMap：既保持清单自身顺序，也保证「多值域时先出现者胜」
        Map<String, Candidate> byValue = new LinkedHashMap<>();
        for (ValueKind kind : kinds) {
            if (kind == null) {
                continue;
            }
            try {
                for (Candidate candidate : expand(kind)) {
                    if (candidate.value() != null && !candidate.value().isBlank()) {
                        byValue.putIfAbsent(candidate.value(), candidate);
                    }
                }
            } catch (Throwable ignored) {
                // 整个值域的清单取不到（注册表没起来的单测/引导阶段、插件没装）：少列这一个值域，不影响其它值域
            }
        }
        return List.copyOf(byValue.values());
    }

    /** 一个值域展开成候选；取不到清单时返回空表。 */
    private static List<Candidate> expand(ValueKind kind) {
        return switch (kind) {
            case ITEM, BLOCK, PLACEABLE -> materials(kind);
            case ENTITY, LIVING, BREEDABLE, TAMEABLE, SHEARABLE -> entities(kind);
            case ENCHANTMENT -> enchantments();
            case FISH -> fish();
        };
    }

    // ---------- 各值域的清单 ----------

    private static List<Candidate> materials(ValueKind kind) {
        List<Candidate> candidates = new ArrayList<>();
        for (Material material : Material.values()) {
            try {
                // LEGACY_* 只是旧存档的兼容条目，却能解析出物品/方块形态，不挡就会往清单里混进几百条
                if (material.isAir() || material.isLegacy() || !ValueKinds.of(material).contains(kind)) {
                    continue;
                }
                candidates.add(new Candidate(material.name(), icon(material, material.name()), material.name()));
            } catch (Throwable ignored) {
                // 单个材质读不出属性（legacy 条目、注册表不可用）就跳过它，别让一条拖垮整张清单
            }
        }
        return candidates;
    }

    private static List<Candidate> entities(ValueKind kind) {
        List<Candidate> candidates = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            try {
                // UNKNOWN 不是真实体，写进配置也永远命中不了
                if (type == EntityType.UNKNOWN || !ValueKinds.of(type).contains(kind)) {
                    continue;
                }
                candidates.add(new Candidate(type.name(), icon(spawnEgg(type), type.name()), type.name()));
            } catch (Throwable ignored) {
                // 见 materials
            }
        }
        return candidates;
    }

    /** 刷怪蛋最像那只生物；箭、船这类没有刷怪蛋的实体退回纸。 */
    private static Material spawnEgg(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg == null ? Material.PAPER : egg;
    }

    private static List<Candidate> enchantments() {
        List<Candidate> candidates = new ArrayList<>();
        for (Enchantment enchantment : ValueKinds.enchantments()) {
            try {
                // 值给不带命名空间的短名（schema 提示里就是这么写的），备注给完整 key 便于对照
                String shortName = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
                String key = enchantment.getKey().toString();
                candidates.add(new Candidate(shortName, icon(Material.ENCHANTED_BOOK, key), key));
            } catch (Throwable ignored) {
                // 见 materials
            }
        }
        return candidates;
    }

    private static List<Candidate> fish() {
        if (!CustomFishingHook.supported()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (FishLoot loot : CustomFishingHook.loot()) {
            try {
                String note = loot.name() == null ? loot.id() : loot.name();
                candidates.add(new Candidate(loot.id(), icon(Material.TROPICAL_FISH, note), note));
            } catch (Throwable ignored) {
                // 见 materials
            }
        }
        return candidates;
    }

    /** 图标只把备注写进 lore，不设 displayName：客户端会自己渲染本地化后的原版名字，比我们维护一份名字表好看。 */
    private static ItemStack icon(Material material, String note) {
        ItemStack stack = new ItemStack(material);
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(Texts.render("&7" + note)));
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
            // 物品元数据工厂要服务端就绪才有（单测/引导阶段取不到）：退回裸图标，胜过丢掉这个候选项
        }
        return stack;
    }
}
