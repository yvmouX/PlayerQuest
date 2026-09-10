package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * 奖励：点券（经 PlayerPoints）。
 * <p>
 * 用反射而不是直接 import：PlayerPoints 是软依赖，直接引用会让缺失该插件的
 * 服务端在类加载阶段就报 NoClassDefFoundError，连插件都启动不了。
 */
public final class PointsReward implements RewardType {

    public static final String ID = "points";

    private Object cachedApi;
    private Method giveMethod;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "点券";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.integer("amount", "数量", 100, "发放的点券数量")
        );
    }

    @Override
    public void grant(Player player, QuestReward reward) {
        Object api = api();
        if (api == null || giveMethod == null) {
            return;
        }
        int amount = reward.integer("amount", 0);
        if (amount <= 0) {
            return;
        }
        try {
            giveMethod.invoke(api, player.getUniqueId(), amount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("发放点券失败", e);
        }
    }

    @Override
    public boolean available() {
        return api() != null;
    }

    @Override
    public String unavailableReason() {
        return api() == null ? "未安装 PlayerPoints" : "";
    }

    /** 查询余额（GUI 展示用）。 */
    public int balance(UUID playerId) {
        Object api = api();
        if (api == null) {
            return 0;
        }
        try {
            Method look = api.getClass().getMethod("look", UUID.class);
            Object result = look.invoke(api, playerId);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** 扣除点券，返回是否成功（刷新费用用）。 */
    public boolean take(UUID playerId, int amount) {
        Object api = api();
        if (api == null || amount <= 0) {
            return false;
        }
        try {
            Method take = api.getClass().getMethod("take", UUID.class, int.class);
            Object result = take.invoke(api, playerId, amount);
            return result instanceof Boolean success && success;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Object api() {
        if (cachedApi != null) {
            return cachedApi;
        }
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            return null;
        }
        try {
            Class<?> mainClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object instance = mainClass.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return null;
            }
            Object api = mainClass.getMethod("getAPI").invoke(instance);
            if (api == null) {
                return null;
            }
            giveMethod = api.getClass().getMethod("give", UUID.class, int.class);
            cachedApi = api;
            return cachedApi;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
