package com.playerPlugin.playerTaskX.api.schema;

/**
 * 字段取值的「值域」——一个目标字段到底能填哪些东西。
 *
 * <h2>为什么需要它</h2>
 * 光有 {@link FieldType#PICKER} 只知道「这里该弹选择器」，不知道<b>该列什么</b>：
 * 挖掘方块与消耗物品都是「材质」，但前者只能填方块、后者只能填物品。少了这一层，
 * 选择器只能把全部材质端上来（于是「挖 64 个苹果」这种永远不会命中的配置也能点出来），
 * 服务端校验也无从判断一个值是否可能被命中。
 *
 * <h2>值从运行期算出来，不维护清单</h2>
 * 每个值的成员资格都由服务端当前的枚举与接口决定（{@code Material.isBlock()}、
 * {@code EntityType.getEntityClass()} 是不是 {@code Animals}…），因此新版本新增的方块、
 * 生物自动就有——手写允许清单必然随版本失效，这个项目已经删过一份那样的表。
 *
 * <h2>语义是「或」</h2>
 * 一个字段可以声明多个值域，值只要满足<b>其中任意一个</b>就算合法（例如「方块或实体」）。
 * 需要「且」的场合另立一个值域，例如 {@link #PLACEABLE}（既是方块、又能拿在手里）——
 * 与其让调用方去组合，不如把「这确实是一类值」命名出来。
 */
public enum ValueKind {

    /** 方块（能被挖掘/被目标方块匹配）。 */
    BLOCK("方块"),
    /** 可作为物品持有的方块（放到地上用的那些）。 */
    PLACEABLE("可放置的方块"),
    /** 能作为物品存在的材质。 */
    ITEM("物品"),
    /** 能食用的物品。 */
    FOOD("食物"),
    /** 任何实体类型。 */
    ENTITY("实体"),
    /** 活体实体（能被击杀的那些）。 */
    LIVING("活体生物"),
    /** 能繁殖的动物。 */
    BREEDABLE("可繁殖的动物"),
    /** 能驯服的生物。 */
    TAMEABLE("可驯服的生物"),
    /** 能剪毛的生物（羊、蘑菇牛）。 */
    SHEARABLE("可剪毛的生物"),
    /** CustomFishing 的战利品。 */
    FISH("自定义鱼"),
    /** 原版附魔。 */
    ENCHANTMENT("附魔");

    private final String label;

    ValueKind(String label) {
        this.label = label;
    }

    /** 中文显示名，用于校验提示（「APPLE 不是方块」这类话要说得出口）。 */
    public String label() {
        return label;
    }
}
