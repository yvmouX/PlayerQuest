package com.playerPlugin.playerTaskX.core.gui;

import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.core.objective.InteractObjective;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 任务详情界面「目标图标推导」的测试。
 *
 * <p>图标完全由类型的 {@code schema()} 推导：界面不认识任何一种目标类型 id，
 * 因此新增目标类型只要如实声明字段类型就能自动得到像样的图标。这里覆盖的正是
 * 该机制——它不依赖任何 Bukkit 运行时，是纯映射逻辑，值得钉住。</p>
 */
class QuestDetailMenuTest {

    private ObjectiveRegistryImpl objectiveTypes;

    @BeforeEach
    void setUp() {
        objectiveTypes = new ObjectiveRegistryImpl();
        objectiveTypes.register(BuiltIns.objective("break_block"));
        objectiveTypes.register(BuiltIns.objective("kill"));
        objectiveTypes.register(new InteractObjective());
    }

    private static QuestObjective objective(String type, Map<String, Object> properties) {
        return QuestObjective.of(type, properties);
    }

    @Test
    @DisplayName("有 MATERIAL 字段时直接用该材质做图标（挖钻石矿显示钻石矿）")
    void materialFieldBecomesIcon() {
        assertEquals(Material.DIAMOND_ORE,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("break_block",
                        Map.of("target", "DIAMOND_ORE", "amount", 64))));
    }

    @Test
    @DisplayName("ENTITY 字段用刷怪蛋做图标（击杀僵尸显示僵尸刷怪蛋）")
    void entityFieldBecomesSpawnEgg() {
        assertEquals(Material.ZOMBIE_SPAWN_EGG,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("kill",
                        Map.of("target", "ZOMBIE", "amount", 20))));
    }

    @Test
    @DisplayName("材质字段留空或 * 时退回纸，不硬套 schema 默认值")
    void blankMaterialFallsBackToPaper() {
        // 留空表示「任意方块」：此时显示钻石矿会让人误以为任务只认钻石矿
        assertEquals(Material.PAPER,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("break_block",
                        Map.of("target", "", "amount", 64))));
        assertEquals(Material.PAPER,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("break_block",
                        Map.of("target", "*", "amount", 64))));
    }

    @Test
    @DisplayName("TARGET 字段（方块或实体皆可）两种取值都能推出图标")
    void targetFieldHandlesBothBlockAndEntity() {
        // 交互目标是方块：直接显示该方块
        assertEquals(Material.CHEST,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("interact",
                        Map.of("target", "CHEST", "mode", "ANY", "amount", 1))));
        // 交互目标是实体：退回刷怪蛋
        assertEquals(Material.VILLAGER_SPAWN_EGG,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("interact",
                        Map.of("target", "VILLAGER", "mode", "ANY", "amount", 1))));
        // 留空表示「任意对象」，退回纸而不是报错，也不是硬套 schema 默认值
        assertEquals(Material.PAPER,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("interact",
                        Map.of("target", "", "mode", "ANY", "amount", 1))));
    }

    @Test
    @DisplayName("多值材质取第一个能识别的（逗号分隔）")
    void multipleValuesTakeFirstMatch() {
        assertEquals(Material.DIAMOND_ORE,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("break_block",
                        Map.of("target", "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE", "amount", 16))));
    }

    @Test
    @DisplayName("完全未知的类型 id 退回纸，不抛异常")
    void unknownTypeFallsBackToPaper() {
        assertEquals(Material.PAPER,
                QuestDetailMenu.objectiveIcon(objectiveTypes, objective("no_such_type", Map.of())));
    }
}
