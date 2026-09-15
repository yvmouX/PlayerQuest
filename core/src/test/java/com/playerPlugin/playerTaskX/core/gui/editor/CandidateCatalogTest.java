package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.gui.editor.CandidateCatalog.Candidate;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 候选清单的离线部分测试：单测没有服务端注册表，材质/附魔/鱼都取不到——那正是要钉住的「少列而不是抛异常」。
 * 图标名字交给客户端本地化、清单内容随版本变，因此两者都不在这里断言。
 */
class CandidateCatalogTest {

    @Test
    @DisplayName("值域为空返回空表；清单取不到的值域只是少列，绝不抛异常")
    void unavailableCatalogsDegradeQuietly() {
        assertTrue(CandidateCatalog.of(List.of()).isEmpty());
        assertTrue(CandidateCatalog.of(null).isEmpty());

        // 这些值域要么整份清单取不到，要么只有实体那一支成立，但都不许把异常抛给调用方
        assertDoesNotThrow(() -> CandidateCatalog.of(List.of(ValueKind.LIVING)));
        assertDoesNotThrow(() -> CandidateCatalog.of(List.of(ValueKind.ITEM, ValueKind.BLOCK, ValueKind.PLACEABLE,
                ValueKind.ENCHANTMENT, ValueKind.FISH, ValueKind.BREEDABLE)));
    }

    @Test
    @DisplayName("ENTITY 候选全是能解析的实体枚举名，且不含 UNKNOWN")
    void entityCandidatesAreRealEntityTypes() {
        List<Candidate> candidates = CandidateCatalog.of(List.of(ValueKind.ENTITY));

        assertFalse(candidates.isEmpty(), "实体枚举不依赖服务端注册表，单测里也该列得出来");
        for (Candidate candidate : candidates) {
            assertNotEquals("UNKNOWN", candidate.value(), "UNKNOWN 写进配置永远命中不了，不该进候选");
            assertDoesNotThrow(() -> EntityType.valueOf(candidate.value()), candidate.value() + " 不是实体枚举名");
            assertEquals(candidate.value(), candidate.note(), "note 就是管理员要照抄进配置的值");
        }
    }

    @Test
    @DisplayName("多值域合并按 value 去重：先出现者胜，重复的值域不会让候选翻倍")
    void mergedKindsAreDeduplicated() {
        List<Candidate> entityOnly = CandidateCatalog.of(List.of(ValueKind.ENTITY));
        List<Candidate> merged = CandidateCatalog.of(List.of(ValueKind.ENTITY, ValueKind.LIVING,
                ValueKind.BREEDABLE, ValueKind.TAMEABLE, ValueKind.SHEARABLE));

        assertFalse(merged.isEmpty(), "与 ENTITY 合并后不该变成空表");
        List<String> values = valuesOf(merged);
        assertEquals(valuesOf(entityOnly), values, "生物类值域都是 ENTITY 的子集，合并后 value 集合与顺序都不该变");
        assertEquals(values.size(), new LinkedHashSet<>(values).size(), "同一个 value 只该出现一次");
    }

    /** 候选的 value 序列，用来断言去重与顺序。 */
    private static List<String> valuesOf(List<Candidate> candidates) {
        List<String> values = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            values.add(candidate.value());
        }
        return values;
    }
}
