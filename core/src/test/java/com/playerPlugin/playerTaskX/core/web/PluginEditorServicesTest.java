package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * {@link PluginEditorServices} 的转交测试。
 *
 * <p>这个类本身没有逻辑，但它承担一个容易被改回去的设计决定：<b>主类不实现
 * {@link EditorServices}</b>——编辑器的需求只在构造时注入（预设仓储与存储描述此前是主类上
 * 两个「只有 web 层会用」的公开 getter）。因此这里把「注进去什么就吐出来什么」钉住，
 * 免得以后有人图省事改成「拿插件实例再转调」，把公开面又还回去。
 */
class PluginEditorServicesTest {

    private static PluginEditorServices services(AtomicReference<String> storage) {
        return new PluginEditorServices(
                new QuestRegistryImpl(),
                mock(QuestRepository.class),
                mock(QuestAdminService.class),
                mock(PresetRepository.class),
                mock(PlayerQuestRepository.class),
                new ObjectiveRegistryImpl(),
                new RewardRegistryImpl(),
                storage::get);
    }

    @Test
    @DisplayName("八个访问点原样转交：注进去什么就给出什么")
    void delegatesEveryAccessor() {
        QuestRegistryImpl quests = new QuestRegistryImpl();
        QuestRepository definitions = mock(QuestRepository.class);
        QuestAdminService admin = mock(QuestAdminService.class);
        PresetRepository presets = mock(PresetRepository.class);
        PlayerQuestRepository playerQuests = mock(PlayerQuestRepository.class);
        ObjectiveRegistryImpl objectives = new ObjectiveRegistryImpl();
        RewardRegistryImpl rewards = new RewardRegistryImpl();

        PluginEditorServices services = new PluginEditorServices(quests, definitions, admin, presets,
                playerQuests, objectives, rewards, () -> "SQLite: data/playerTaskX.db");

        assertSame(quests, services.quests());
        assertSame(definitions, services.questDefinitions());
        assertSame(admin, services.questAdmin());
        assertSame(presets, services.presets(), "预设仓储只有编辑器用，因此由构造参数注入而不是主类开 getter");
        assertSame(playerQuests, services.playerQuestRepository());
        assertSame(objectives, services.objectiveTypes());
        assertSame(rewards, services.rewardTypes());
        assertEquals("SQLite: data/playerTaskX.db", services.describeStorage());
    }

    @Test
    @DisplayName("存储描述每次现取：数据库句柄就绪前后拿到的是不同结果")
    void storageDescriptionIsReadThroughTheSupplier() {
        AtomicReference<String> storage = new AtomicReference<>(null);
        PluginEditorServices services = services(storage);

        assertEquals("未连接", services.describeStorage(),
                "数据库还没打开时如实降级成「未连接」，而不是把 null 递给前端");

        storage.set("MySQL: playertaskx@localhost");
        assertEquals("MySQL: playertaskx@localhost", services.describeStorage(),
                "取值函数而不是快照：装配顺序变化时这里不会留下过期字符串");
    }
}
