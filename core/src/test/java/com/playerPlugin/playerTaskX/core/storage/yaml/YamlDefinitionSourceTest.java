package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code quests/} 与 {@code presets/} 目录的读取测试：文件名与 id 对不上、某个文件解析失败被静默忽略、两个文件抢同一个 id 时结果取决于文件系统顺序——这些错都不报错，因此连「告警有没有发出来」一起钉住。 */
class YamlDefinitionSourceTest {

    @Test
    @DisplayName("文件名就是 id：文件里不写 id 也能用")
    void fileNameIsTheId(@TempDir Path dir) throws IOException {
        write(dir.resolve("daily_mine.yml"), """
                name: 挖矿日常
                icon: STONE_PICKAXE
                type: DAILY
                objectives:
                  - type: break_block
                    properties: { target: STONE, amount: 64 }
                """);

        List<Quest> quests = loadQuests(dir, new ArrayList<>());

        assertEquals(1, quests.size());
        assertEquals("daily_mine", quests.get(0).id(), "文件名（去掉扩展名）应当成为 id");
        assertEquals("挖矿日常", quests.get(0).name());
        assertEquals(64, quests.get(0).objectives().get(0).integer("amount", 0));
    }

    @Test
    @DisplayName("内容里写了 id 则以内容为准（复制文件改内容时不会撞 id）")
    void contentIdWins(@TempDir Path dir) throws IOException {
        write(dir.resolve("some_file_name.yml"), """
                id: real_id
                name: 任务
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);

        List<Quest> quests = loadQuests(dir, new ArrayList<>());

        assertEquals("real_id", quests.get(0).id());
    }

    @Test
    @DisplayName("递归扫描子目录，且非 yml 文件被忽略")
    void scansSubdirectoriesAndIgnoresOtherFiles(@TempDir Path dir) throws IOException {
        write(dir.resolve("group/nested.yml"), """
                name: 子目录里的任务
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);
        write(dir.resolve("notes.txt"), "这不是定义");
        write(dir.resolve("backup.yml.bak"), "name: 备份");

        List<Quest> quests = loadQuests(dir, new ArrayList<>());

        assertEquals(1, quests.size());
        assertEquals("nested", quests.get(0).id());
    }

