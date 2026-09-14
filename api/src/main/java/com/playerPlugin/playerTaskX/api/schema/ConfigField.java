package com.playerPlugin.playerTaskX.api.schema;

import java.util.List;

/**
 * 目标/奖励类型的单个配置字段描述。
 * <p>
 * 这是「新增一种类型只写一个类」的关键：类型自描述字段，
 * 网页编辑器与游戏内 GUI 据此<b>自动生成表单</b>，无需为新类型改任何界面代码。
 *
 * <p>{@link #type()} 说明<b>渲染成什么控件</b>，{@link #kinds()} 说明<b>这个控件里能填什么值</b>
 * （见 {@link ValueKind}）。两者刻意分开：加一个新值域（「只有可剪毛的生物」）不需要动前端，
 * 而换一种控件（下拉 → 选择器）也不影响值域的定义。
 *
 * @param key          配置键，对应 {@code properties} 中的键名
 * @param label        显示名
 * @param type         控件类型
 * @param required     是否必填
 * @param defaultValue 默认值（生成新配置时写入）
 * @param options      {@link FieldType#ENUM} 的候选项
 * @param hint         帮助文本，说明该字段接受什么取值
 * @param kinds        {@link FieldType#PICKER} 的取值域（多个之间是「或」）；其它控件为空表
 */
public record ConfigField(
        String key,
        String label,
        FieldType type,
        boolean required,
        Object defaultValue,
        List<String> options,
        String hint,
        List<ValueKind> kinds
) {

    public ConfigField {
        options = options == null ? List.of() : List.copyOf(options);
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
    }

    public static ConfigField text(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.STRING, true, defaultValue, List.of(), hint, List.of());
    }

    /** 允许留空的文本字段（例如「关键词留空表示任意发言」）。 */
    public static ConfigField optionalText(String key, String label, String defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.STRING, false, defaultValue, List.of(), hint, List.of());
    }

    public static ConfigField integer(String key, String label, int defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.INTEGER, true, defaultValue, List.of(), hint, List.of());
    }

    public static ConfigField decimal(String key, String label, double defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.DECIMAL, true, defaultValue, List.of(), hint, List.of());
    }

    /** 允许留空的小数字段；留空按 {@code defaultValue} 处理（例如「0 表示不限」）。 */
    public static ConfigField optionalDecimal(String key, String label, double defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.DECIMAL, false, defaultValue, List.of(), hint, List.of());
    }

    public static ConfigField bool(String key, String label, boolean defaultValue, String hint) {
        return new ConfigField(key, label, FieldType.BOOLEAN, true, defaultValue, List.of(), hint, List.of());
    }

    /**
     * 选择器字段。
     *
     * @param required 是否必填；「任意」这类语义必须传 false，界面据此显示可留空
     * @param kinds    取值域，多个之间是「或」
     */
    public static ConfigField picker(String key, String label, String defaultValue, boolean required,
                                     String hint, ValueKind... kinds) {
        return new ConfigField(key, label, FieldType.PICKER, required, defaultValue, List.of(), hint,
                List.of(kinds));
    }

    /** 必填的方块名（挖掘/放置这类只有一个明确对象的目标）。 */
    public static ConfigField blocks(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, true, "方块名，如 DIAMOND_ORE", ValueKind.BLOCK);
    }

    /** 允许留空的方块名（留空或 {@code *} 表示任意方块）。 */
    public static ConfigField optionalBlocks(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, false, "方块名，如 DIAMOND_ORE；留空或 * 表示任意方块",
                ValueKind.BLOCK);
    }

    /** 必填的物品名。 */
    public static ConfigField items(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, true, "物品名，如 DIAMOND", ValueKind.ITEM);
    }

    /** 允许留空的物品名（例如「钓上任意东西」）。 */
    public static ConfigField optionalItems(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, false, "物品名，如 COD；留空或 * 表示任意物品", ValueKind.ITEM);
    }

    /**
     * 允许留空的实体字段，带自定义说明。
     * <p>
     * 说明文本会被编辑器与 GUI 原样展示给配置者，因此「这个字段还能接受什么写法」
     * 只能写在这里——写进目标类型的实现里没人看得到。
     */
    public static ConfigField optionalEntities(String key, String label, String defaultValue, String hint,
                                               ValueKind... kinds) {
        ValueKind[] domain = kinds == null || kinds.length == 0 ? new ValueKind[]{ValueKind.ENTITY} : kinds;
        return picker(key, label, defaultValue, false, hint, domain);
    }

    /** 方块或实体皆可的目标（例如右键交互的对象）。 */
    public static ConfigField blockOrEntity(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, true, "方块或实体类型名，如 CHEST、VILLAGER；留空或 * 表示任意",
                ValueKind.BLOCK, ValueKind.ENTITY);
    }

    /** 允许留空的方块或实体字段。 */
    public static ConfigField optionalBlockOrEntity(String key, String label, String defaultValue) {
        return picker(key, label, defaultValue, false, "方块或实体类型名，如 CHEST、VILLAGER；留空或 * 表示任意",
                ValueKind.BLOCK, ValueKind.ENTITY);
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
        return picker(key, label, defaultValue, false, hint, ValueKind.FISH);
    }

    /** 附魔名。 */
    public static ConfigField optionalEnchantments(String key, String label, String defaultValue, String hint) {
        return picker(key, label, defaultValue, false, hint, ValueKind.ENCHANTMENT);
    }

    public static ConfigField options(String key, String label, String defaultValue, List<String> options, String hint) {
        return new ConfigField(key, label, FieldType.ENUM, true, defaultValue, options, hint, List.of());
    }

    /** 目标数量字段，所有目标类型共用，因此单独提供。 */
    public static ConfigField amount(int defaultValue) {
        return new ConfigField("amount", "所需数量", FieldType.INTEGER, true, defaultValue, List.of(),
                "完成该目标需要的次数", List.of());
    }
}
