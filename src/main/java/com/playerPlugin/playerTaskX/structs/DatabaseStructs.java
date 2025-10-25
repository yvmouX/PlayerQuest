package com.playerPlugin.playerTaskX.structs;

import java.util.Map;

record PlayersStruct(
        String id,
        String player_name,
        String task_id,
        Boolean finish // 是否完成了任务，默认False
) {
}
