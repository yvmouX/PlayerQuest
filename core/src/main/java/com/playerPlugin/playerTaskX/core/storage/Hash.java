package com.playerPlugin.playerTaskX.core.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 短哈希工具。
 *
 * <p>两个用途：
 * <ul>
 *   <li><b>并发冲突检测</b>：编辑器保存前比对文件当前内容与打开时的哈希，
 *       不一致就拒绝覆盖（见 {@link QuestFileRepository#contentHash(String)}）。</li>
 *   <li><b>目标结构指纹</b>：把任务的目标列表算成一个短摘要存进玩家记录，
 *       下次读进度时比对——不一致说明定义变过，旧进度的下标已错位，
 *       必须重置而不是静默套用。见 {@code ProgressService}。</li>
 * </ul>
 *
 * <p>用 SHA-256 而非 {@code String.hashCode()}：后者只有 32 位且碰撞容易，
 * 用它做「定义是否变过」的判断会出现"改了却没检测出来"，正是要防的情况。
 */
public final class Hash {

    /** 指纹长度：够短便于写进存档，够长避免碰撞。 */
    private static final int FINGERPRINT_LENGTH = 12;

    private Hash() {
    }

    /** 完整 SHA-256（十六进制小写）。 */
    public static String sha256(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，正常不会走到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 截短的指纹，用于写进玩家存档。 */
    public static String fingerprint(String text) {
        String full = sha256(text);
        return full.length() <= FINGERPRINT_LENGTH ? full : full.substring(0, FINGERPRINT_LENGTH);
    }
}
