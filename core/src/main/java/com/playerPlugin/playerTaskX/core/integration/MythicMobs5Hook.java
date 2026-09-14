package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link MythicMobsHook} 的 MythicMobs 5.x 实现。
 *
 * <h2>为什么用反射，而不是 compileOnly 依赖</h2>
 * MythicMobs 的 API 类（{@code MythicBukkit}）继承自另一个构件里的 {@code LuminePlugin}，
 * 编译期引用它会连带要求 {@code LumineUtils} 之类的依赖（实测：{@code 无法访问 LuminePlugin}），
 * 那意味着构建要多挂一个第三方仓库、还要解析快照版本。
 * 而我们只用到三个方法，反射的代价远小于此，顺带还能容忍 5.x 内部的小幅改名
 * （失败时只记一条日志、降级为「未接入」，不会让插件起不来）。
 *
 * <p>这与 {@code PlaceholderHook}、{@code PointsReward} 是同一套做法：
 * <b>对方是别人的插件 jar，就用最小的方式接进去</b>。
 *
 * <h2>为什么怪物管理器要延迟解析</h2>
 * MythicMobs 的 plugin.yml 是 {@code load: POSTWORLD}，它<b>启用</b>得比本插件晚
 * （实测 Folia/Canvas 上：本插件 20:01:33 启用，MythicMobs 20:01:33 才开始启用），
 * 而怪物管理器是它在自己的 onEnable 里创建的。早先的实现在创建时读一次
 * {@code getMobManager()} 并保存实例，于是「装了 MythicMobs 也永远接不上」——
 * 只有启动日志里一行 warn，玩家侧表现为 {@code mythic:} 目标永远不涨进度。
 * 现在只保存 {@code MythicBukkit} 单例（它在<b>加载</b>阶段就绑好了），
 * 管理器在第一次真正用到时解析，拿到即缓存（见 {@link #manager()}）。
 */
final class MythicMobs5Hook implements MythicMobsHook {

    private static final String BUKKIT_CLASS = "io.lumine.mythic.bukkit.MythicBukkit";

    /** 怪物管理器接口（{@code getMythicMobInstance} / {@code getMobNames} 都声明在它上面）。 */
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

    /** 管理器就绪后才解析出来的两个方法。 */
    private Method getMythicMobInstance;
    private Method getMobNames;

    private MythicMobs5Hook(Object bukkit, Method getMobManager, Method getMobType) {
        this.bukkit = bukkit;
        this.getMobManager = getMobManager;
        this.getMobType = getMobType;
    }

    /**
     * 解析 {@code MythicBukkit} 与 {@code ActiveMob} 这两个入口。
     * <p>
     * <b>刻意不在这里读怪物管理器</b>：那时 MythicMobs 还没启用，读到的一定是 null。
     *
     * @throws ReflectiveOperationException 版本不匹配（类/方法被改名或移除）时
     */
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
        getMythicMobInstance = method(managerType, "getMythicMobInstance", Entity.class);
        getMobNames = method(managerType, "getMobNames");
        if (getMythicMobInstance == null || getMobNames == null) {
            try {
                Class<?> iface = Class.forName(MOB_MANAGER_CLASS);
                if (getMythicMobInstance == null) {
                    getMythicMobInstance = method(iface, "getMythicMobInstance", Entity.class);
                }
                if (getMobNames == null) {
                    getMobNames = method(iface, "getMobNames");
                }
            } catch (ClassNotFoundException ignored) {
                // 没有这个接口时以运行时类的结果为准
            }
        }
        mobManager = resolved;
        return resolved;
    }

    /** 取公开方法；不存在或签名不符时返回 {@code null}（调用方各自降级）。 */
    @Nullable
    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException | RuntimeException e) {
            return null;
        }
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

    @Override
    public List<String> mobIds() {
        if (manager() == null || getMobNames == null) {
            return List.of();
        }
        try {
            Object names = getMobNames.invoke(mobManager);
            if (!(names instanceof Collection<?> collection)) {
                return List.of();
            }
            List<String> ids = new ArrayList<>(collection.size());
            for (Object name : collection) {
                if (name != null) {
                    ids.add(String.valueOf(name));
                }
            }
            return ids;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }
}
