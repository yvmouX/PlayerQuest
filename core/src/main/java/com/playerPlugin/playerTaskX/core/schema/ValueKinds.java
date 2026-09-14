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
 * 值域的<b>运行期</b>判定：某个候选值属于哪些 {@link ValueKind}，以及一个配置值能不能命中。
 *
 * <h2>为什么只有这一处</h2>
 * 同一份判定要服务三个地方——素材目录给每个候选打标签（编辑器据此按值域列条目）、
 * 选择器按标签过滤、服务端校验「这个值能不能命中」。三处各写一遍必然漂移，
 * 而漂移的后果正是这个项目最怕的「配了却永远不命中」且无人报警。
 *
 * <h2>成员资格从当前服务端算，不维护清单</h2>
 * 方块与物品看 {@link Material} 的运行期方法，生物看 {@link EntityType#getEntityClass()}
 * 是不是 {@link Animals} / {@link Tameable} / {@link Shearable} 等接口：
 * 新版本新增的内容自动进入对应值域，不需要（也不允许）手写一张会过期的表。
 *
 * <h2>判断不了 ≠ 不满足</h2>
 * {@code Material.isItem()} 与 {@code Enchantment.values()} 这类 API 在服务端启动前会直接
 * 抛异常（它们要读注册表，单元测试环境正是这种情况）。那时结论是 {@link Verdict#UNKNOWN}——
 * <b>放行</b>，而不是报一条「这个值不可能命中」。把「我判断不了」当成「你写错了」，
 * 会让校验在半个环境下满屏误报。
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
    // 条目的值域（编辑器目录用）
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
        if (material.isEdible()) {
            kinds.add(ValueKind.FOOD);
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

    /** MythicMobs 的怪：它们是活体，因此既能当击杀目标也能当交互对象。 */
    public static Set<ValueKind> mythicMob() {
        return EnumSet.of(ValueKind.ENTITY, ValueKind.LIVING);
    }

    /** CustomFishing 的战利品。 */
    public static Set<ValueKind> fish() {
        return EnumSet.of(ValueKind.FISH);
    }

    /** 原版附魔。 */
    public static Set<ValueKind> enchantment() {
        return EnumSet.of(ValueKind.ENCHANTMENT);
    }

    /**
     * 全部原版附魔（编辑器选择器用；名字即 Bukkit 附魔名，如 {@code SHARPNESS}）。
     * <p>
     * 需要服务端注册表，因此启动前返回空表——目录是运行期请求，拿不到就少一栏，
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
     *
     * <p>刻意只说「不可能命中」这一件事：值写得怪但能对上（例如自定义内容 id）时一律放行——
     * 这一层的职责是把「永远不生效的配置」拦下来，不是替管理员做全部判断。
     * 因此以下情形直接放行：
     * <ul>
     *   <li>留空或 {@code *}（表示任意）；</li>
     *   <li>带命名空间的值（{@code itemsadder:…} / {@code craftengine:…} / {@code mythic:…}）——
     *       那是别家插件的 id，离线判断不了，插件缺失时另有专门的提示；</li>
     *   <li>相关值域在这个环境下判断不了（{@link Verdict#UNKNOWN}）。</li>
     * </ul>
     *
     * @param kinds    字段声明的值域（多个之间是「或」）
     * @param rawValue 配置里写的值（可能是逗号分隔的多值）
     * @param fishLoot CustomFishing 已注册的战利品 id；{@code null} 表示无从校验
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
            case BLOCK, PLACEABLE, ITEM, FOOD -> materialVerdict(kind, value);
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
