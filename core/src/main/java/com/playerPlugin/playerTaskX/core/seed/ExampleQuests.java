package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.List;
import java.util.Map;

/**
 * 出厂自带的示例任务，仅在数据库为空时写入一次（见 {@code QuestAdminService#seedIfEmpty}）。
 *
 * <p>同一批任务还会被 {@link ExampleFiles} 铺成 {@code quests/} 下的 YAML 文件，
 * 那里用的是 {@code example_file_} 前缀（同 id 会撞上「库优先」，见该类注释）。</p>
 *
 * <p>这批任务有双重身份：既是新服「开箱即玩」的起点，也是各种目标/奖励写法的活文档——
 * 管理员在编辑器里对照着改，比读字段说明直观。</p>
 *
 * <p>选型上覆盖了大部分目标类型，并刻意包含「留空 = 任意」（垂钓、附魔、繁殖、驯服）
 * 与「逗号分隔多值」（讨伐亡灵、钻石矿工）两种约定，这些光看配置猜不出来。</p>
 *
 * <p>刻意不含 {@code submit}（提交物品）的示例：该类型目前没有生产者——没有监听器推
 * {@code Trigger.SUBMIT}，GUI 也没有提交入口，写进示例只会得到一个永远做不动的任务，
 * 而示例随出厂数据发给每个新服。类型本身仍然保留，见 {@code docs/objectives.md}。</p>
 *
 * <p>奖励以<b>命令奖励</b>为主（{@code give} / {@code xp} 这类原版命令）：它不需要任何软依赖，
 * 因此示例不会在没装经济插件的服务器上刷出「奖励不可用」的启动警告。金币只保留挖矿日常一处、
 * 点券只保留驯兽师一处，用来展示「货币奖励长什么样」——这两处确实会在缺少对应插件时报不可用，
 * 是刻意的示范代价。</p>
 *
 * <p>MiniMessage 写法注意：不要用闭合标签（如 {@code </yellow>}），
 * 「未开启标签的闭合」会直接抛异常；颜色由下一个标签覆盖，无需闭合。</p>
 */
public final class ExampleQuests {

    private ExampleQuests() {
    }

