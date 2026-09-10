package com.playerPlugin.playerTaskX.core.text;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 向玩家发送 actionbar 与 title。
 *
 * <h2>入口约定</h2>
 * 两个方法都接受<b>原始文本</b>（MiniMessage 或 {@code &} 颜色码皆可），
 * 内部统一渲染后发送。把转换收敛到出口，避免「有的段落渲染了、有的没有」。
 *
 * <h2>actionbar 的实现选择</h2>
 * Spigot 没有 actionbar API，常见替代是「空 title + 副标题」，但那在<b>屏幕中央</b>，
 * 会严重遮挡视野。Paper / Folia / Canvas 提供 {@code sendActionBar(String)}，因此：
 * <ol>
 *   <li>优先反射调用它 —— 真正的动作栏位置；</li>
 *   <li>服务端确实没有时才退回副标题方案，并在日志里明确记录。</li>
 * </ol>
 *
 * <h2>为什么要传 {@code §} 色码而不是 MiniMessage</h2>
 * 实测发现 Paper 的 {@code sendActionBar(String)} <b>不解析 MiniMessage</b>：
 * 传 {@code <yellow>文字} 会把标签原样显示给玩家（真实线上截图证实）。
 * 所以这里传传统 {@code §} 色码——这是该类字符串接口的通行格式。
 * 文本的 MiniMessage 解析由 {@link TextRenderer} 在我们这一侧完成。
 */
public final class PlayerNotifier {

    /** 解析结果缓存；{@code null} 表示服务端不提供该 API。 */
    private static Method actionBarMethod;
    private static boolean actionBarResolved;

    private PlayerNotifier() {
    }

    /**
     * 发送 actionbar 文本（传入原始文本，内部渲染）。
     */
    public static void actionBar(Player player, String rawText) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // 一律用 § 色码：sendActionBar(String) 不认 MiniMessage
        String legacy = TextUpgrader.toLegacy(rawText);
        Method method = resolveActionBar(player);
        if (method != null) {
            try {
                method.invoke(player, legacy);
                return;
            } catch (ReflectiveOperationException e) {
                // 反射失败则永久退回旧方案，避免每次刷进度都抛异常
                actionBarMethod = null;
                Bukkit.getLogger().warning("[PlayerTaskX] 调用 sendActionBar 失败，"
                        + "改用副标题位置显示进度: " + e);
            }
        }
        legacyActionBar(player, legacy);
    }

    /**
     * 发送 title 与副标题（传入原始文本，内部渲染）。
     */
    public static void title(Player player, String rawTitle, String rawSubtitle,
                             int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendTitle(TextRenderer.render(rawTitle), TextRenderer.render(rawSubtitle),
                fadeIn, stay, fadeOut);
    }

    /**
     * 探测服务端是否提供 {@code sendActionBar(String)}。
     * <p>
     * 用 {@code getMethod}（沿接口层次查找）而不是 {@code getDeclaredMethod}。
     * 探测结果会写日志，便于确认当前服务端实际走的是哪条路径。
     */
    private static Method resolveActionBar(Player player) {
        if (actionBarResolved) {
            return actionBarMethod;
        }
        actionBarResolved = true;
        try {
            actionBarMethod = player.getClass().getMethod("sendActionBar", String.class);
            Bukkit.getLogger().info("[PlayerTaskX] 进度将通过动作栏（actionbar）显示。");
        } catch (NoSuchMethodException e) {
            actionBarMethod = null;
            Bukkit.getLogger().info("[PlayerTaskX] 服务端未提供 sendActionBar(String)"
                    + "（非 Paper/Folia 系服务端），进度将改用副标题位置显示。");
        }
        return actionBarMethod;
    }

    /**
     * 最后手段：Spigot 上把文字放在副标题位置。
     * <p>
     * 注意这是屏幕中央，只在服务端确实没有 actionbar API 时才会走到这里。
     */
    private static void legacyActionBar(Player player, String legacyText) {
        player.sendTitle("", legacyText, 0, 30, 5);
    }
}
