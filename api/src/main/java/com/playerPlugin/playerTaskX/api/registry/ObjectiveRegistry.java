package com.playerPlugin.playerTaskX.api.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;

import java.util.Collection;
import java.util.Optional;

/** 目标类型注册表：内置类型启动时注册，其它插件也可注册自己的；只管按 id 查。 */
public interface ObjectiveRegistry {

    /** 注册一个目标类型；重复 id 会被拒绝并告警。 */
    void register(ObjectiveType type);

    Optional<ObjectiveType> find(String id);

    /** 是否存在该类型（用于配置校验）。 */
    default boolean contains(String id) {
        return find(id).isPresent();
    }

    /** 全部已注册类型。 */
    Collection<ObjectiveType> all();

    /** 按 id 取显示名，未知类型返回 id 本身，便于报错时展示。 */
    default String displayName(String id) {
        return find(id).map(ObjectiveType::displayName).orElse(id);
    }
}