    /**
     * 全部示例任务。
     *
     * @param dailyRefreshCost 每日任务的刷新费用，统一取插件配置，保证示例与玩家实际扣费一致
     */
    public static List<Quest> all(double dailyRefreshCost) {
        return List.of(
                // ---------- 每日池：默认每天抽 3 个，池子太小会让所有玩家每天看到同一批 ----------
                daily("example_daily_mine", "<yellow>挖矿日常", "STONE_PICKAXE",
                        List.of("<gray>挖掘 64 个石头", "<gray>完成后可领取 500 金币"),
                        List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64))),
                        List.of(QuestReward.of("money", Map.of("amount", 500))),
                        dailyRefreshCost),
                daily("example_daily_hunt", "<yellow>讨伐亡灵", "IRON_SWORD",
                        List.of("<gray>击杀 10 只僵尸或骷髅", "<gray>完成后可领取 200 经验"),
                        List.of(QuestObjective.of("kill",
                                Map.of("target", "ZOMBIE,SKELETON", "amount", 10))),
                        List.of(giveExp(200)),
                        dailyRefreshCost),
                daily("example_daily_fish", "<yellow>渔夫的一天", "FISHING_ROD",
                        List.of("<gray>钓上 5 条鱼（任意种类）", "<gray>完成后可领取 5 个熟鲑鱼"),
                        List.of(QuestObjective.of("fish", Map.of("target", "", "amount", 5))),
                        List.of(give("cooked_salmon", 5)),
                        dailyRefreshCost),
                daily("example_daily_meal", "<yellow>一日三餐", "BREAD",
                        List.of("<gray>吃掉 8 个面包", "<gray>完成后可领取 150 经验"),
                        List.of(QuestObjective.of("consume", Map.of("target", "BREAD", "amount", 8))),
                        List.of(giveExp(150)),
                        dailyRefreshCost),
                daily("example_daily_torch", "<yellow>火把工坊", "TORCH",
                        List.of("<gray>合成 16 个火把", "<gray>完成后可领取 8 个煤炭"),
                        List.of(QuestObjective.of("craft", Map.of("target", "TORCH", "amount", 16))),
                        List.of(give("coal", 8)),
                        dailyRefreshCost),
                daily("example_daily_build", "<yellow>添砖加瓦", "BRICKS",
                        List.of("<gray>放置 64 个圆石", "<gray>完成后可领取 300 经验"),
                        List.of(QuestObjective.of("place_block",
                                Map.of("target", "COBBLESTONE", "amount", 64))),
                        List.of(giveExp(300)),
                        dailyRefreshCost),

                // ---------- 常驻任务：长期存在，顺带展示多奖励、多值目标等写法 ----------
                normal("example_normal_slayer", "<gold>亡灵猎人", "DIAMOND_SWORD",
                        List.of("<gray>击杀 64 只僵尸", "<gray>完成后可领取 3 颗钻石与 500 经验"),
                        List.of(QuestObjective.of("kill", Map.of("target", "ZOMBIE", "amount", 64))),
                        List.of(give("diamond", 3), giveExp(500))),
                normal("example_normal_diamond", "<gold>钻石矿工", "DIAMOND",
                        List.of("<gray>挖掘 16 个钻石矿（含深层钻石矿）", "<gray>完成后可领取 1000 经验"),
                        List.of(QuestObjective.of("break_block",
                                Map.of("target", "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE", "amount", 16))),
                        List.of(giveExp(1000))),
                normal("example_normal_enchant", "<gold>附魔师", "ENCHANTING_TABLE",
                        List.of("<gray>完成 10 次附魔（任意附魔）", "<gray>完成后可领取 32 个青金石"),
                        List.of(QuestObjective.of("enchant", Map.of("target", "", "amount", 10))),
                        List.of(give("lapis_lazuli", 32))),
                normal("example_normal_shepherd", "<gold>剪羊毛", "SHEARS",
                        List.of("<gray>给 32 只羊剪毛", "<gray>完成后可领取 16 个白色羊毛"),
                        List.of(QuestObjective.of("shear", Map.of("target", "SHEEP", "amount", 32))),
                        List.of(give("white_wool", 16))),
                normal("example_normal_rancher", "<gold>繁殖计划", "GOLDEN_CARROT",
                        List.of("<gray>繁殖 16 只动物（任意种类）", "<gray>完成后可领取 32 个小麦"),
                        List.of(QuestObjective.of("breed", Map.of("target", "", "amount", 16))),
                        List.of(give("wheat", 32))),
                normal("example_normal_tamer", "<gold>驯兽师", "NAME_TAG",
                        List.of("<gray>驯服 3 只动物（任意种类）", "<gray>完成后可领取 300 点券"),
                        List.of(QuestObjective.of("tame", Map.of("target", "", "amount", 3))),
                        List.of(QuestReward.of("points", Map.of("amount", 300))))
        );
    }

    /**
     * 用命令发物品：命令奖励由控制台执行，因此不需要给玩家任何权限。
     * <p>
     * 物品不再是一种奖励类型——那样每种要发的物品都得在插件里重做一遍 {@code material} /
     * {@code name} / {@code lore} 字段，而 {@code /give} 早就把这些做完了。
     */
    private static QuestReward give(String item, int amount) {
        return QuestReward.of("command", Map.of("command", "give %player% " + item + " " + amount));
    }

    /** 用命令发经验：同样是命令奖励，理由见 {@link #give}。 */
    private static QuestReward giveExp(int points) {
        return QuestReward.of("command", Map.of("command", "xp add %player% " + points + " points"));
    }

    /** 每日示例的公共外壳：分类「每日」，刷新费用与玩家实际刷新扣费保持一致。 */
    private static Quest daily(String id, String name, String icon, List<String> description,
                               List<QuestObjective> objectives, List<QuestReward> rewards,
                               double refreshCost) {
        return new Quest(id, name, description, icon, "每日", QuestType.DAILY,
                objectives, rewards, refreshCost, true);
    }

    /** 常驻示例的公共外壳：刷新费用只对每日任务有意义，固定 0。 */
    private static Quest normal(String id, String name, String icon, List<String> description,
                                List<QuestObjective> objectives, List<QuestReward> rewards) {
        return new Quest(id, name, description, icon, "常驻", QuestType.NORMAL,
                objectives, rewards, 0, true);
    }
}
