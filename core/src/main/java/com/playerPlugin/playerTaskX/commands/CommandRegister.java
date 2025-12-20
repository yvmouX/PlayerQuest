package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.SimpleCommandManager;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.admin.CreateCmd;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.commands.admin.StartCmd;
import com.playerPlugin.playerTaskX.commands.user.MeCmd;
import com.playerPlugin.playerTaskX.service.TaskService;

public class CommandRegister {
    private final ConfigurationManager configurationManager;
    private final SimpleCommandManager manager;
    private final TaskCache cache;
    private final TaskService service;

    public CommandRegister(ConfigurationManager configurationManager, SimpleCommandManager manager, TaskCache cache, TaskService service) {
        this.configurationManager = configurationManager;
        this.manager = manager;
        this.cache = cache;
        this.service = service;
    }

    public void registerCommands() {
        manager.registerCommands("playertaskx",
                new MeCmd(cache)
        );
        manager.registerCommands("ptx",
                new MeCmd(cache)
        );
        manager.registerCommands("playertaskxadmin",
                new ReloadCmd(configurationManager),
                new ListCmd(cache),
                new StartCmd(cache, service),
                new CreateCmd(service)
        );
        manager.registerCommands("ptxa",
                new ReloadCmd(configurationManager),
                new ListCmd(cache),
                new StartCmd(cache, service),
                new CreateCmd(service)
        );
    }
}
