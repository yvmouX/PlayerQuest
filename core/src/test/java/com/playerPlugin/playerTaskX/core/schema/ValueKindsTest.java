package com.playerPlugin.playerTaskX.core.schema;

import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 值域判定与校验的测试。
 *
 * <p>这一层决定两件事，且两件事必须一致：选择器里能选到什么、服务端会不会把配置标红。
 * 判错的后果都是静默的——要么列出一个永远命中不了的值（挖苹果），
 * 要么把一个其实能用的值判成非法（把自定义内容 id 拦下来）。因此逐个钉住。
 *
 * <h2>这里只有「实体系」的断言</h2>
 * {@code Material.isItem()} 与 {@code Enchantment.values()} 都要读服务端注册表，
 * 单元测试环境里会抛异常——那是刻意的设计（见 {@code ValueKinds} 的 UNKNOWN 分支）：
 * 判断不了就放行，而不是满屏误报。因此材质与附魔的判定只能靠真机验证，
 * 这里钉住的是**能离线判断的那些**：生物能力、CustomFishing 清单、以及各种放行规则。
 */
class ValueKindsTest {

    @Test
    @DisplayName("实体能力来自 getEntityClass() 的接口，不维护清单")
    void entityKindsComeFromEntityClass() {
        assertTrue(ValueKinds.of(EntityType.SHEEP).containsAll(
                List.of(ValueKind.ENTITY, ValueKind.LIVING, ValueKind.BREEDABLE, ValueKind.SHEARABLE)));
        assertTrue(ValueKinds.of(EntityType.PIG).contains(ValueKind.BREEDABLE));
        assertFalse(ValueKinds.of(EntityType.PIG).contains(ValueKind.SHEARABLE), "猪不能剪毛");
        assertTrue(ValueKinds.of(EntityType.WOLF).contains(ValueKind.TAMEABLE));
        assertTrue(ValueKinds.of(EntityType.ZOMBIE).contains(ValueKind.LIVING));
        assertFalse(ValueKinds.of(EntityType.ZOMBIE).contains(ValueKind.BREEDABLE), "僵尸不能繁殖");
    }

    @Test
    @DisplayName("MythicMobs 的怪按活体处理：既能当击杀目标也能当交互对象")
    void mythicMobsAreLiving() {
        assertEquals(List.of(ValueKind.ENTITY, ValueKind.LIVING), List.copyOf(ValueKinds.mythicMob()));
    }

    @Test
    @DisplayName("校验：猪填进「剪切」要报出来，并说清它不是什么")
    void checkRejectsImpossibleEntityValues() {
        String problem = ValueKinds.check(List.of(ValueKind.SHEARABLE), "PIG", null);

        assertNotNull(problem, "给猪剪毛永远不可能命中，必须报出来");
        assertTrue(problem.contains("PIG") && problem.contains("可剪毛"), problem);
    }

    @Test
    @DisplayName("校验：实体能力对得上就放行，逗号多值逐个看")
    void checkAcceptsMatchingEntities() {
        assertNull(ValueKinds.check(List.of(ValueKind.SHEARABLE), "SHEEP", null));
        assertNull(ValueKinds.check(List.of(ValueKind.BREEDABLE), "COW", null));
        assertNull(ValueKinds.check(List.of(ValueKind.LIVING), "ZOMBIE,SKELETON", null));
        assertNotNull(ValueKinds.check(List.of(ValueKind.LIVING), "ZOMBIE,PIGEON", null),
                "多值里混进一个不存在的名字，同样要报——那一项就是死配置");
    }

    @Test
    @DisplayName("校验放行：留空、*、别家插件的 id，以及这个环境下判断不了的值")
    void checkAllowsOpenEndedValues() {
        List<ValueKind> shearable = List.of(ValueKind.SHEARABLE);
        for (String value : new String[]{"", "  ", "*", "SHEEP,*"}) {
            assertNull(ValueKinds.check(shearable, value, null), "「" + value + "」表示任意，不该报");
        }
        assertNull(ValueKinds.check(shearable, "craftengine:default:torch", null),
                "自定义方块是别家插件的 id，离线判断不了——插件缺失时另有专门的提示");
        assertNull(ValueKinds.check(shearable, "mythic:CustomSheep", null),
                "MythicMobs 的怪同样由 MythicMobsHook 单独提示");
        assertNotNull(ValueKinds.check(shearable, "NOT_AN_ENTITY_AT_ALL", null),
                "「可剪毛的生物」这个值域里只有实体：认不出来的名字就是死配置，该报");
    }

    @Test
    @DisplayName("材质与附魔在服务端起来之前判断不了：一律放行，不误报")
    void registryBackedKindsAreUnknownBeforeServerStart() {
        // 单元测试环境没有服务端注册表：Material.isItem() 与 Enchantment.values() 都会抛异常。
        // 结论必须是「放行」而不是「你写错了」——否则半个环境下校验会满屏误报
        assertNull(ValueKinds.check(List.of(ValueKind.BLOCK), "APPLE", null));
        assertNull(ValueKinds.check(List.of(ValueKind.ENCHANTMENT), "NOT_AN_ENCHANTMENT", null));
        assertTrue(ValueKinds.enchantments().isEmpty(), "拿不到注册表时返回空表，而不是抛异常");
    }

    @Test
    @DisplayName("校验：拿得到 CustomFishing 清单时，写错的鱼 id 要报出来")
    void checkFishAgainstTheRegistry() {
        List<ValueKind> fish = List.of(ValueKind.FISH);
        List<String> loot = List.of("my_custom_fish", "another_fish");

        assertNull(ValueKinds.check(fish, "my_custom_fish", loot));
        assertNull(ValueKinds.check(fish, "MY_CUSTOM_FISH", loot), "id 大小写不敏感");
        assertNotNull(ValueKinds.check(fish, "no_such_fish", loot),
                "清单在手就敢说它不存在——这正是「装了 CustomFishing 却写错 id」的唯一报警机会");
    }

    @Test
    @DisplayName("校验：拿不到 CustomFishing 清单时不敢说鱼 id 不匹配")
    void checkFishWithoutRegistryIsLenient() {
        assertNull(ValueKinds.check(List.of(ValueKind.FISH), "whatever_id", null));
    }

    @Test
    @DisplayName("值域是「或」：方块或实体字段里两边都合法")
    void kindsAreUnions() {
        List<ValueKind> blockOrEntity = List.of(ValueKind.BLOCK, ValueKind.ENTITY);

        assertNull(ValueKinds.check(blockOrEntity, "VILLAGER", null), "实体那一支成立即可");
        assertNotNull(ValueKinds.check(blockOrEntity, "PIGEON", null),
                "既不是方块也不是实体：右键交互点不到它");
    }
}
