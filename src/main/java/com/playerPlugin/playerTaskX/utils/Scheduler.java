package com.playerPlugin.playerTaskX.utils;

import cn.yvmou.ylib.YLib;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import kotlin.Function;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Lambda;
import org.bukkit.Bukkit;

public class Scheduler {
    private static final YLib yLib = YLib.getyLib();

    public static void runTask(Function0<Void> runnable) {
        yLib.getScheduler().runTask(runnable::invoke);
    }
}
