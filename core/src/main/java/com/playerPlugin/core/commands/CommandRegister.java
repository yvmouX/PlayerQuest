package com.playerPlugin.core.commands;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.bukkit.configs.TaskConfig;
import com.playerPlugin.core.PlayerTaskX;
import com.playerPlugin.core.commands.admin.ListCmd;
import com.playerPlugin.core.commands.admin.ReloadCmd;
import com.playerPlugin.core.commands.admin.StartCmd;
import com.playerPlugin.core.commands.user.MeCmd;
import com.playerPlugin.core.commands.user.OpenCmd;

public class CommandRegister {
    private final PlayerTaskX plugin;
    private final YLib ylib;
    private final TaskConfig taskConfig;

    public CommandRegister(PlayerTaskX plugin, YLib ylib, TaskConfig taskConfig) {
        this.plugin = plugin;
        this.ylib = ylib;
        this.taskConfig = taskConfig;
    }

    public void registerCommands() {
        ylib.getCommandManager().registerCommands("playertaskx",
                new MeCmd(),
                new OpenCmd()
        );
        ylib.getCommandManager().registerCommands("ptx",
                new MeCmd(),
                new OpenCmd()
        );
        ylib.getCommandManager().registerCommands("playertaskxadmin",
                new ReloadCmd(plugin, taskConfig),
                new ListCmd(),
                new StartCmd()
        );
        ylib.getCommandManager().registerCommands("ptxa",
                new ReloadCmd(plugin, taskConfig),
                new ListCmd(),
                new StartCmd()
        );
    }
}
