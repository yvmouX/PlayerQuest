package com.playerPlugin.playerTaskX.api;

import cn.yvmou.ylib.api.services.LoggerService;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * API 提供者
 * 用于获取 PlayerTaskXAPI 实例
 */
public final class PlayerTaskXProvider {
    private static TaskAPI api;

    private PlayerTaskXProvider() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 设置 API 实例（仅供插件内部使用）
     * @param instance API 实例
     */
    public static void setApi(TaskAPI instance) {
        if (api != null) {
            throw new IllegalStateException("API instance already set");
        }
        api = instance;
    }

    /**
     * 获取 API 实例
     * @return API 实例
     * @throws IllegalStateException 如果 API 未初始化
     */
    public static TaskAPI getApi() {
        if (api == null) {
            throw new IllegalStateException("PlayerTaskX API is not initialized. Make sure PlayerTaskX plugin is loaded.");
        }
        return api;
    }

    /**
     * 检查 API 是否已加载
     * @return 是否已加载
     */
    public static boolean isLoaded() {
        return api != null;
    }

    /**
     * 重置 API 实例（仅供插件内部使用）
     */
    public static void reset() {
        api = null;
    }

    public static class UpdateHelper {
        private final LoggerService log;

        public UpdateHelper(LoggerService log) {
            this.log = log;
        }

        public void checkUpdate(String currentVersion) {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/Findoutsider/DeathPunish/releases/latest"))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // 解析 JSON 响应
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                String name = (String) jsonObject.get("name");

                compareVersion(currentVersion, name);


            } catch (IOException | InterruptedException | ParseException e) {
                throw new RuntimeException(e);
            }
        }

        private void compareVersion(String currentVersion, String latestVersion) {
//            if (latestVersion == null) {
//                log.info(ChatColor.RED, "无法获取最新版本信息");
//                return;
//            }
//
//            int latest;
//            int current;
//            ArrayList<String> currentVersionList = new ArrayList<>(Arrays.asList(currentVersion.split("\\.")));
//            ArrayList<String> latestVersionList = new ArrayList<>(Arrays.asList(latestVersion.split("\\.")));
//            for (int i = 0; i < Math.min(currentVersionList.size(), latestVersionList.size()); i++) {
//                current = Integer.parseInt(currentVersionList.get(i));
//                latest = Integer.parseInt(latestVersionList.get(i));
//                if (current < latest) {
//                    Common.isLatest = false;
//                    log.info(ChatColor.GREEN, "有新版本可用，请前往 https://github.com/Findoutsider/PlayerTaskX/releases/latest 下载");
//                    break;
//                }
//                if (current > latest) {
//                    Common.isLatest = true;
//                    break;
//                }
//            }

        }

    }
}
