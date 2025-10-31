package com.playerPlugin.playerTaskX.structs;

record PlayersStruct(
        String player_uuid,
        String task_id,
        Integer finish_CYCLE_times,
        Integer finish_FOREVER_times,
        Integer finish_LIMIT_times,
        Integer finish_TRIGGER_times,
        Integer task_CRAFT_times,
        Integer task_KILL_times,
        Integer task_BREAK_times,
        Integer task_ENCHANT_times,
        Integer task_FISHING_times,
        Integer task_SCISSOR_times,
        Integer task_BREED_times,
        Integer task_TAME_times,
        Integer task_CONSUME_times,
        Integer task_TRIGGER_times,
        Integer task_DROP_times,
        Integer task_TAKE_times,
        Integer times
) { }

record PlayersTasksStruct(
        String player_uuid,
        String task_id,
        Integer task_type,
        Integer task_target,
        Integer progress,
        Integer status,
        String started_time,
        String finished_time,
        String expired_time
) { }
