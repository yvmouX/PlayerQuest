package com.playerPlugin.playerTaskX.core.integration;

import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * 内存版 MythicMobs 接入点，供不装 MythicMobs 的测试使用。
 *
 * <p>真机验证只能覆盖「没装 MythicMobs」这一支（本项目的测试环境也没有它），
 * 而「装了之后击杀任务要带上 mythic: 别名」「MythicMobs 自己出错不能吞掉击杀」
 * 这些分支恰恰是最容易写错的，因此用替身把它们钉住。
 */
public final class FakeMythicMobsHook implements MythicMobsHook {

    private final String mobId;
    private final List<String> mobIds;
    private final boolean failOnLookup;

    private FakeMythicMobsHook(String mobId, List<String> mobIds, boolean failOnLookup) {
        this.mobId = mobId;
        this.mobIds = mobIds == null ? List.of() : List.copyOf(mobIds);
        this.failOnLookup = failOnLookup;
    }

    /** 所有实体都解析成同一个怪物 id。 */
    public static FakeMythicMobsHook ofMobId(String mobId) {
        return new FakeMythicMobsHook(mobId, List.of(mobId), false);
    }

    /** 实体不是 MythicMobs 怪物（返回 null）。 */
    public static FakeMythicMobsHook ofNothing() {
        return new FakeMythicMobsHook(null, List.of(), false);
    }

    /** 只有怪物清单，没有实体解析：供编辑器目录的测试用。 */
    public static FakeMythicMobsHook ofMobIds(String... mobIds) {
        return new FakeMythicMobsHook(null, List.of(mobIds), false);
    }

    /** 解析实体时抛异常：模拟 MythicMobs 内部出错。 */
    public static FakeMythicMobsHook failing() {
        return new FakeMythicMobsHook(null, List.of(), true);
    }

    @Override
    public String mobId(LivingEntity entity) {
        if (failOnLookup) {
            throw new IllegalStateException("MythicMobs 内部错误（测试替身）");
        }
        return mobId;
    }

    @Override
    public List<String> mobIds() {
        return mobIds;
    }
}
