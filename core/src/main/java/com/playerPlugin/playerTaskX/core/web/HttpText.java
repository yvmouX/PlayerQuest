package com.playerPlugin.playerTaskX.core.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 极简 HTTP 文本获取，只服务于「下载语言文件」这一件事。
 *
 * <h2>为什么用 JDK 自带的 HttpClient</h2>
 * 需求只有「GET 一个小文本文件」，为此引入 HTTP 客户端库不值得；
 * JDK 11+ 的 {@link HttpClient} 够用且没有额外依赖。
 *
 * <h2>为什么处处设超时、处处不抛异常</h2>
 * 这些请求都是<b>尽力而为</b>的附属功能（拿中文译名），失败只应导致退回英文名，
 * 绝不能把异常抛到调用方、更不能影响插件启用。因此所有失败都收敛成 {@code null}。
 * 超时也必须设：不少服务端在受限网络里，出站请求会一直挂着。
 */
final class HttpText {

    /** 单个请求的超时；有界等待，避免受限网络下长时间挂住。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpText() {
    }

    /**
     * GET 一个文本资源，失败返回 {@code null}（不抛异常）。
     *
     * @return 响应体；非 2xx、超时、网络不可达等一律返回 null
     */
    static String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "PlayerTaskX (Minecraft plugin; language file fetch)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            // 恢复中断标志，交给上层决定是否继续
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
