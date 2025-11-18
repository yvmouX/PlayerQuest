package com.playerPlugin.core.usecase;

import com.playerPlugin.core.utils.DomainMapper;
import com.paperPlugin.api.dto.TaskDefinitionDTO;
import com.playerPlugin.core.domain.PlayerTask.Task.TaskDefinition;
import com.playerPlugin.core.repository.TaskRepository;

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
