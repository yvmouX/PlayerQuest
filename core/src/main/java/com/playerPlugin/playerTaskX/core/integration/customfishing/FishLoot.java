package com.playerPlugin.playerTaskX.core.integration.customfishing;

/**
 * 一条 CustomFishing 战利品，供校验 {@code custom_fish} 目标的鱼 id。
 * <p>刻意不引用任何 CustomFishing 类型：定义校验每次都会碰它，没装该插件的服务端也要能安全加载。
 */
public record FishLoot(String id, String name) {

    /** 显示名缺失时退回 id，界面上至少还能看出是哪条。 */
    public FishLoot {
        name = name == null || name.isBlank() ? id : name;
    }
}
