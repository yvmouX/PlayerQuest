package com.playerPlugin.playerTaskX.core.registry;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.playerPlugin.playerTaskX.core.objective.ObjectiveBuiltIns;

/**
 * 目标字段「值域」的约束测试：写宽了会放过永远命中不了的值（配出来的任务永不命中，玩家只来问「我挖了怎么不涨」），漏写则让校验彻底闭嘴，两者编译期都看不出来。
 * 新增目标类型时忘了声明值域，本测试会失败并指出具体字段。
 */
class ObjectiveFieldDomainTest {

    /** 与生产注册共用同一份清单（ObjectiveBuiltIns），不会出现「注册了但测试没跟上」。 */
    private static List<ObjectiveType> builtInObjectives() {
        return ObjectiveBuiltIns.all();
    }

    private static ConfigField fieldOf(String typeId, String fieldKey) {
        ObjectiveType type = builtInObjectives().stream()
                .filter(candidate -> candidate.id().equals(typeId))
                .findFirst()
                .orElse(null);
        assertNotNull(type, "找不到目标类型 " + typeId + "（类型 id 被改名了？）");
        ConfigField field = type.schema().stream()
                .filter(candidate -> candidate.key().equals(fieldKey))
                .findFirst()
                .orElse(null);
        assertNotNull(field, typeId + " 缺少字段 " + fieldKey);
        return field;
    }

    /** 断言某个字段的取值域恰好是给定的这几个（顺序无关，语义是「或」）。 */
    private static void assertKinds(String typeId, String fieldKey, ValueKind... expected) {
        assertEquals(Set.of(expected), Set.copyOf(fieldOf(typeId, fieldKey).kinds()),
                typeId + "." + fieldKey + " 的取值域不对：GUI 会推出错误的图标，"
                        + "而校验也会因此放过（或误拦）永远命中不了的值");
    }

    @Test
    @DisplayName("挖掘方块只声明「方块」：食物虽然也是材质，但永远不可能被挖到")
    void breakBlockAcceptsBlocksOnly() {
        assertKinds("break_block", "target", ValueKind.BLOCK);
    }

    @Test
    @DisplayName("放置方块声明「可放置」：基岩、刷怪笼这类没有物品形态的方块放不下去")
    void placeBlockAcceptsPlaceableOnly() {
        assertKinds("place_block", "target", ValueKind.PLACEABLE);
    }

    @Test
    @DisplayName("合成 / 消耗 / 提交 / 垂钓声明「物品」")
    void itemFieldsAcceptItems() {
        assertKinds("craft", "target", ValueKind.ITEM);
        assertKinds("consume", "target", ValueKind.ITEM);
        assertKinds("submit", "target", ValueKind.ITEM);
        // 钓获物是物品材质（COD / SALMON），曾经被误声明为纯文本
        assertKinds("fish", "target", ValueKind.ITEM);
    }

    @Test
    @DisplayName("击杀声明「活体」；剪毛 / 繁殖 / 驯服各自声明对应的生物能力")
    void entityFieldsDeclareTheirCapability() {
        assertKinds("kill", "target", ValueKind.LIVING);
        // 猪不能剪毛、僵尸不能繁殖：值域写宽了，校验就会放过这些永远不成立的组合
        assertKinds("shear", "target", ValueKind.SHEARABLE);
        assertKinds("breed", "target", ValueKind.BREEDABLE);
        assertKinds("tame", "target", ValueKind.TAMEABLE);
    }

    @Test
    @DisplayName("交互声明「方块或实体」：右键的对象两种都可能是")
    void interactAcceptsBlocksAndEntities() {
        assertKinds("interact", "target", ValueKind.BLOCK, ValueKind.ENTITY);
    }

    @Test
    @DisplayName("附魔声明「附魔」，自定义钓鱼声明「鱼」")
    void specialDomainsAreDeclared() {
        assertKinds("enchant", "target", ValueKind.ENCHANTMENT);
        assertKinds("custom_fish", "target", ValueKind.FISH);
    }

    @Test
    @DisplayName("真正自由的文本字段不声明值域，校验才不会去拦它")
    void freeTextFieldsHaveNoDomain() {
        // 关键词与命令名是开放式输入，没有候选清单可判
        assertTrue(fieldOf("chat", "target").kinds().isEmpty());
        assertTrue(fieldOf("command", "target").kinds().isEmpty());
    }

    @Test
    @DisplayName("每个目标类型都有 amount 字段，且它不是名字类字段")
    void everyObjectiveDeclaresAmount() {
        Set<String> missing = new TreeSet<>();
        for (ObjectiveType type : builtInObjectives()) {
            ConfigField amount = type.schema().stream()
                    .filter(field -> "amount".equals(field.key()))
                    .findFirst()
                    .orElse(null);
            if (amount == null || !amount.kinds().isEmpty()) {
                missing.add(type.id());
            }
        }
        assertTrue(missing.isEmpty(),
                "以下目标类型缺少（或给错了）amount 字段，GUI 与引擎都依赖这个统一约定：\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("字段 key 不重复，且都非空——重复会让后一个字段顶掉前一个")
    void fieldKeysAreUniqueAndNonBlank() {
        List<String> offenders = new ArrayList<>();
        for (ObjectiveType type : builtInObjectives()) {
            Set<String> seen = new TreeSet<>();
            for (ConfigField field : type.schema()) {
                if (field.key() == null || field.key().isBlank()) {
                    offenders.add(type.id() + " 有空 key");
                } else if (!seen.add(field.key())) {
                    offenders.add(type.id() + " 重复的字段 key: " + field.key());
                }
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("值域成员必须有字段在声明它们，否则 ValueKind 就是死语义")
    void everyValueKindIsUsedBySomeField() {
        Set<ValueKind> declared = new TreeSet<>();
        for (ObjectiveType type : builtInObjectives()) {
            for (ConfigField field : type.schema()) {
                declared.addAll(field.kinds());
            }
        }
        assertEquals(Set.of(ValueKind.values()), declared,
                "有值域没有任何字段声明（或字段用了不存在的值域）："
                        + "那些分支在 ValueKinds 里永远不会被走到，应当连常量一起删掉");
    }

    @Test
    @DisplayName("kill 的 target 说明里要写明支持 mythic: 前缀——这是玩家能看到的唯一提示")
    void killTargetHintMentionsMythicMobs() {
        ConfigField target = fieldOf("kill", "target");

        assertTrue(target.hint().contains("mythic:"),
                "「击杀 MythicMobs 自定义怪」只是 target 的写法，唯一的提示就是这行 hint，"
                        + "没写就等于没有这个功能。实际: " + target.hint());
    }
}
