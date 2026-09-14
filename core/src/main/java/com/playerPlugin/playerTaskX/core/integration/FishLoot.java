package com.playerPlugin.playerTaskX.core.integration;

/**
 * 一条 CustomFishing 战利品，供编辑器的「鱼 id」选择器列出。
 *
 * <p><b>本类刻意不引用任何 CustomFishing 类型</b>：它要能被没装 CustomFishing 的
 * 服务端安全加载（素材目录在每次请求里都会碰它）。真正读 CustomFishing 注册表的是
 * {@link CustomFishingCatalog}，那个类只由 {@link CustomFishingHook} 反射加载。
 *
 * @param id   战利品 id，也就是写进 {@code custom_fish} 目标 {@code target} 的值
 * @param name 显示名（CustomFishing 配置里的 {@code nick}）；取不到时与 id 相同
 */
public record FishLoot(String id, String name) {

    /** 显示名缺失时退回 id，界面上至少还能看出是哪条。 */
    public FishLoot {
        name = name == null || name.isBlank() ? id : name;
    }
}
