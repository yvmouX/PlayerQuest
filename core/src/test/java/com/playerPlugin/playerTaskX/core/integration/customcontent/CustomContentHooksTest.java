package com.playerPlugin.playerTaskX.core.integration.customcontent;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义内容（ItemsAdder / CraftEngine）接入层的规矩测试：两家插件都不在本机，这里用替身验证与具体插件无关的部分——别名生成、裸 id 与前缀 id 都认、没接上的前缀要被校验报出来。
 * 真实反射签名对不对只能在装了插件的服务器上验证。
 */
class CustomContentHooksTest {

    /** 替身来源：固定回一个 id，用来验证别名生成与前缀。 */
    private record FakeHook(String plugin, String prefix, String itemId, String blockId)
            implements CustomContentHook {

        @Override
        public String itemId(ItemStack item) {
            return itemId;
        }

        @Override
        public String blockId(Block block) {
            return blockId;
        }
    }

    private static FakeHook itemsAdder() {
        return new FakeHook("ItemsAdder", "itemsadder:", "myitems:ruby", "myitems:ruby_block");
    }

    private static FakeHook craftEngine() {
        return new FakeHook("CraftEngine", "craftengine:", "myitems:ruby", null);
    }

    @Test
    @DisplayName("别名同时给带前缀与裸 id 两种写法：两种 target 都能命中同一个对象")
    void aliasesCarryBothForms() {
        CustomContentHooks hooks = CustomContentHooks.of(itemsAdder());

        assertTrue(hooks.aliases((ItemStack) null).isEmpty(), "null 物品不该产生别名");
        assertTrue(hooks.aliases((Block) null).isEmpty(), "null 方块不该产生别名");

        // 替身来源不看物品内容，只看「问没问它」——真实实现里这一步是插件的 API 调用
        assertEquals(List.of("myitems:ruby", "itemsadder:myitems:ruby"), hooks.aliases(fakeItem()));
    }

    @Test
    @DisplayName("两家都定义同一个 id 时，别名里两家都有")
    void bothPluginsContribute() {
        CustomContentHooks hooks = CustomContentHooks.of(itemsAdder(), craftEngine());

        List<String> collected = hooks.aliases(fakeItem());

        assertEquals(List.of("myitems:ruby", "itemsadder:myitems:ruby", "craftengine:myitems:ruby"), collected,
                "裸 id 只留一份，带前缀的各留一份");
    }

    @Test
    @DisplayName("校验：写了前缀但插件没接上时，报出「需要哪个插件」")
    void problemsReportMissingPlugin() {
        Quest quest = questWithTarget("itemsadder:myitems:ruby_block");

        // 空实现 = 两家都没接上
        List<String> problems = CustomContentHooks.empty().problems(quest);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("ItemsAdder"), problems.get(0));
        assertTrue(problems.get(0).contains("未安装"), problems.get(0));

        // 接上了就不再报
        assertTrue(CustomContentHooks.of(itemsAdder()).problems(quest).isEmpty());
    }

    @Test
    @DisplayName("校验：接上了 ItemsAdder 不代表 CraftEngine 的目标也能用")
    void problemsArePerPlugin() {
        Quest quest = questWithTarget("craftengine:myitems:ruby_block,DIAMOND_ORE");

        List<String> problems = CustomContentHooks.of(itemsAdder()).problems(quest);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("CraftEngine"), problems.get(0));
    }

    @Test
    @DisplayName("校验：没用前缀的普通目标不报；裸 id 也不报（无法判断是哪一家）")
    void plainTargetsAreIgnored() {
        assertTrue(CustomContentHooks.empty().problems(questWithTarget("DIAMOND_ORE,STONE")).isEmpty());
        assertTrue(CustomContentHooks.empty().problems(questWithTarget("myitems:ruby")).isEmpty(),
                "裸 id 可能来自任一家，也可能只是写错了材质名，不在这里误报");
    }

    @Test
    @DisplayName("前缀解析：大小写不敏感，逗号多值里任何一段都算")
    void prefixParsing() {
        assertEquals(List.of("itemsadder:"), CustomContentHooks.usedPrefixes("ItemsAdder:x:y"));
        assertEquals(List.of("craftengine:"), CustomContentHooks.usedPrefixes("STONE, craftengine:a:b"));
        assertEquals(List.of("itemsadder:", "craftengine:"),
                CustomContentHooks.usedPrefixes("itemsadder:a:b,craftengine:c:d"));
        assertTrue(CustomContentHooks.usedPrefixes("STONE").isEmpty());
        assertTrue(CustomContentHooks.usedPrefixes("").isEmpty());
    }

    @Test
    @DisplayName("空实现：没有别名、isEmpty 为真")
    void emptyHooks() {
        CustomContentHooks hooks = CustomContentHooks.empty();

        assertTrue(hooks.isEmpty());
        assertTrue(hooks.aliases(fakeItem()).isEmpty());
        assertFalse(hooks.hooks().iterator().hasNext());
    }

    // ---------------------------------------------------------------- 辅助

    /**
     * 假物品：真实的 {@link ItemStack} 需要服务端注册表，单测里造不出来。
     * <p>
     * 替身来源不看物品内容（真实实现里「这是不是自定义物品」由插件自己回答），
     * 因此一个 mock 就够。
     */
    private static ItemStack fakeItem() {
        return org.mockito.Mockito.mock(ItemStack.class);
    }

    private static Quest questWithTarget(String target) {
        return new Quest("q", "任务", List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("break_block", java.util.Map.of("target", target))),
                List.of(), 0.0, true);
    }
}
