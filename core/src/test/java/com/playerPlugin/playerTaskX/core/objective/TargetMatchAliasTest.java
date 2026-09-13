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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「同一对象的多个等价标识」（{@link ProgressContext#aliases()}）的判定测试。
 *
 * <p>场景来自 MythicMobs：一只自定义僵尸既是 {@code ZOMBIE}，又是 {@code mythic:CustomZombie}。
 * 两种写法都必须能配上任务，而「击杀任意生物」必须只算一次——监听器只推一个动作、
 * 把另一个名字放进别名，就是为了这一点。若退回「每识别出一个名字就推一次动作」，
 * 那个「任意生物」的任务会默默地按双倍速度完成。</p>
 */
class TargetMatchAliasTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final ObjectiveType kill = BuiltIns.objective("kill");

    @Test
    @DisplayName("配置写原版类型名时命中（别名不影响主标识）")
    void matchesPrimaryTarget() {
        assertEquals(1, kill.match(context(List.of("mythic:CustomZombie")), config("ZOMBIE")));
    }

    @Test
    @DisplayName("配置写 mythic: 内部名时靠别名命中")
    void matchesAlias() {
        assertEquals(1, kill.match(context(List.of("mythic:CustomZombie")), config("mythic:CustomZombie")));
    }

    @Test
    @DisplayName("别名与原版名可以混写在同一份配置里")
    void matchesEitherOfCommaSeparatedValues() {
        Map<String, Object> config = config("COW,mythic:CustomZombie");
        assertEquals(1, kill.match(context(List.of("mythic:CustomZombie")), config));
    }

    @Test
    @DisplayName("复合目标不匹配：别名与主标识都不符时为 0")
    void unrelatedTargetScoresZero() {
        assertEquals(0, kill.match(context(List.of("mythic:CustomZombie")), config("SKELETON")));
        assertEquals(0, kill.match(context(List.of("mythic:CustomZombie")), config("mythic:Other")));
    }

    @Test
    @DisplayName("「任意生物」只算一次：别名不会让同一个动作被匹配两遍")
    void anyTargetMatchesOnce() {
        ProgressContext context = context(List.of("mythic:CustomZombie"));
        assertEquals(1, kill.match(context, config("")));
        assertEquals(1, kill.match(context, config("*")), "一个动作只有一份 amount，不因标识多而翻倍");
    }

    @Test
    @DisplayName("别名比较忽略大小写；没有别名时行为与从前一致")
    void aliasComparisonIgnoresCaseAndDefaultsToEmpty() {
        assertEquals(1, kill.match(context(List.of("mythic:CustomZombie")), config("MYTHIC:customzombie")));
        assertFalse(context(List.of()).aliases().contains("mythic:CustomZombie"));
        assertEquals(1, kill.match(context(List.of()), config("ZOMBIE")));
    }

    private static Map<String, Object> config(String target) {
        return Map.of("target", target, "amount", 1);
    }

    private static ProgressContext context(List<String> aliases) {
        return new ProgressContext(PLAYER, null, Trigger.KILL, "ZOMBIE", 1, null, aliases);
    }
}
