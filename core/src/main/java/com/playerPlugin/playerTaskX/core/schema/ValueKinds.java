package com.playerPlugin.playerTaskX.core.schema;

import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Shearable;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 值域的<b>运行期</b>判定：某个值属于哪些 {@link ValueKind}、某个配置值能不能命中。
 * 成员资格按当前服务端的枚举与接口算（不维护清单）；判断不了时结论是「放行」而不是「你写错了」。
 */
public final class ValueKinds {

    private ValueKinds() {
    }

    /** 单个值域的判定结果。 */
    private enum Verdict {
        /** 满足该值域。 */
        YES,
        /** 确定不满足。 */
        NO,
        /** 这个环境下判断不了（注册表还没起来等）。 */
        UNKNOWN
    }

    // ------------------------------------------------------------------
    // 条目的值域（按当前服务端算出的候选归属）
    // ------------------------------------------------------------------

    /** 一个材质属于哪些值域（按 {@link ValueKind} 的声明顺序）。 */
    public static Set<ValueKind> of(Material material) {
        Set<ValueKind> kinds = EnumSet.noneOf(ValueKind.class);
        if (material == null) {
            return kinds;
        }
        if (material.isItem()) {
            kinds.add(ValueKind.ITEM);
        }
        if (material.isBlock()) {
            kinds.add(ValueKind.BLOCK);
            // 可放置 = 既是方块又能拿在手里（AIR 之类的纯方块状态放不下去）
            if (material.isItem() && !material.isAir()) {
                kinds.add(ValueKind.PLACEABLE);
            }
        }
        return kinds;
    }

    /** 一个实体类型属于哪些值域。 */
    public static Set<ValueKind> of(EntityType type) {
        Set<ValueKind> kinds = EnumSet.noneOf(ValueKind.class);
        if (type == null) {
            return kinds;
        }
        kinds.add(ValueKind.ENTITY);
        Class<? extends Entity> clazz = entityClass(type);
        if (clazz == null) {
            // 取不到实现类（少数技术实体）时保守处理：连「活体」都不标，
            // 免得把一只盔甲架列进「可击杀生物」里
            return kinds;
        }
        if (LivingEntity.class.isAssignableFrom(clazz)) {
            kinds.add(ValueKind.LIVING);
        }
        if (Animals.class.isAssignableFrom(clazz)) {
            kinds.add(ValueKind.BREEDABLE);
        }
        if (Tameable.class.isAssignableFrom(clazz)) {
            kinds.add(ValueKind.TAMEABLE);
        }
        if (Shearable.class.isAssignableFrom(clazz)) {
            kinds.add(ValueKind.SHEARABLE);
        }
        return kinds;
    }

    /**
     * 全部原版附魔（名字即 Bukkit 附魔名，如 {@code SHARPNESS}）。
     * <p>
     * 需要服务端注册表，因此启动前返回空表——拿不到就当没有这份清单，
     * 不该把插件拖垮。
     */
    public static List<Enchantment> enchantments() {
        try {
            return List.of(Enchantment.values());
        } catch (Throwable e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 一个配置值在给定值域下是否<b>可能</b>命中；不可能时返回给管理员看的原因，否则返回 {@code null}。
     * 只说「不可能命中」这一件事：留空、{@code *}、带命名空间的别家 id、以及这个环境下判断不了的值一律放行。
     */
    public static String check(List<ValueKind> kinds, String rawValue, List<String> fishLoot) {
        if (kinds == null || kinds.isEmpty()) {
            return null;
        }
        String target = rawValue == null ? "" : rawValue.trim();
        if (target.isEmpty() || "*".equals(target)) {
            return null;
        }
        for (String candidate : target.split(",")) {
            String value = candidate.trim();
            if (value.isEmpty() || "*".equals(value) || isForeignId(value)) {
                continue;
            }
            if (!matches(kinds, value, fishLoot)) {
                return "目标 " + value + " 不是「" + labels(kinds) + "」，这个目标永远不会命中";
            }
        }
        return null;
    }

    /** 别家插件的 id 或带命名空间的值：离线判断不了，一律放行。 */
    private static boolean isForeignId(String value) {
        return value.indexOf(':') >= 0;
    }

    /** 满足任意一个值域即算通过；全都不满足、但有一个判断不了时也放行。 */
    private static boolean matches(List<ValueKind> kinds, String value, List<String> fishLoot) {
        boolean unknown = false;
        for (ValueKind kind : kinds) {
            switch (verdict(kind, value, fishLoot)) {
                case YES -> {
                    return true;
                }
                case UNKNOWN -> unknown = true;
                case NO -> {
                    // 继续看下一个值域
                }
            }
        }
        return unknown;
    }

    /** 单个值域的判定。 */
    private static Verdict verdict(ValueKind kind, String value, List<String> fishLoot) {
        return switch (kind) {
            case FISH -> fishLoot == null ? Verdict.UNKNOWN
                    : containsIgnoreCase(fishLoot, value) ? Verdict.YES : Verdict.NO;
            case ENCHANTMENT -> enchantmentVerdict(value);
            case ENTITY, LIVING, BREEDABLE, TAMEABLE, SHEARABLE -> entityVerdict(kind, value);
            case BLOCK, PLACEABLE, ITEM -> materialVerdict(kind, value);
        };
    }

    private static Verdict enchantmentVerdict(String value) {
        List<Enchantment> all = enchantments();
        if (all.isEmpty()) {
            // 注册表还没起来：判断不了就放行
            return Verdict.UNKNOWN;
        }
        String wanted = value.toUpperCase(Locale.ROOT);
        for (Enchantment enchantment : all) {
            if (enchantment.getKey().getKey().equalsIgnoreCase(wanted)
                    || enchantment.getKey().toString().equalsIgnoreCase(wanted)) {
                return Verdict.YES;
            }
        }
        return Verdict.NO;
    }

    private static Verdict entityVerdict(ValueKind kind, String value) {
        EntityType type;
        try {
            type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 不是实体名：这个值域不满足，但别的值域（方块/物品）可能仍然成立
            return Verdict.NO;
        }
        return of(type).contains(kind) ? Verdict.YES : Verdict.NO;
    }

    private static Verdict materialVerdict(ValueKind kind, String value) {
        Material material;
        try {
            material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        } catch (Throwable e) {
            // 材质注册表不可用（单元测试、插件引导阶段）：判断不了就放行
            return Verdict.UNKNOWN;
        }
        if (material == null) {
            // 认不出来：可能压根不是材质名，也可能别家的 id 恰好长这样——交给其它值域判
            return Verdict.NO;
        }
        try {
            return of(material).contains(kind) ? Verdict.YES : Verdict.NO;
        } catch (Throwable e) {
            return Verdict.UNKNOWN;
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** 值域的一串显示名，用于提示文案（「方块」「方块或实体」）。 */
    private static String labels(List<ValueKind> kinds) {
        List<String> labels = new ArrayList<>(kinds.size());
        for (ValueKind kind : kinds) {
            labels.add(kind.label());
        }
        return String.join("或", labels);
    }

    /** {@link EntityType#getEntityClass()} 在少数类型上会抛异常，这里统一吞掉。 */
    private static Class<? extends Entity> entityClass(EntityType type) {
        try {
            return type.getEntityClass();
        } catch (Throwable e) {
            return null;
        }
    }
}
