package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandComplete;
import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.CompleteType;
import cn.yvmou.ylib.api.command.SubCommand;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.model.TaskDefinition;
import com.playerPlugin.playerTaskX.model.TaskObjective;
import com.playerPlugin.playerTaskX.service.TaskService;
import com.playerPlugin.playerTaskX.utils.TimeUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class CreateCmd implements SubCommand {
    private final TaskService taskService;

    public CreateCmd(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    @CommandOptions(
            name = "create",
            permission = "",
            onlyPlayer = false,
            alias = {},
            register = true,
            usage = "/playertaskx create <taskID> <taskType> <taskName>"
    )
    @CommandComplete({
            @CommandComplete.Tab(
                    type = CompleteType.CUSTOM,
                    customOptions = {"<taskID>"}
            ),
            @CommandComplete.Tab(
                    type = CompleteType.CUSTOM,
                    customOptions = {"CYCLE", "LIMIT", "FOREVER"}
            ),
            @CommandComplete.Tab(
                    type = CompleteType.CUSTOM,
                    customOptions = {"<taskName>"}
            ),
    })
    public boolean execute(CommandSender sender, String[] args) {
        // TEST
        String taskId = args[1];
        if (taskId == null) {
            sender.sendMessage(ChatColor.RED + "Invalid task ID");
            return false;
        }

        PTXTaskType taskType = PTXTaskType.fromString(args[2]);
        if (taskType == PTXTaskType.NONE) {
            sender.sendMessage(ChatColor.RED + "Invalid task type: " + args[2]);
            return false;
        }

        String taskName = args[3];
        if (taskName == null) {
            sender.sendMessage(ChatColor.RED + "Invalid task name");
            return false;
        }


        TaskDefinition takDef = new TaskDefinition(taskId, taskType, taskName,"这是描述哈",
                List.of(new TaskObjective(PTXActionType.BREAK, "DIAMOND_BLOCK", false, 0, 10, TimeUtil.getTime(), TimeUtil.getTime())),
                TimeUtil.getTime(), TimeUtil.getTime()
        );
        taskService.createTask(takDef);
        return true;
    }
}
