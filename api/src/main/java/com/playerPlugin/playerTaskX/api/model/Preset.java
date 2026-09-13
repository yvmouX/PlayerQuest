package com.playerPlugin.playerTaskX.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标或奖励预设：一组「类型 + 属性」，建任务时可一键套用。
 *
 * <p>与 {@link QuestObjective} / {@link QuestReward} 的关系：预设是它们的<b>模板</b>，
 * 因此字段结构相同（{@code type} + {@code properties}），另加给人看的 {@code id} /
 * {@code name} / {@code description}。
 *
 * <p>预设只有编辑器使用，<b>游戏引擎完全不读它</b>——套用预设时配置已被复制进
 * 任务本身。因此它与任务定义同属「内容」，与玩家数据（状态）分开存储。
 *
 * @param id          预设标识，用于覆盖与删除
 * @param name        显示名，缺失时回退为 type
 * @param type        目标/奖励类型 id
 * @param properties  该类型的配置
 * @param description 说明文字，可为空
 */
public record Preset(String id, String name, String type,
                     Map<String, Object> properties, String description) {

    public Preset {
        properties = properties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static Preset of(String id, String name, String type,
                            Map<String, Object> properties, String description) {
        return new Preset(id, name, type, properties, description);
    }
}
