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
        int amount = reward.integer("amount", 0);
        if (amount > 0) {
            giveTo(player.getUniqueId(), amount);
        }
    }

    @Override
    public boolean available() {
        return isAvailable();
    }

    @Override
    public String unavailableReason() {
        return isAvailable() ? "" : "未安装 PlayerPoints";
    }

    // ------------------------------------------------------------------
    // 静态访问：供 CurrencyType 等无实例的调用方使用
    // ------------------------------------------------------------------

    /** 缓存的 PlayerPoints API；null 表示尚未解析或不可用。 */
    private static Object staticApi;

    /** 缓存的 give 方法。 */
    private static Method staticGiveMethod;

    /** PlayerPoints 是否可用。 */
    public static boolean isAvailable() {
        return resolveApi() != null;
    }

    /** 查询余额。 */
    public static int balanceOf(UUID playerId) {
        Object api = resolveApi();
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

    /** 扣除点券，返回是否成功。 */
    public static boolean takeFrom(UUID playerId, int amount) {
        Object api = resolveApi();
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

    /** 发放点券，返回是否成功。 */
    public static boolean giveTo(UUID playerId, int amount) {
        Object api = resolveApi();
        if (api == null || staticGiveMethod == null || amount <= 0) {
            return false;
        }
        try {
            staticGiveMethod.invoke(api, playerId, amount);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 解析并缓存 PlayerPoints API。 */
    private static Object resolveApi() {
        if (staticApi != null) {
            return staticApi;
        }
        // 用 MoneyReward 的同款探测：服务端未初始化时 getPluginManager() 为 null，
        // 软依赖检测不该因此抛 NPE
        if (!MoneyReward.isPluginPresent("PlayerPoints")) {
            return null;
        }
        try {
            // 反射而不是直接 import：PlayerPoints 是软依赖，直接引用会让缺失该插件的
            // 服务端在类加载阶段就报 NoClassDefFoundError
            Class<?> mainClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object instance = mainClass.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return null;
            }
            Object api = mainClass.getMethod("getAPI").invoke(instance);
            if (api == null) {
                return null;
            }
            staticGiveMethod = api.getClass().getMethod("give", UUID.class, int.class);
            staticApi = api;
            return staticApi;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
