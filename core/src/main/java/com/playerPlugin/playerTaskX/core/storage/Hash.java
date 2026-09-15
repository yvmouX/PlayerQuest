package com.playerPlugin.playerTaskX.core.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 目标结构指纹：把任务的目标列表算成一个短摘要存进玩家记录。
 *
 * <p>下次读进度时比对——不一致说明定义变过，旧进度的下标已错位，
 * 必须重置而不是静默套用。见 {@code ProgressService#structureHash}。
 *
 * <p>用 SHA-256 而非 {@code String.hashCode()}：后者只有 32 位且碰撞容易，
 * 用它做「定义是否变过」的判断会出现"改了却没检测出来"，正是要防的情况。
 */
public final class Hash {

    /**
     * 指纹的十六进制字符数。
     * <p>
     * 取整数个字节（6 字节 = 12 个字符），这样「先取字节再转十六进制」与
     * 「整串转十六进制再截断」逐字相同——库里已有的指纹在换实现后仍然对得上，
     * 不会因为一次重构把全部玩家的进度判成「结构已变化」而重置。
     */
    private static final int FINGERPRINT_HEX_LENGTH = 12;

    private static final int FINGERPRINT_BYTES = FINGERPRINT_HEX_LENGTH / 2;

    /** 十六进制小写，与 {@code Character.forDigit} 的输出一致。 */
    private static final HexFormat HEX = HexFormat.of();

    private Hash() {
    }

    /** 目标列表的短指纹（{@value #FINGERPRINT_HEX_LENGTH} 位十六进制）。 */
    public static String fingerprint(String text) {
        if (text == null) {
            return "";
        }
        byte[] bytes = sha256(text);
        if (bytes.length <= FINGERPRINT_BYTES) {
            return HEX.formatHex(bytes);
        }
        return HEX.formatHex(bytes, 0, FINGERPRINT_BYTES);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，正常不会走到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
