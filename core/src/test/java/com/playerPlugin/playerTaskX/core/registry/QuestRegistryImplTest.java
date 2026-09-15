package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表顺序契约测试：翻页界面与命令列表都直接用它给的顺序，顺序一乱的表现是
 * 「切一次启用状态，任务跳到别的页」「/ptxa list 每次顺序不一样」——都不报错，因此必须钉住。
 */
class QuestRegistryImplTest {

    @Test
    @DisplayName("all() 按 id 升序，与插入顺序无关")
    void allIsSortedById() {
        QuestRegistryImpl registry = new QuestRegistryImpl();
        registry.replaceAll(List.of(quest("miner"), quest("daily_mine"), quest("zoo")));

        assertEquals(List.of("daily_mine", "miner", "zoo"), ids(registry.all()));
    }

    @Test
    @DisplayName("单个 upsert / remove 之后仍然有序")
    void staysSortedAfterWrites() {
        QuestRegistryImpl registry = new QuestRegistryImpl();
        registry.upsert(quest("c"));
        registry.upsert(quest("a"));
        registry.upsert(quest("b"));
        assertEquals(List.of("a", "b", "c"), ids(registry.all()));

        assertTrue(registry.remove("b"));
        assertEquals(List.of("a", "c"), ids(registry.all()));

        // 覆盖同一个 id 不该变成两条，也不该跑到别的顺序上
        registry.upsert(quest("a"));
        assertEquals(List.of("a", "c"), ids(registry.all()));
    }

    @Test
    @DisplayName("enabled() 与 ofType() 沿用同一顺序（周期任务界面按它列）")
    void derivedViewsKeepOrder() {
        QuestRegistryImpl registry = new QuestRegistryImpl();
        registry.upsert(quest("b", QuestType.DAILY, true));
        registry.upsert(quest("a", QuestType.DAILY, true));
        registry.upsert(quest("c", QuestType.DAILY, false));

        assertEquals(List.of("a", "b"), ids(registry.enabled()));
        assertEquals(List.of("a", "b"), ids(registry.ofType(QuestType.DAILY)));
        assertTrue(registry.ofType(QuestType.WEEKLY).isEmpty());
    }

    @Test
    @DisplayName("id 为空的任务进不来（否则 TreeMap 会当场抛 NPE，且空 id 也没有意义）")
    void rejectsBlankId() {
        QuestRegistryImpl registry = new QuestRegistryImpl();

        assertTrue(!registry.upsert(quest("")));
        assertTrue(!registry.upsert(quest(null)));
        assertTrue(!registry.upsert(null));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    @DisplayName("all(排序方式)：换个顺序排，但不改动注册表本身的顺序")
    void allWithOrder() {
        QuestRegistryImpl registry = new QuestRegistryImpl();
        // 名称与 id 故意不同序，才能看出到底按哪个排（中文的比较是码位序、不是拼音序，测试里用 ASCII 免歧义）
        registry.upsert(quest("a", "zeta", QuestType.NORMAL, true));
        registry.upsert(quest("b", "alpha", QuestType.NORMAL, true));
        registry.upsert(quest("c", "mid", QuestType.NORMAL, true));

        assertEquals(List.of("b", "c", "a"),
                registry.all(Comparator.comparing(Quest::name)).stream().map(Quest::id).toList());
        assertEquals(List.of("a", "b", "c"), ids(registry.all()), "传排序方式不该改注册表自己的顺序");
        assertEquals(ids(registry.all()), ids(registry.all(null)), "null 等同默认顺序");
    }

    @Test
    @DisplayName("排序稳定：比较结果相同的任务保持 id 升序（同名任务不会在翻页时抖动）")
    void orderIsStable() {
        QuestRegistryImpl registry = new QuestRegistryImpl();
        registry.upsert(quest("c", "同名", QuestType.NORMAL, true));
        registry.upsert(quest("a", "同名", QuestType.NORMAL, true));
        registry.upsert(quest("b", "同名", QuestType.NORMAL, true));

        assertEquals(List.of("a", "b", "c"),
                registry.all(Comparator.comparing(Quest::name)).stream().map(Quest::id).toList());
    }

    private static List<String> ids(List<Quest> quests) {
        return quests.stream().map(Quest::id).toList();
    }

    private static Quest quest(String id) {
        return quest(id, id, QuestType.NORMAL, true);
    }

    private static Quest quest(String id, QuestType type, boolean enabled) {
        return quest(id, id, type, enabled);
    }

    private static Quest quest(String id, String name, QuestType type, boolean enabled) {
        return new Quest(id, name, List.of(), "PAPER", "", type,
                List.of(new QuestObjective("break_block", Map.of("target", "STONE"))),
                List.of(), 0, enabled);
    }
}
