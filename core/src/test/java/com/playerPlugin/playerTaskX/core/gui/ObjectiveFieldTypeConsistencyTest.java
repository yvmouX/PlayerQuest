package com.playerPlugin.playerTaskX.core.gui;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.FieldType;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标字段的「控件类型 + 取值域」约束测试。
 *
 * <p>这两件事写错的后果都是静默的：把「材质名」声明成 {@code STRING} 只会让编辑器给一个
 * 纯文本框，逼管理员去查 Bukkit 枚举名；而值域写宽了（例如挖掘方块的 target 只声明成
 * 「物品」）会让选择器把苹果、面包一并列出来——配出来的任务**永远不会命中**，
 * 玩家只会来问「我挖了怎么不涨」。两者编译期都看不出来，因此在这里逐个钉住。
 *
 * <p>新增目标类型时若把材质/实体字段写成 {@code STRING}、或忘了声明值域，
 * 本测试会失败并指出具体字段。
 */
class ObjectiveFieldTypeConsistencyTest {

    /** 与生产注册共用同一份清单（BuiltIns），不会出现「注册了但测试没跟上」。 */
    private static List<ObjectiveType> builtInObjectives() {
        return BuiltIns.objectives();
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

    private static FieldType typeOf(String typeId, String fieldKey) {
        return fieldOf(typeId, fieldKey).type();
    }

    /** 断言某个字段的取值域恰好是给定的这几个（顺序无关，语义是「或」）。 */
    private static void assertKinds(String typeId, String fieldKey, ValueKind... expected) {
        ConfigField field = fieldOf(typeId, fieldKey);
        assertEquals(FieldType.PICKER, field.type(),
                typeId + "." + fieldKey + " 应该是选择器（PICKER），否则编辑器不会给候选列表");
        assertEquals(Set.of(expected), Set.copyOf(field.kinds()),
                typeId + "." + fieldKey + " 的取值域不对：选择器会列出不该出现的东西，"
                        + "而服务端校验也会因此放过永远命中不了的值");
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
        // 猪不能剪毛、僵尸不能繁殖：值域写宽了，选择器就会给出这些永远不成立的组合
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
    @DisplayName("真正自由的文本字段仍保持 STRING，且不该声明值域")
    void freeTextFieldsStayString() {
        // 关键词与命令名是开放式输入，没有候选清单可选
        assertEquals(FieldType.STRING, typeOf("chat", "target"));
        assertEquals(FieldType.STRING, typeOf("command", "target"));
        assertTrue(fieldOf("chat", "target").kinds().isEmpty());
        assertTrue(fieldOf("command", "target").kinds().isEmpty());
    }

    @Test
    @DisplayName("每个目标类型都有 INTEGER 的 amount 字段")
    void everyObjectiveHasIntegerAmount() {
        Set<String> missing = new TreeSet<>();
        for (ObjectiveType type : builtInObjectives()) {
            ConfigField amount = type.schema().stream()
                    .filter(field -> "amount".equals(field.key()))
                    .findFirst()
                    .orElse(null);
            if (amount == null || amount.type() != FieldType.INTEGER) {
                missing.add(type.id());
            }
        }
        assertTrue(missing.isEmpty(),
                "以下目标类型缺少 INTEGER 的 amount 字段，编辑器与引擎都依赖这个统一约定：\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("说明里写着「留空表示任意」的字段，必须如实标记为非必填")
    void optionalFieldsAreNotMarkedRequired() {
        List<String> offenders = new ArrayList<>();
        for (ObjectiveType type : builtInObjectives()) {
            for (ConfigField field : type.schema()) {
                String hint = field.hint() == null ? "" : field.hint();
                if (!hint.contains("留空")) {
                    continue;
                }
                // 编辑器据此决定「可留空」提示与红色星号；标反了会给出与实际不符的提示
                if (field.required()) {
                    offenders.add(type.id() + "." + field.key() + " 说明里允许留空，却标成了必填");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下字段应使用允许留空的工厂方法（optionalItems / optionalEntities…）：\n  "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("字段 key 不重复，且都非空——重复会让表单静默覆盖")
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
    @DisplayName("kill 的 target 说明里要写明支持 mythic: 前缀——写不进表单的能力等于没有")
    void killTargetHintMentionsMythicMobs() {
        ConfigField target = BuiltIns.objective("kill").schema().stream()
                .filter(field -> "target".equals(field.key()))
                .findFirst()
                .orElseThrow();

        assertTrue(target.hint().contains("mythic:"),
                "「击杀 MythicMobs 自定义怪」只是 target 的写法，编辑器里唯一的提示就是这行 hint，"
                        + "没写就等于没有这个功能。实际: " + target.hint());
    }
}
