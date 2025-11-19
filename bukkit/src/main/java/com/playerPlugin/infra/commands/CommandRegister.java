package com.playerPlugin.infra.commands;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.infra.TaskManager;
import com.playerPlugin.infra.PlayerTaskX;
import com.playerPlugin.infra.commands.admin.ListCmd;
import com.playerPlugin.infra.commands.admin.ReloadCmd;
import com.playerPlugin.infra.commands.admin.StartCmd;
import com.playerPlugin.infra.commands.user.AcceptCmd;
import com.playerPlugin.infra.commands.user.MeCmd;
import com.playerPlugin.infra.commands.user.OpenCmd;
import com.playerPlugin.infra.configs.TaskConfig;
import com.playerPlugin.infra.dataManager.StorgeManager;

public class CommandRegister {
    private final PlayerTaskX plugin;
    private final YLib ylib;
    private final TaskConfig taskConfig;
    private final StorgeManager sm;
    private final TaskManager tm;

    public CommandRegister(PlayerTaskX plugin, YLib ylib, TaskConfig taskConfig, StorgeManager sm, TaskManager tm) {
        this.plugin = plugin;
        this.ylib = ylib;
        this.taskConfig = taskConfig;
        this.sm = sm;
        this.tm = tm;
    }

    public void registerCommands() {
        ylib.getCommandManager().registerCommands("playertaskx",
                new MeCmd(tm, sm),
                new AcceptCmd(tm),
                new OpenCmd()
        );
        ylib.getCommandManager().registerCommands("ptx",
                new MeCmd(tm, sm),
                new AcceptCmd(tm),
                new OpenCmd()
        );
        ylib.getCommandManager().registerCommands("playertaskxadmin",
                new ReloadCmd(plugin, taskConfig),
                new ListCmd(tm),
                new StartCmd(tm)
        );
        ylib.getCommandManager().registerCommands("ptxa",
                new ReloadCmd(plugin, taskConfig),
                new ListCmd(tm),
                new StartCmd(tm)
        );
    }
}
