package com.playerPlugin.playerTaskX.structs;

import java.util.List;
import java.util.Map;

public record TaskStructs(
        String ID,
        String name,
        String type,
        String target,
        Map<String, String> condition,
        Map<OnTaskStart, Boolean> onTaskStart,
        Map<OnTaskFinish, Boolean> onTaskFinish,
        Map<OnTaskCancel, Boolean> onTaskCancel,
        Map<OnTaskFail, Boolean> onTaskFail
) { }

record Trigger(
        String type,
        String args
) {}

record OnTaskStart(
    List<Trigger> commands
){}

record OnTaskFinish(
        List<Trigger> commands
){}

record OnTaskCancel(
        List<Trigger> commands
){}

record OnTaskFail(
        List<Trigger> commands
){}
