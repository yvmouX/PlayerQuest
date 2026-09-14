package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.util.List;
import java.util.Map;

/**
 * 出厂自带的默认预设，仅在 {@code preset} 表为空时写入一次。
 *
 * <p>同一批预设还会被 {@link ExampleFiles} 铺成 {@code presets/} 下的 YAML 文件，
 * 那里用的是 {@code example_file_} 前缀（同 id 会撞上「库优先」，见该类注释）。</p>
 *
 * <p>作用与 {@link ExampleQuests} 相同但面向编辑器：管理员一进预设页面就看得见
 * 「预设长什么样、怎么套用」，不必先自己建一条才有参照。
 *
 * <p>刻意覆盖三类写法：最常见的目标、类型自己的默认值（{@code chat} 的任意发言）、
 * 以及三类奖励——命令（不需要任何依赖）、金币（要 Vault）、点券（要 PlayerPoints）。
 * 后两类顺带验证编辑器对自己用不了的奖励类型是否有妥善提示。</p>
 */
public final class ExamplePresets {

    private ExamplePresets() {
    }

    /** 全部默认预设（目标在前、奖励在后，顺序即编辑器里的展示顺序）。 */
    public static List<Preset> all() {
        return List.of(
                objective("mine-stone", "挖 64 个石头", "break_block",
                        props("target", "STONE", "amount", 64), "最基础的挖掘目标"),
                objective("mine-diamond", "挖 16 个钻石矿", "break_block",
                        props("target", "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE", "amount", 16),
                        "两种钻石矿都计入"),
                objective("kill-zombie", "击杀 20 只僵尸", "kill",
                        props("target", "ZOMBIE", "amount", 20), ""),
                objective("kill-any", "击杀任意生物 30 只", "kill",
                        props("target", "", "amount", 30), "target 留空表示任意生物"),
                objective("craft-torch", "合成 16 个火把", "craft",
                        props("target", "TORCH", "amount", 16), ""),
                objective("fish-any", "钓 10 条鱼", "fish",
                        props("target", "", "amount", 10), ""),
                objective("chat-hello", "发言一次", "chat",
                        props("target", "", "amount", 1), "任意发言都算"),

                reward("reward-money", "奖励 500 金币", "money",
                        props("amount", 500), "需要 Vault"),
                reward("reward-points", "奖励 100 点券", "points",
                        props("amount", 100), "需要 PlayerPoints"),
                reward("reward-diamond", "奖励 3 个钻石", "command",
                        props("command", "give %player% diamond 3"), "用命令发物品，不需要任何依赖"),
                reward("reward-broadcast", "全服公告", "command",
                        props("command", "broadcast %player% 完成了一个任务！"), "命令奖励不限于发东西")
        );
    }

    private static Preset objective(String id, String name, String type,
                                    Map<String, Object> properties, String description) {
        return new Preset(Preset.OBJECTIVES, id, name, type, properties, description);
    }

    private static Preset reward(String id, String name, String type,
                                 Map<String, Object> properties, String description) {
        return new Preset(Preset.REWARDS, id, name, type, properties, description);
    }

    /** 成对的键值建表，比一串 {@code Map.of} 更接近「配置」的写法。 */
    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
