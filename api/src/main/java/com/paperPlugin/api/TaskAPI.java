package com.paperPlugin.api;

import com.paperPlugin.api.model.TaskDefinitionDTO;

import java.util.List;

public interface TaskAPI {
    List<TaskDefinitionDTO> listTasks();
    void createTask(TaskDefinitionDTO dto);
    void handleCoreEvent(Object coreEvent);
    void reload();

}
