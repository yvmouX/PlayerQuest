package com.playerPlugin.playerTaskX.commands;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.bukkit.configs.TaskConfig;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.commands.admin.ListCmd;
import com.playerPlugin.playerTaskX.commands.admin.ReloadCmd;
import com.playerPlugin.playerTaskX.commands.admin.StartCmd;
import com.playerPlugin.playerTaskX.commands.user.MeCmd;
import com.playerPlugin.playerTaskX.commands.user.OpenCmd;

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
