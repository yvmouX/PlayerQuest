package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.Trigger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 目标类型注册表。内置类型在插件启动时注册，其它插件也可注册自定义类型。
 */
public interface ObjectiveRegistry {

    /** 注册一个目标类型；重复 id 会被拒绝并告警。 */
    void register(ObjectiveType type);

    Optional<ObjectiveType> find(String id);

    /** 是否存在该类型（用于编辑器校验配置）。 */
    default boolean contains(String id) {
        return find(id).isPresent();
    }

    /** 全部已注册类型。 */
    Collection<ObjectiveType> all();

    /** 按 id 取显示名，未知类型返回 id 本身，便于报错时展示。 */
    default String displayName(String id) {
        return find(id).map(ObjectiveType::displayName).orElse(id);
    }

    /** 响应指定动作的类型，引擎的分发入口。 */
    List<ObjectiveType> byTrigger(Trigger trigger);
}
