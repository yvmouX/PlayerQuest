package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标或奖励预设：一组「类型 + 属性」，建任务时可一键套用。
 *
 * <p>与 {@link QuestObjective} / {@link QuestReward} 的关系：预设是它们的<b>模板</b>，
 * 因此属性结构相同（{@code type} + {@code properties}），另加给人看的
 * {@code kind} / {@code id} / {@code name} / {@code description}。
 *
 * <p>类别 {@code kind} 直接在元素上标明，而不是靠 id 命名约定或两个独立仓储推断——
 * 编辑器要分开展示两类预设，让数据自己说清楚是哪一类最省事。
 *
 * <p>预设只有编辑器使用，<b>游戏引擎完全不读它</b>：套用预设时配置已被复制进任务本身。
 * 因此它与任务定义同属「内容」，与玩家数据（状态）分开存储。
 *
 * @param kind        类别，取值见 {@link #OBJECTIVES} / {@link #REWARDS}
 * @param id          预设标识，用于覆盖与删除
 * @param name        显示名，缺失时回退为 type
 * @param type        目标/奖励类型 id
 * @param properties  该类型的配置
 * @param description 说明文字，可为空
 */
public record Preset(String kind, String id, String name, String type,
                     Map<String, Object> properties, String description) {

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

    public static Preset of(String kind, String id, String name, String type,
                            Map<String, Object> properties, String description) {
        return new Preset(kind, id, name, type, properties, description);
    }

    /** 类别规范化：只认 objectives / rewards，其它一律归为 objectives 而不是抛异常。 */
    public static String normalizeKind(String kind) {
        return REWARDS.equalsIgnoreCase(kind) ? REWARDS : OBJECTIVES;
    }

    public boolean isReward() {
        return REWARDS.equals(kind);
    }
}
