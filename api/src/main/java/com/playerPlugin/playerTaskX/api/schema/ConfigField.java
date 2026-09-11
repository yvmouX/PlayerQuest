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

    public static ConfigField integer(String key, String label, int defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.INTEGER, true, defaultValue, List.of(), hint);
    }

    public static ConfigField decimal(String key, String label, double defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.DECIMAL, true, defaultValue, List.of(), hint);
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

    public static ConfigField options(String key, String label, String defaultValue, List<String> options, String hint) {
        return new ConfigField(key, label, FieldType.ENUM, true, defaultValue, options, hint);
    }

    /** 目标数量字段，所有目标类型共用，因此单独提供。 */
    public static ConfigField amount(int defaultValue) {
        return new ConfigField("amount", "所需数量", FieldType.INTEGER, true, defaultValue, List.of(),
                "完成该目标需要的次数");
    }
}
