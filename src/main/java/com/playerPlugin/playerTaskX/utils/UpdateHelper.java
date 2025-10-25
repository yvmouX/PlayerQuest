package com.playerPlugin.playerTaskX.utils;

import com.playerPlugin.playerTaskX.consts.common;
import org.bukkit.ChatColor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;

public class UpdateHelper {
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
        int latest;
        int current;
        ArrayList<String> currentVersionList = new ArrayList<>(Arrays.asList(currentVersion.split("\\.")));
        ArrayList<String> latestVersionList = new ArrayList<>(Arrays.asList(latestVersion.split("\\.")));
        for (int i = 0; i < Math.min(currentVersionList.size(), latestVersionList.size()); i++) {
            current = Integer.parseInt(currentVersionList.get(i));
            latest = Integer.parseInt(latestVersionList.get(i));
            if (current < latest) {
                common.isLatest = false;
                log.info(ChatColor.GREEN, "有新版本可用，请前往 https://github.com/Findoutsider/PlayerTaskX/releases/latest 下载");
                break;
            }
            if (current > latest) {
                common.isLatest = true;
                break;
            }
        }

    }

}
