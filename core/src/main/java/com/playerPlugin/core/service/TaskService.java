package com.playerPlugin.core.service;

import com.paperPlugin.api.TaskAPI;
import com.paperPlugin.api.model.TaskDefinitionDTO;
import com.playerPlugin.core.repository.TaskRepository;
import com.playerPlugin.core.usecase.CreateTaskUseCase;
import com.playerPlugin.core.usecase.IssueRewardUseCase;
import com.playerPlugin.core.usecase.UpdateProgressUseCase;
import com.playerPlugin.core.utils.DomainMapper;

import java.util.List;
import java.util.stream.Collectors;

public class TaskService implements TaskAPI {
    private final TaskRepository taskRepo;
    private final CreateTaskUseCase createUseCase;
    private final UpdateProgressUseCase updateUseCase;
    private final IssueRewardUseCase issueRewardUseCase;

    public TaskService(TaskRepository taskRepo, CreateTaskUseCase createUseCase,
                       UpdateProgressUseCase updateUseCase, IssueRewardUseCase issueRewardUseCase) {
        this.taskRepo = taskRepo;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.issueRewardUseCase = issueRewardUseCase;
    }



    /**
     * 获取所有任务定义
     *
     * @return 任务定义DTO列表
     */
    @Override
    public List<TaskDefinitionDTO> listTasks() {
        return taskRepo.loadAll().stream().map(DomainMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public void createTask(TaskDefinitionDTO dto) {
        createUseCase.execute(dto);
    }

    @Override
    public void handleCoreEvent(Object coreEvent) {
        updateUseCase.handleCoreEvent(coreEvent);
    }

    @Override
    public void reload() {
        // reload impl: read repo -> replace in-memory caches
    }
}
