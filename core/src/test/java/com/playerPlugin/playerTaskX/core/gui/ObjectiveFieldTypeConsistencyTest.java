package com.playerPlugin.playerTaskX.core.gui;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.FieldType;
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
 * 目标字段「语义类型」的约束测试。
 *
 * <p>把「材质名」声明成 {@code STRING} 不会编译报错、也不会让任何断言变红，
 * 唯一后果是编辑器只能给管理员一个纯文本框，逼他去查 Bukkit 枚举名——
 * 钓鱼、繁殖、剪毛、交互四处原本就是这样，属于「不报错的错误」。</p>
 *
 * <p>因此这里把「哪个字段该是什么类型」显式钉住。新增目标类型时若把材质/实体
 * 字段写成 {@code STRING}，本测试会失败并指出具体字段。</p>
 */
class ObjectiveFieldTypeConsistencyTest {

    /** 与生产注册共用同一份清单（BuiltIns），不会出现「注册了但测试没跟上」。 */
    private static List<ObjectiveType> builtInObjectives() {
        return BuiltIns.objectives();
    }

    private static FieldType typeOf(String typeId, String fieldKey) {
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
        return field.type();
    }

    @Test
    @DisplayName("取材质名的字段都声明成 MATERIAL，编辑器才会给选择器")
    void materialFieldsUseMaterialType() {
        assertEquals(FieldType.MATERIAL, typeOf("break_block", "target"));
        assertEquals(FieldType.MATERIAL, typeOf("place_block", "target"));
        assertEquals(FieldType.MATERIAL, typeOf("craft", "target"));
        assertEquals(FieldType.MATERIAL, typeOf("consume", "target"));
        assertEquals(FieldType.MATERIAL, typeOf("submit", "target"));
        // 钓获物是物品材质（COD / SALMON），曾经被误声明为纯文本
        assertEquals(FieldType.MATERIAL, typeOf("fish", "target"));
    }

    @Test
    @DisplayName("取实体名的字段都声明成 ENTITY")
    void entityFieldsUseEntityType() {
        assertEquals(FieldType.ENTITY, typeOf("kill", "target"));
        assertEquals(FieldType.ENTITY, typeOf("tame", "target"));
        // 繁殖出的幼崽与被剪的羊都是实体，曾经被误声明为纯文本
        assertEquals(FieldType.ENTITY, typeOf("breed", "target"));
        assertEquals(FieldType.ENTITY, typeOf("shear", "target"));
    }

    @Test
    @DisplayName("方块或实体皆可的字段声明成 TARGET")
    void blockOrEntityFieldsUseTargetType() {
        assertEquals(FieldType.TARGET, typeOf("interact", "target"));
    }

    @Test
    @DisplayName("真正自由的文本字段仍保持 STRING（不该被上面的规则误伤）")
    void freeTextFieldsStayString() {
        // 这些是关键词、附魔名、命令名——开放式输入，没有候选清单可选
        assertEquals(FieldType.STRING, typeOf("chat", "target"));
        assertEquals(FieldType.STRING, typeOf("enchant", "target"));
        assertEquals(FieldType.STRING, typeOf("command", "target"));
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
                "以下字段应使用 optionalMaterial / optionalEntity / optionalBlockOrEntity：\n  "
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
