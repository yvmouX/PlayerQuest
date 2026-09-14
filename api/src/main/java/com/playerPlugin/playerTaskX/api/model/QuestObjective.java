package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务目标：一份「配置数据」，行为由 {@link com.playerPlugin.playerTaskX.api.objective.ObjectiveType} 提供。
 * <p>
 * 参数统一放 {@code properties} 而不是为每种目标建子类：配置、数据库、网页编辑器、GUI
 * 四处都只需要一份通用实现；每种目标用 {@code schema()} 自描述字段，表单可自动生成。
 *
 * <h2>引用预设时的两个 map</h2>
 * {@code objectives: [{preset: mine-stone}]} 这种写法会让本记录同时带两份配置，缺一不可：
 * <ul>
 *   <li>{@code properties} = <b>生效值</b>（预设给的字段），引擎按它判定；</li>
 *   <li>{@code authored} = <b>作者写的那份</b>（就是 {@code {preset: id}}），落库/导出按它写回。</li>
 * </ul>
 * 只留生效值会让「改预设 → 引用它的任务跟着变」失效（下次保存就把预设的值写死了），
 * 只留作者那份则引擎还得自己去查预设。没引用预设时两者是同一个 map。
 * <p>
 * 引用条目上<b>不</b>再接受其它字段：字段全部由预设提供，多写的键不生效（会报校验问题）。
 * 同一个字段有两个来源时「这个任务实际在做什么」需要心算，界面也说不清哪些字段被改过；
 * 要偏离预设就先展开成独立配置（见 {@code PresetRefs}）。
 *
 * @param type       目标类型 id，如 {@code break_block}
 * @param properties 生效配置
 * @param authored   作者写的配置（含 {@code preset} 键；未引用预设时等于 {@code properties}）
 */
public record QuestObjective(String type, Map<String, Object> properties, Map<String, Object> authored) {

    /** 预设引用的保留键：出现在 {@link #authored()} 里就表示这个目标引用了预设。 */
    public static final String PRESET_KEY = "preset";

    public QuestObjective {
        properties = copy(properties);
        authored = authored == null ? properties : copy(authored);
    }

    /** 直接写配置（不引用预设）的目标。 */
    public QuestObjective(String type, Map<String, Object> properties) {
        this(type, properties, properties);
    }

    public static QuestObjective of(String type, Map<String, Object> properties) {
        return new QuestObjective(type, properties);
    }

    /** 引用了哪个预设；没引用返回 {@code null}。 */
    public String presetId() {
        Object value = authored.get(PRESET_KEY);
        if (value == null) {
            return null;
        }
        String id = String.valueOf(value).trim();
        return id.isEmpty() ? null : id;
    }

    /** 取字符串配置，缺失返回默认值。 */
    public String string(String key, String fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 取整数配置，缺失或非法返回默认值。 */
    public int integer(String key, int fallback) {
        Object value = properties.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 目标所需数量，统一约定 {@code amount} 键，默认 1。 */
    public int amount() {
        return Math.max(1, integer("amount", 1));
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
