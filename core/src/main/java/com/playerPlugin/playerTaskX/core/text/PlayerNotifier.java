package com.playerPlugin.playerTaskX.core.text;

import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 向玩家发送 actionbar 与 title，入口收原始文本、内部统一渲染成 {@code §} 色码。
 * actionbar 优先反射调用 {@code sendActionBar}（空 title 的替代方案在屏幕中央会遮视野），且它不解析 MiniMessage，传标签会把标签原样显示给玩家。
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
        String legacy = TextRenderer.render(rawText);
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
