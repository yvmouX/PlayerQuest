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
 */
final class MythicMobs5Hook implements MythicMobsHook {

    private static final String BUKKIT_CLASS = "io.lumine.mythic.bukkit.MythicBukkit";

    /** 怪物管理器实例（{@code MobExecutor} / {@code MobManager}）。 */
    private final Object mobManager;

    private final Method getMythicMobInstance;
    private final Method getMobType;
    private final Method getMobNames;

    private MythicMobs5Hook(Object mobManager, Method getMythicMobInstance,
                            Method getMobType, @Nullable Method getMobNames) {
        this.mobManager = mobManager;
        this.getMythicMobInstance = getMythicMobInstance;
        this.getMobType = getMobType;
        this.getMobNames = getMobNames;
    }

    /**
     * 解析 MythicMobs 5.x 的必要入口。
     *
     * @throws ReflectiveOperationException 版本不匹配（方法被改名/移除）时
     */
    static MythicMobs5Hook create() throws ReflectiveOperationException {
        Class<?> bukkit = Class.forName(BUKKIT_CLASS);
        Object instance = bukkit.getMethod("inst").invoke(null);
        if (instance == null) {
            throw new IllegalStateException("MythicBukkit.inst() 返回 null");
        }
        Object mobManager = bukkit.getMethod("getMobManager").invoke(instance);
        if (mobManager == null) {
            throw new IllegalStateException("MythicBukkit#getMobManager() 返回 null");
        }
        // 签名取自 MythicMobs 5.x：getMythicMobInstance(Entity) → ActiveMob；
        // ActiveMob#getMobType() 是怪物内部名
        Method instanceOf = mobManager.getClass().getMethod("getMythicMobInstance", Entity.class);
        Class<?> activeMob = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
        Method mobType = activeMob.getMethod("getMobType");
        // 列全部怪物名只是编辑器的便利功能，取不到就退化为「不列」
        Method mobNames = null;
        try {
            mobNames = mobManager.getClass().getMethod("getMobNames");
        } catch (NoSuchMethodException ignored) {
            // 5.x 一直有这个方法；真没有也只是编辑器少一份清单
        }
        return new MythicMobs5Hook(mobManager, instanceOf, mobType, mobNames);
    }

    @Override
    @Nullable
    public String mobId(LivingEntity entity) {
        if (entity == null) {
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
        if (getMobNames == null) {
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
