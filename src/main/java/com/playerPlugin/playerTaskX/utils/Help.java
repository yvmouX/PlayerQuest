package com.playerPlugin.playerTaskX.utils;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.dataManager.StorgeManager;

public class Help {
    public static UniversalScheduler scheduler = YLib.getyLib().getScheduler();
    public static LoggerTools log = YLib.getyLib().getLoggerTools();

    public static StorgeManager sm;
    public static TaskManager tm;
}
