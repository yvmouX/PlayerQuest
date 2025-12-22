package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.api.command.SimpleCommandManager;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import com.playerPlugin.playerTaskX.api.TaskAPI;
import com.playerPlugin.playerTaskX.cache.TaskCache;
import com.playerPlugin.playerTaskX.commands.admin.CreateCmd;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.commands.admin.StartCmd;
import com.playerPlugin.playerTaskX.commands.user.MeCmd;

public class CommandRegister {
    private final ConfigurationManager configurationManager;
    private final SimpleCommandManager manager;
    private final TaskCache cache;
    private final TaskAPI taskAPI;

    public CommandRegister(ConfigurationManager configurationManager, SimpleCommandManager manager, TaskCache cache, TaskAPI taskAPI) {
        this.configurationManager = configurationManager;
        this.manager = manager;
        this.cache = cache;
        this.taskAPI = taskAPI;
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
                new StartCmd(cache, taskAPI),
                new CreateCmd(taskAPI)
        );
        manager.registerCommands("ptxa",
                new ReloadCmd(configurationManager),
                new ListCmd(cache),
                new StartCmd(cache, taskAPI),
                new CreateCmd(taskAPI)
        );
    }
}
