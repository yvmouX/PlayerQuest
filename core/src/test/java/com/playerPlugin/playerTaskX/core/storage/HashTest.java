package com.playerPlugin.playerTaskX.core.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构指纹的<b>格式</b>测试——功能测试在 {@code StructureFingerprintTest}，这里钉的是「值本身」。
 *
 * <h2>为什么需要它</h2>
 * 指纹存在 {@code player_quest.structure_hash} 里，读进度时与重算值比对：不一致就
 * <b>清空该任务进度并告警</b>。因此换掉哈希实现（哪怕只是把「整串转十六进制再截断」
 * 改成「先取字节再转十六进制」这种看起来等价的写法）时，只要有一个字节的偏差，
 * 全部在线玩家的进度会在下次登录时被静默重置——不报错、不崩溃，只是进度没了。
 *
 * <p>下面那两个常量就是旧实现（{@code Character.forDigit} 逐字节拼十六进制后
 * {@code substring(0, 12)}）的输出，换实现时必须仍然相等。
 */
class HashTest {

    @Test
    @DisplayName("指纹是 SHA-256 十六进制的前 12 位（与旧实现逐字相同）")
    void fingerprintMatchesStoredFormat() {
        assertEquals("ba7816bf8f01", Hash.fingerprint("abc"),
                "『abc』的 SHA-256 以 ba7816bf8f01cfea… 开头，指纹必须取它的前 12 位");
        assertEquals("e3b0c44298fc", Hash.fingerprint(""),
                "空串同样有定值：e3b0c44298fc1c14…");
    }

    @Test
    @DisplayName("指纹固定 12 位小写十六进制，且同一输入稳定")
    void fingerprintShape() {
        String text = "break_block\u000164\u0001{target=STONE}\u0002";
        String first = Hash.fingerprint(text);

        assertEquals(12, first.length(), "长度变了就等于全部玩家的旧指纹都对不上");
        assertTrue(first.matches("[0-9a-f]{12}"), "只允许小写十六进制: " + first);
        assertEquals(first, Hash.fingerprint(text), "同一输入必须得到同一指纹");
    }

    @Test
    @DisplayName("不同结构得到不同指纹（否则「定义变了」检测不出来）")
    void differentTextDifferentFingerprint() {
        assertTrue(!Hash.fingerprint("a\u00011\u0002b\u00012\u0002")
                        .equals(Hash.fingerprint("b\u00012\u0002a\u00011\u0002")),
                "只是调换目标顺序也必须换指纹——进度按下标记录，顺序变了旧进度就错位了");
    }

    @Test
    @DisplayName("null 输入返回空串（旧实现如此，别改成 NPE 或 \"null\" 的哈希）")
    void nullTextIsEmpty() {
        assertEquals("", Hash.fingerprint(null));
    }
}
