package com.playerPlugin.playerTaskX.usecase;

import com.paperPlugin.api.model.TaskDefinitionDTO;
import com.playerPlugin.playerTaskX.domain.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import com.playerPlugin.playerTaskX.utils.DomainMapper;

public class CreateTaskUseCase {
    private final TaskRepository taskRepo;

    public CreateTaskUseCase(TaskRepository repo) {
        this.taskRepo = repo;
    }

    public void execute(TaskDefinitionDTO dto) {
        // 验证
        // validation
        if (dto.id == null || dto.id.trim().isEmpty()) throw new IllegalArgumentException("id required");
        TaskDefinition taskDefinition = DomainMapper.fromDTO(dto);
        // 进一步验证：唯一标识
        // further validation: unique id
        if (taskRepo.findById(taskDefinition.getId()).isPresent()) throw new IllegalArgumentException("taskDefinition exists");
        // 保存任务
        // save taskDefinition
        taskRepo.save(taskDefinition);
    }
}
