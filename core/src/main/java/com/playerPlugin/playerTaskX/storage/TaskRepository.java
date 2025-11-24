package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    /**
     * 从目录加载所有已定义任务，返回一个List<TaskDefinition>
     * <p>
     *     每个任务文件为一个 YAML 文件，文件名即任务 ID。
     *     每个任务文件的内容为一个 TaskDefinition 对象，序列化格式为 YAML。
     *     如果任务目录不存在，会自动创建。
     * </p>
     *
     * @return {@link List }<{@link TaskDefinition }>
     */
    List<TaskDefinition> loadAll();

    /**
     * 通过任务ID查找对应的已定义任务
     * <p>
     *     如果任务不存在，返回 Optional.empty()。
     * </p>
     *
     * @param id 任务ID
     * @return {@link Optional }<{@link TaskDefinition }>
     */
    Optional<TaskDefinition> findById(String id);

    /**
     * 保存已经定义任务到任务目录下
     *
     * @param taskDefinition TaskDefinition 已定义任务
     */
    void save(TaskDefinition taskDefinition);

    /**
     * 删除指定任务ID的任务文件
     *
     * @param id 任务ID
     */
    void delete(String id);
}
