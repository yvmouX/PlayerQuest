package com.playerPlugin.playerTaskX.core.integration.mythicmobs;

import org.bukkit.entity.LivingEntity;

/**
 * 内存版 MythicMobs 接入点，供不装 MythicMobs 的测试使用：真机只能覆盖「没装」那一支，而「装了之后击杀要带上 {@code mythic:} 别名」「MythicMobs 自己出错不能吞掉击杀」最易写错，用替身钉住。
 */
public final class FakeMythicMobsHook implements MythicMobsHook {

    private final String mobId;
    private final boolean failOnLookup;

    private FakeMythicMobsHook(String mobId, boolean failOnLookup) {
        this.mobId = mobId;
        this.failOnLookup = failOnLookup;
    }

    /** 所有实体都解析成同一个怪物 id。 */
    public static FakeMythicMobsHook ofMobId(String mobId) {
        return new FakeMythicMobsHook(mobId, false);
    }

    /** 实体不是 MythicMobs 怪物（返回 null）。 */
    public static FakeMythicMobsHook ofNothing() {
        return new FakeMythicMobsHook(null, false);
    }

    /** 解析实体时抛异常：模拟 MythicMobs 内部出错。 */
    public static FakeMythicMobsHook failing() {
        return new FakeMythicMobsHook(null, true);
    }

    @Override
    public String mobId(LivingEntity entity) {
        if (failOnLookup) {
            throw new IllegalStateException("MythicMobs 内部错误（测试替身）");
        }
        return mobId;
    }
}
