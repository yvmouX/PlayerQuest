package com.playerPlugin.playerTaskX.core.integration.mythicmobs;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import com.playerPlugin.playerTaskX.core.integration.Reflect;

/** MythicMobs 5.x 接入（全反射）：MobManager 必须延迟解析，因为 MythicMobs 是 {@code load: POSTWORLD}，启用得比本插件晚，onEnable 时创建的管理器还拿不到。 */
final class MythicMobs5Hook implements MythicMobsHook {

    private static final String BUKKIT_CLASS = "io.lumine.mythic.bukkit.MythicBukkit";

    /** 怪物管理器接口（{@code getMythicMobInstance} 声明在它上面）。 */
    private static final String MOB_MANAGER_CLASS = "io.lumine.mythic.core.mobs.MobManager";

    private static final String ACTIVE_MOB_CLASS = "io.lumine.mythic.core.mobs.ActiveMob";

    /** {@code MythicBukkit} 单例；MythicMobs 只是加载完就有。 */
    private final Object bukkit;

    /** {@code MythicBukkit#getMobManager()}；管理器未就绪时返回 null，不当成失败。 */
    private final Method getMobManager;

    /** {@code ActiveMob#getMobType()}；与实例无关，创建时解析一次即可。 */
    private final Method getMobType;

    /** 已解析到的怪物管理器；拿到就缓存，避免击杀热路径上每次反射取 getter。 */
    private Object mobManager;

    /** 管理器就绪后才解析出来的方法。 */
    private Method getMythicMobInstance;

    private MythicMobs5Hook(Object bukkit, Method getMobManager, Method getMobType) {
        this.bukkit = bukkit;
        this.getMobManager = getMobManager;
        this.getMobType = getMobType;
    }

    /** 解析 {@code MythicBukkit} 与 {@code ActiveMob} 两个入口；刻意不在这里读怪物管理器——那时 MythicMobs 还没启用，读到的必是 null。 */
    static MythicMobs5Hook create() throws ReflectiveOperationException {
        Class<?> bukkitClass = Class.forName(BUKKIT_CLASS);
        Object instance = bukkitClass.getMethod("inst").invoke(null);
        if (instance == null) {
            throw new IllegalStateException("MythicBukkit.inst() 返回 null");
        }
        Method mobManager = bukkitClass.getMethod("getMobManager");
        Method mobType = Class.forName(ACTIVE_MOB_CLASS).getMethod("getMobType");
        return new MythicMobs5Hook(instance, mobManager, mobType);
    }
    /**
     * 怪物管理器；MythicMobs 尚未启用时返回 {@code null}，由各调用方降级。
     * <p>
     * 方法先按运行时类取（公开方法在类与接口上都找得到，因此对实现类改名免疫），
     * 取不到再按 {@code MobManager} 接口取一次——两条都失败才算「签名对不上」。
     */
    @Nullable
    private Object manager() {
        if (mobManager != null) {
            return mobManager;
        }
        Object resolved;
        try {
            resolved = getMobManager.invoke(bukkit);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        if (resolved == null) {
            return null;
        }
        Class<?> managerType = resolved.getClass();
        getMythicMobInstance = Reflect.method(managerType, "getMythicMobInstance", Entity.class);
        if (getMythicMobInstance == null) {
            try {
                Class<?> iface = Class.forName(MOB_MANAGER_CLASS);
                getMythicMobInstance = Reflect.method(iface, "getMythicMobInstance", Entity.class);
            } catch (ClassNotFoundException ignored) {
                // 没有这个接口时以运行时类的结果为准
            }
        }
        mobManager = resolved;
        return resolved;
    }

    @Override
    @Nullable
    public String mobId(LivingEntity entity) {
        if (entity == null || manager() == null || getMythicMobInstance == null) {
            return null;
        }
        try {
            Object mob = getMythicMobInstance.invoke(mobManager, entity);
            if (mob == null) {
                return null;
            }
            Object type = getMobType.invoke(mob);
            return type == null ? null : String.valueOf(type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 单次解析失败（实体是别的插件伪造的等）不该让击杀事件整体出错
            return null;
        }
    }
}
