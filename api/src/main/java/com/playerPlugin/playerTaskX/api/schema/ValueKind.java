package com.playerPlugin.playerTaskX.api.schema;

/**
 * 字段取值的「值域」——这个字段能填哪一类值，成员资格由服务端当前的枚举与接口算出来（不维护清单）。
 * 多个值域之间是「或」；每个常量都必须有字段声明它，否则判定里那条分支永远走不到。
 */
public enum ValueKind {

    /** 方块（能被挖掘/被目标方块匹配）。 */
    BLOCK("方块"),

    /** 可放置的方块（既是方块、又有物品形态，能拿在手里放下）。 */
    PLACEABLE("可放置的方块"),

    /** 物品（材质里有物品形态的那些）。 */
    ITEM("物品"),

    /** 实体类型（含箭、船这类非生物）。 */
    ENTITY("实体"),

    /** 活体生物（能被击杀的那种）。 */
    LIVING("生物"),

    /** 能繁殖的动物。 */
    BREEDABLE("可繁殖的动物"),

    /** 能驯服的生物。 */
    TAMEABLE("可驯服的生物"),

    /** 能剪毛的生物。 */
    SHEARABLE("可剪毛的生物"),

    /** CustomFishing 的战利品 id。 */
    FISH("自定义鱼"),

    /** 原版附魔名。 */
    ENCHANTMENT("附魔");

    private final String label;

    ValueKind(String label) {
        this.label = label;
    }

    /** 提示文案里的显示名（「方块或实体」由调用方拼接）。 */
    public String label() {
        return label;
    }
}
