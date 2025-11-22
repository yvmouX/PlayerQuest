package com.paperPlugin.api.events;

import com.playerPlugin.common.Enum.PTXActionType;

import java.util.UUID;

public class TaskProgressEvent {
    private final UUID playerId;

    /** 行为类型，如：KILL_MOB / BREAK_BLOCK / CRAFT_ITEM / CUSTOM */
    private final PTXActionType actionType;

    /** 针对行为的目标，例如：ZOMBIE / DIAMOND_BLOCK / DIAMOND_SWORD */
    private final String target;

    /** 增加的进度量（一般为 1，但也可能来自任务计算） */
    private final int amount;

    public TaskProgressEvent(UUID playerId, PTXActionType actionType, String target, int amount) {
        this.playerId = playerId;
        this.actionType = actionType;
        this.target = target;
        this.amount = amount;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public PTXActionType getActionType() {
        return actionType;
    }

    public String getTarget() {
        return target;
    }

    public int getAmount() {
        return amount;
    }

    public String getEventName() {
        return "TaskProgressEvent";
    }

    @Override
    public String toString() {
        return "TaskProgressEvent{" +
                "playerId=" + playerId +
                ", actionType='" + actionType + '\'' +
                ", target='" + target + '\'' +
                ", amount=" + amount +
                '}';
    }
}