    @Test
    @DisplayName("坏文件只跳过它自己并告警，其余照常加载")
    void brokenFileIsSkippedWithWarning(@TempDir Path dir) throws IOException {
        write(dir.resolve("good.yml"), """
                name: 好任务
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);
        write(dir.resolve("broken.yml"), "name: [未闭合\n");
        List<String> warnings = new ArrayList<>();

        List<Quest> quests = loadQuests(dir, warnings);

        assertEquals(1, quests.size(), "一份写坏的 YAML 不该让整个目录失效");
        assertEquals("good", quests.get(0).id());
        assertEquals(1, warnings.size(), "跳过了什么必须说出来: " + warnings);
        assertTrue(warnings.get(0).contains("broken.yml"), warnings.get(0));
    }

    @Test
    @DisplayName("只有注释/空内容的文件静默跳过（留着写笔记是合理用法）")
    void commentOnlyFileIsIgnoredSilently(@TempDir Path dir) throws IOException {
        write(dir.resolve("notes.yml"), "# 这里记点东西\n# 没有真正的定义\n");
        List<String> warnings = new ArrayList<>();

        List<Quest> quests = loadQuests(dir, warnings);

        assertTrue(quests.isEmpty());
        assertTrue(warnings.isEmpty(), "空文件每次都刷警告只会教人忽略警告: " + warnings);
    }

    @Test
    @DisplayName("两个文件抢同一个 id：按路径排序先到先得并告警（结果必须稳定）")
    void duplicateIdsAreDeterministic(@TempDir Path dir) throws IOException {
        write(dir.resolve("a_first.yml"), """
                id: same_id
                name: 第一个
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);
        write(dir.resolve("b_second.yml"), """
                id: same_id
                name: 第二个
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);
        List<String> warnings = new ArrayList<>();

        List<Quest> quests = loadQuests(dir, warnings);

        assertEquals(1, quests.size());
        assertEquals("第一个", quests.get(0).name(), "按文件路径排序取第一个");
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("same_id"), warnings.get(0));
    }

    @Test
    @DisplayName("目录不存在时返回空表，不抛异常")
    void missingDirectoryIsEmpty(@TempDir Path dir) {
        assertTrue(loadQuests(dir.resolve("nope"), new ArrayList<>()).isEmpty());
    }

    // ---------- 预设 ----------

    @Test
    @DisplayName("预设：kind 取自内容")
    void presetKindFromContent(@TempDir Path dir) throws IOException {
        write(dir.resolve("money_100.yml"), """
                kind: rewards
                name: 100 金币
                type: money
                properties: { amount: 100 }
                """);

        List<Preset> presets = loadPresets(dir, new ArrayList<>());

        assertEquals(1, presets.size());
        assertTrue(presets.get(0).isReward(), "kind: rewards 必须被认成奖励预设");
        assertEquals("money_100", presets.get(0).id());
    }

    @Test
    @DisplayName("预设：没写 kind 时按父目录名判断（presets/rewards/xxx.yml）")
    void presetKindFromFolder(@TempDir Path dir) throws IOException {
        write(dir.resolve(Preset.REWARDS + "/money_100.yml"), """
                name: 100 金币
                type: money
                properties: { amount: 100 }
                """);

        List<Preset> presets = loadPresets(dir, new ArrayList<>());

        assertEquals(1, presets.size());
        assertTrue(presets.get(0).isReward());
    }

    @Test
    @DisplayName("预设：既没有 kind 也无法从目录名判断时跳过并告警（猜错比少加载更难发现）")
    void presetWithoutKindIsSkipped(@TempDir Path dir) throws IOException {
        write(dir.resolve("mystery.yml"), """
                name: 不知道是哪类
                type: money
                properties: { amount: 100 }
                """);
        List<String> warnings = new ArrayList<>();

        assertTrue(loadPresets(dir, warnings).isEmpty());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("kind"), warnings.get(0));
    }

    @Test
    @DisplayName("预设：缺 type 时跳过并告警")
    void presetWithoutTypeIsSkipped(@TempDir Path dir) throws IOException {
        write(dir.resolve("no_type.yml"), """
                kind: objectives
                name: 没有类型
                """);
        List<String> warnings = new ArrayList<>();

        assertTrue(loadPresets(dir, warnings).isEmpty());
        assertTrue(warnings.get(0).contains("type"), warnings.get(0));
    }

    // ---------- 辅助 ----------

    private static List<Quest> loadQuests(Path dir, List<String> warnings) {
        DefinitionFolder folder = new DefinitionFolder(dir, "quests", warnings::add);
        return YamlDefinitions.questSources(folder).all();
    }

    private static List<Preset> loadPresets(Path dir, List<String> warnings) {
        DefinitionFolder folder = new DefinitionFolder(dir, "presets", warnings::add);
        return YamlDefinitions.presetSources(folder).all();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("只读标记：文件里有的 id 是只读的，查不到的 id 不是")
    void queryHelpers(@TempDir Path dir) throws IOException {
        write(dir.resolve("mine.yml"), """
                name: 挖矿
                objectives:
                  - type: chat
                    properties: { target: "", amount: 1 }
                """);
        YamlSources<Quest> sources = YamlDefinitions.questSources(
                new DefinitionFolder(dir, "quests", message -> { }));

        sources.all();
        assertTrue(sources.isFileDefined("mine"));
        assertFalse(sources.isFileDefined("other"));
        assertTrue(sources.find("mine").isPresent());
        assertFalse(sources.find("other").isPresent());
        assertEquals("quests/mine.yml", sources.location("mine").orElseThrow(),
                "提示里要能说出是哪个文件");
    }

    @Test
    @DisplayName("isEmpty 只认 YAML：放 README 或备份不算「已经有定义」")
    void isEmptyIgnoresNonYamlFiles(@TempDir Path dir) throws IOException {
        DefinitionFolder folder = new DefinitionFolder(dir, "quests", message -> { });

        assertTrue(folder.isEmpty(), "目录不存在也算空");
        write(dir.resolve("README.md"), "放点说明");
        write(dir.resolve("backup.json"), "{}");
        assertTrue(folder.isEmpty(), "非 YAML 文件不该让插件以为管理员已经有定义了");

        write(dir.resolve("nested/mine.yml"), "name: 挖矿\n");
        assertFalse(folder.isEmpty());
    }

    @Test
    @DisplayName("writeOnce 只创建、绝不覆盖")
    void writeOnceNeverOverwrites(@TempDir Path dir) throws IOException {
        DefinitionFolder folder = new DefinitionFolder(dir, "quests", message -> { });
        Path file = folder.pathOf("mine");

        assertTrue(folder.writeOnce(file, "name: 第一次\n"));
        assertFalse(folder.writeOnce(file, "name: 第二次\n"), "第二次必须什么都不做");
        assertEquals("name: 第一次\n", Files.readString(file), "已有文件的内容不能被改写");
        assertEquals(dir.resolve("mine.yml"), file, "文件名固定带 .yml");
    }
}
