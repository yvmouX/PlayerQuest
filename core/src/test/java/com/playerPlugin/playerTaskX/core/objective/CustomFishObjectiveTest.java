package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「自定义钓鱼」目标（CustomFishing 对接）的判定测试。
 *
 * <p>要防的是「配了却永远不涨」：战利品 id 比较写错大小写、最小尺寸比反了方向、
 * 拿不到尺寸时把小鱼算成达标——这些都不会报错，只会让玩家反复钓却看不到进度。</p>
 */
class CustomFishObjectiveTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final ObjectiveType type = BuiltIns.objective(CustomFishObjective.ID);

    @Test
    @DisplayName("id 命中就给本次数量，id 不符给 0")
    void matchesById() {
        Map<String, Object> config = Map.of("target", "my_custom_fish", "min-size", 0);

        assertEquals(1, type.match(context("my_custom_fish", null, 1), config));
        assertEquals(0, type.match(context("other_fish", null, 1), config), "别的鱼不该计入");
    }

    @Test
    @DisplayName("id 比较忽略大小写：CustomFishing 的 id 大小写不该成为坑")
    void idComparisonIgnoresCase() {
        Map<String, Object> config = Map.of("target", "My_Custom_Fish", "min-size", 0);
        assertEquals(1, type.match(context("my_custom_fish", null, 1), config));
    }

    @Test
    @DisplayName("留空或 * 表示任意自定义鱼")
    void emptyTargetMatchesAnyCustomFish() {
        for (String any : new String[]{"", "*"}) {
            Map<String, Object> config = Map.of("target", any, "min-size", 0);
            assertEquals(1, type.match(context("whatever", null, 1), config), "target=" + any);
        }
    }

    @Test
    @DisplayName("数量按钓获物堆大小计")
    void amountFollowsLootStack() {
        Map<String, Object> config = Map.of("target", "", "min-size", 0);
        assertEquals(5, type.match(context("anything", null, 5), config));
    }

    @Test
    @DisplayName("最小尺寸：达标才计数，且是「不小于」而不是「大于」")
    void minSizeIsInclusiveLowerBound() {
        Map<String, Object> config = Map.of("target", "", "min-size", 30);

        assertEquals(1, type.match(context("fish", "30.0", 1), config), "刚好达标应计入");
        assertEquals(1, type.match(context("fish", "45.5", 1), config));
        assertEquals(0, type.match(context("fish", "29.9", 1), config), "不达标不该计入");
    }

    @Test
    @DisplayName("min-size 为 0 或缺失时不过滤尺寸")
    void zeroMinSizeDisablesFilter() {
        assertEquals(1, type.match(context("fish", "1.0", 1), Map.of("target", "", "min-size", 0)));
        // 老配置里可能根本没有这个键：不能因此把所有钓获都判成不达标
        assertEquals(1, type.match(context("fish", "1.0", 1), Map.of("target", "")));
    }

    @Test
    @DisplayName("拿不到尺寸时按 0 处理：配了最小尺寸的目标宁可不给进度")
    void unknownSizeDoesNotSatisfyMinSize() {
        Map<String, Object> config = Map.of("target", "", "min-size", 10);
        assertEquals(0, type.match(context("fish", null, 1), config), "缺尺寸不该当成达标");
        assertEquals(1, type.match(context("fish", null, 1), Map.of("target", "", "min-size", 0)));
    }

    @Test
    @DisplayName("它响应的动作是 CUSTOM_FISH，不是原版 FISH")
    void usesItsOwnTrigger() {
        assertEquals(Trigger.CUSTOM_FISH, type.trigger(),
                "两种钓获来自不同事件，混用会让原版垂钓目标被计两次");
    }

    @Test
    @DisplayName("schema：id 可留空、尺寸是非必填的 DECIMAL、数量是 INTEGER")
    void schemaIsEditorFriendly() {
        assertNotNull(type.schema().stream().filter(field -> "target".equals(field.key()))
                .findFirst().orElse(null));
        assertFalse(type.schema().stream().filter(field -> "target".equals(field.key()))
                .findFirst().orElseThrow().required(), "id 允许留空表示任意鱼");
        assertEquals("DECIMAL", type.schema().stream().filter(field -> "min-size".equals(field.key()))
                .findFirst().orElseThrow().type().name());
        assertTrue(type.schema().stream().anyMatch(field -> "amount".equals(field.key())));
    }

    private static ProgressContext context(String id, String size, int amount) {
        return new ProgressContext(PLAYER, null, Trigger.CUSTOM_FISH, id, amount, size, List.of());
    }
}
