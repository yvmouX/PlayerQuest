package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.impl.command.CommandManager;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.commands.admin.StartCmd;
import com.playerPlugin.playerTaskX.commands.user.MeCmd;
import com.playerPlugin.playerTaskX.commands.user.OpenCmd;
import com.playerPlugin.playerTaskX.service.TaskService;
import me.devnatan.inventoryframework.ViewFrame;

public class CommandRegister {
    private final CommandManager manager;
    private final TaskCache cache;
    private final ViewFrame frame;
    private final TaskService service;

    public CommandRegister(CommandManager manager, TaskCache cache, ViewFrame frame, TaskService service) {
        this.manager = manager;
        this.cache = cache;
        this.frame = frame;
        this.service = service;
    }

    public void registerCommands() {
        manager.registerCommands("playertaskx",
                new MeCmd(cache),
                new OpenCmd(frame)
        );
        manager.registerCommands("ptx",
                new MeCmd(cache),
                new OpenCmd(frame)
        );
        manager.registerCommands("playertaskxadmin",
                new ReloadCmd(),
                new ListCmd(cache),
                new StartCmd(cache, service)
        );
        manager.registerCommands("ptxa",
                new ReloadCmd(),
                new ListCmd(cache),
                new StartCmd(cache, service)
        );
    }
}
