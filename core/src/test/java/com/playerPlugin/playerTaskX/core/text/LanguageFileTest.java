package com.playerPlugin.playerTaskX.core.text;

import cn.yvmou.ylib.logger.Logger;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.message.MessageServiceImpl;
import cn.yvmou.ylib.message.MessageSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 语言文件「键」的约束测试。
 *
 * <p>要防的是「不报错的错误」：YAML 1.1 把裸写的 {@code yes} / {@code no} / {@code on} / {@code off}
 * 当作布尔值，于是 {@code common} 段下裸写的 {@code yes: "是"} 在 Bukkit 读进来之后键变成了
 * {@code common.true}。源文件看着没有任何问题，编译与肉眼检查都发现不了，
 * 只有玩家点开界面时才会看到一句「缺少语言键: common.yes」（实测踩过）。</p>
 *
 * <p>所以这里不检查文本，而是检查「Bukkit 实际读到了什么」：把源文件里<b>声明</b>的叶子键
 * 与 YamlConfiguration <b>加载</b>出来的叶子键对比，任何被 YAML 语义悄悄改名的键都会暴露；
 * 再用真实的 {@link MessageServiceImpl} 走一遍「jar 默认 → 用户文件 → 查询」，
 * 钉住玩家界面真正走的那条链路（缺键时界面显示的是 {@code Missing message: <键>}）。
 * 顺带钉住两种语言的键集合必须一致，避免只补了中文、英文回退到 "Missing message"。</p>
 */
class LanguageFileTest {

    /** 与 {@code language.available} 的默认值一致；缺一个就少一份语言。 */
    private static final List<String> LANGUAGES = List.of("zh_CN", "en");

    @Test
    @DisplayName("源文件里声明的每个键都能被 YamlConfiguration 原样读出来")
    void declaredKeysSurviveYamlLoading() {
        for (String code : LANGUAGES) {
            String source = readSource(code);
            Set<String> declared = declaredLeafKeys(source);
            Set<String> lost = new TreeSet<>(declared);
            lost.removeAll(leafKeys(load(code)));
            assertEquals(Set.of(), lost, "语言文件 " + code + ".yml 里有键被 YAML 解析改了名"
                    + "（键名若与 yes/no/on/off 这类布尔字面量同名，必须加引号）");
        }
    }

    @Test
    @DisplayName("两种语言声明的键集合完全一致")
    void languagesDeclareTheSameKeys() {
        Set<String> zh = declaredLeafKeys(readSource("zh_CN"));
        Set<String> en = declaredLeafKeys(readSource("en"));
        assertEquals(zh, en, "zh_CN.yml 与 en.yml 的键集合必须一致，否则缺失的那门语言会显示 Missing message");
    }

    @Test
    @DisplayName("走一遍 MessageService：common.yes / common.no 查得到，界面才不会打出 Missing message")
    void messageServiceResolvesBoolLikeKeys(@TempDir Path dataFolder) {
        // 用真实资源 + 真实 YamlConfiguration 走一遍「jar 默认 → 用户文件 → 查询」，
        // 因为出问题的位置正是这条链路上的查询（日志里的「缺少语言键: common.yes」）。
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call ->
                LanguageFileTest.class.getClassLoader().getResourceAsStream(call.getArgument(0)));

        MessageService messages = new MessageServiceImpl(plugin, mock(Logger.class), MessageSettings.builder()
                .defaultLanguage("zh_CN")
                .availableLanguages("zh_CN", "en")
                .filePattern("%s.yml")
                .languageFolder("lang")
                .build());

        assertTrue(messages.has("common.yes"), "MessageService 查不到 common.yes（键名被 YAML 当成布尔值了？）");
        assertEquals("是", messages.raw("common.yes"));
        assertEquals("否", messages.raw("common.no"));
        assertEquals("无", messages.raw("common.none"), "common.none 一直正常，别让修复把它带坏");
    }

    // ---------- 读取 ----------

    /** 内置语言文件必须从类路径读：和插件从自己 jar 里读的是同一份资源。 */
    private static InputStream resource(String code) {
        InputStream stream = LanguageFileTest.class.getClassLoader()
                .getResourceAsStream("lang/" + code + ".yml");
        assertNotNull(stream, "测试类路径上找不到 lang/" + code + ".yml（processResources 没跑？）");
        return stream;
    }

    private static FileConfiguration load(String code) {
        try (InputStream stream = resource(code)) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("加载语言文件失败: " + code, e);
        }
    }

    private static String readSource(String code) {
        try (InputStream stream = resource(code)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取语言文件失败: " + code, e);
        }
    }

    /** 已加载配置里的叶子键（不含中间节点）。 */
    private static Set<String> leafKeys(FileConfiguration config) {
        Set<String> keys = new TreeSet<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * 源文件里声明的叶子键：按缩进还原层级，<b>不</b>走 YAML 解析器——
     * 走解析器就看不见「键被改名」这件事了，那正是本测试要抓的东西。
     */
    private static Set<String> declaredLeafKeys(String source) {
        Set<String> keys = new TreeSet<>();
        Deque<Integer> indents = new ArrayDeque<>();
        Deque<String> sections = new ArrayDeque<>();
        for (String line : source.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = trimmed.substring(0, colon).strip().replaceAll("^[\"']|[\"']$", "");
            if (!name.matches("[A-Za-z0-9_.-]+")) {
                continue;
            }
            int indent = line.indexOf(trimmed.charAt(0));
            while (!indents.isEmpty() && indents.peek() >= indent) {
                indents.pop();
                sections.pop();
            }
            String path = sections.isEmpty() ? name : sections.peek() + "." + name;
            if (trimmed.substring(colon + 1).strip().isEmpty()) {
                indents.push(indent);
                sections.push(path);
            } else {
                keys.add(path);
            }
        }
        return keys;
    }
}
