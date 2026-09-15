package com.playerPlugin.playerTaskX.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构指纹的「值本身」测试（功能测试见 {@code StructureFingerprintTest}）：指纹存在 {@code player_quest.structure_hash} 里，与重算值不一致就清空该任务进度并告警，因此换哈希实现只要差一个字节，全部在线玩家的进度会在下次登录时被静默重置。
 * 下面那两个常量是旧实现的输出，换实现后必须仍然相等。
 */
class StructureFingerprintFormatTest {

    @Test
    @DisplayName("指纹是 SHA-256 十六进制的前 12 位（与旧实现逐字相同）")
    void fingerprintMatchesStoredFormat() {
        assertEquals("ba7816bf8f01", StructureFingerprint.fingerprint("abc"),
                "『abc』的 SHA-256 以 ba7816bf8f01cfea… 开头，指纹必须取它的前 12 位");
        assertEquals("e3b0c44298fc", StructureFingerprint.fingerprint(""),
                "空串同样有定值：e3b0c44298fc1c14…");
    }

    @Test
    @DisplayName("指纹固定 12 位小写十六进制，且同一输入稳定")
    void fingerprintShape() {
        String text = "break_block\u000164\u0001{target=STONE}\u0002";
        String first = StructureFingerprint.fingerprint(text);

        assertEquals(12, first.length(), "长度变了就等于全部玩家的旧指纹都对不上");
        assertTrue(first.matches("[0-9a-f]{12}"), "只允许小写十六进制: " + first);
        assertEquals(first, StructureFingerprint.fingerprint(text), "同一输入必须得到同一指纹");
    }

    @Test
    @DisplayName("不同结构得到不同指纹（否则「定义变了」检测不出来）")
    void differentTextDifferentFingerprint() {
        assertTrue(!StructureFingerprint.fingerprint("a\u00011\u0002b\u00012\u0002")
                        .equals(StructureFingerprint.fingerprint("b\u00012\u0002a\u00011\u0002")),
                "只是调换目标顺序也必须换指纹——进度按下标记录，顺序变了旧进度就错位了");
    }

    @Test
    @DisplayName("null 输入返回空串（旧实现如此，别改成 NPE 或 \"null\" 的哈希）")
    void nullTextIsEmpty() {
        assertEquals("", StructureFingerprint.fingerprint(null));
    }
}
