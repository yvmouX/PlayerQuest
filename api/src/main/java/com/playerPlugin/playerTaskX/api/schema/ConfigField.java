package com.playerPlugin.playerTaskX.api.schema;

import java.util.List;

/**
 * 目标/奖励类型的单个配置字段描述。
 * <p>
 * 这是「新增一种类型只写一个类」的关键：类型自描述字段，
 * 网页编辑器与游戏内 GUI 据此<b>自动生成表单</b>，无需为新类型改任何界面代码。
 *
 * @param key         配置键，对应 {@code properties} 中的键名
 * @param label       显示名
 * @param type        输入类型
 * @param required    是否必填
 * @param defaultValue 默认值（生成新配置时写入）
 * @param options     {@link FieldType#ENUM} 的候选项
 * @param hint        帮助文本，说明该字段接受什么取值
 */
public record ConfigField(
        String key,
        String label,
        FieldType type,
        boolean required,
        Object defaultValue,
        List<String> options,
        String hint
) {

    public ConfigField {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ConfigField text(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.STRING, true, defaultValue, List.of(), hint);
    }

    /** 允许留空的文本字段（例如「关键词留空表示任意发言」）。 */
    public static ConfigField optionalText(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.STRING, false, defaultValue, List.of(), hint);
    }

    public static ConfigField integer(String key, String label, int defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.INTEGER, true, defaultValue, List.of(), hint);
    }

    public static ConfigField decimal(String key, String label, double defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.DECIMAL, true, defaultValue, List.of(), hint);
    }

    /** 允许留空的小数字段；留空按 {@code defaultValue} 处理（例如「0 表示不限」）。 */
    public static ConfigField optionalDecimal(String key, String label, double defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.DECIMAL, false, defaultValue, List.of(), hint);
    }

    public static ConfigField bool(String key, String label, boolean defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.BOOLEAN, true, defaultValue, List.of(), hint);
    }

    public static ConfigField material(String key, String label, String defaultValue) {
        return new ConfigField(key, label, FieldType.MATERIAL, true, defaultValue, List.of(),
                "Bukkit 材质名，如 DIAMOND_ORE");
    }

    public static ConfigField entity(String key, String label, String defaultValue) {
        return new ConfigField(key, label, FieldType.ENTITY, true, defaultValue, List.of(),
                "实体类型名，如 ZOMBIE；留空或 * 表示任意");
    }

    /** 方块或实体皆可的目标（例如右键交互的对象）。 */
    public static ConfigField blockOrEntity(String key, String label, String defaultValue) {
        return new ConfigField(key, label, FieldType.TARGET, true, defaultValue, List.of(),
                "方块或实体类型名，如 CHEST、VILLAGER；留空或 * 表示任意");
    }

    /**
     * 允许留空的材质字段。
     * <p>
     * {@code MATERIAL} / {@code ENTITY} / {@code TARGET} 的默认工厂方法都标记为必填，
     * 因为它们通常确实需要一个具体目标；但「钓上任意鱼」「击杀任意生物」这类语义
     * 要求字段可以留空。此时必须用这些 {@code optionalXxx} 变体创建，
     * 编辑器才能如实显示「可留空」，而不是靠猜 hint 文案。
     */
    public static ConfigField optionalMaterial(String key, String label, String defaultValue) {
        return new ConfigField(key, label, FieldType.MATERIAL, false, defaultValue, List.of(),
                "Bukkit 材质名，如 COD；留空或 * 表示任意");
    }

    /** 允许留空的实体字段，用于「任意生物」这类目标。 */
    public static ConfigField optionalEntity(String key, String label, String defaultValue) {
        return optionalEntity(key, label, defaultValue,
                "实体类型名，如 COW；留空或 * 表示任意");
    }

    /**
     * 允许留空的实体字段，带自定义说明。
     * <p>
     * 说明文本会被编辑器与 GUI 原样展示给配置者，因此「这个字段还能接受什么写法」
     * 只能写在这里——写进目标类型的实现里没人看得到。
     */
    public static ConfigField optionalEntity(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.ENTITY, false, defaultValue, List.of(), hint);
    }

    /** 允许留空的方块或实体字段。 */
    public static ConfigField optionalBlockOrEntity(String key, String label, String defaultValue) {
        return new ConfigField(key, label, FieldType.TARGET, false, defaultValue, List.of(),
                "方块或实体类型名，如 CHEST、VILLAGER；留空或 * 表示任意");
    }

    /**
     * 允许留空的 CustomFishing 战利品字段。
     * <p>
     * 值就是 CustomFishing 配置里那条战利品的 id（不带任何前缀——监听器从
     * {@code ContextKeys.ID} 拿到的就是它）。编辑器会把 CustomFishing 注册表里的
     * id 列出来供选择，但输入框照旧可手打：没装 CustomFishing 的服务器上写下的值
     * 在装上之后就能生效，不该因为「当时列不出来」而被拦住。
     */
    public static ConfigField optionalFish(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.FISH, false, defaultValue, List.of(), hint);
    }

    public static ConfigField options(String key, String label, String defaultValue, List<String> options, String hint) {
        return new ConfigField(key, label, FieldType.ENUM, true, defaultValue, options, hint);
    }

    /** 目标数量字段，所有目标类型共用，因此单独提供。 */
    public static ConfigField amount(int defaultValue) {
        return new ConfigField("amount", "所需数量", FieldType.INTEGER, true, defaultValue, List.of(),
                "完成该目标需要的次数");
    }
}
