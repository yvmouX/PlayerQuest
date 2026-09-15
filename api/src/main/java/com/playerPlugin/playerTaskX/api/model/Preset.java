package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标 / 奖励预设：一组「类型 + 属性」，任务里用 {@code preset: <id>} 引用，载入/保存时由 {@code PresetRefs} 展开。
 * 没有显示名与说明——id 就是它的身份（文件来源时取文件名），游戏内编辑器的预设选择器也是按 id 列的。
 */
public record Preset(String kind, String id, String type, Map<String, Object> properties) {

    /** 目标预设类别。 */
    public static final String OBJECTIVES = "objectives";
    /** 奖励预设类别。 */
    public static final String REWARDS = "rewards";

    public Preset {
        kind = normalizeKind(kind);
        properties = properties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /** 类别规范化：只认 objectives / rewards，其它一律归为 objectives 而不是抛异常。 */
    public static String normalizeKind(String kind) {
        return REWARDS.equalsIgnoreCase(kind) ? REWARDS : OBJECTIVES;
    }

    public boolean isReward() {
        return REWARDS.equals(kind);
    }
}
