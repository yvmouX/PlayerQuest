package com.playerPlugin.playerTaskX.utils;

import cn.yvmou.ylib.YLib;
import cn.yvmou.ylib.api.scheduler.UniversalScheduler;
import cn.yvmou.ylib.tools.LoggerTools;

public class Help {
    public static UniversalScheduler scheduler = YLib.getyLib().getScheduler();
    public static LoggerTools logger = YLib.getyLib().getLoggerTools();
}
