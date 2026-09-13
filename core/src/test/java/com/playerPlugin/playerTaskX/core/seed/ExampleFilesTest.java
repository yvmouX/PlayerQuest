package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.yaml.DefinitionFolder;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 示例文件（{@code quests/} 与 {@code presets/} 下的出厂示例）的测试。
 *
 * <p>这里钉的是三类「不报错的错」：文件铺完读不回来（写出去的 YAML 用了别的语义）、
 * 与前缀一起漏改的前置 id（文件里的任务链悄悄指向库里的任务）、
 * 以及重启时把管理员删掉的示例又补回来。</p>
 */
class ExampleFilesTest {

    private static final double REFRESH_COST = 1000.0;

    private final List<String> warnings = new ArrayList<>();

    @Test
    @DisplayName("目录为空时铺一整套：任务与预设各一份文件")
    void writesWholeSetWhenEmpty(@TempDir Path dir) {
        Path quests = dir.resolve("quests");
        Path presets = dir.resolve("presets");

        assertEquals(ExampleQuests.all(REFRESH_COST).size(),
                ExampleFiles.writeQuests(folder(quests, "quests"), ExampleQuests.all(REFRESH_COST)));
        assertEquals(ExamplePresets.all().size(),
                ExampleFiles.writePresets(folder(presets, "presets"), ExamplePresets.all()));

        assertTrue(Files.isRegularFile(quests.resolve("example_file_daily_mine.yml")),
                "文件名就是 id，且带上了与库里那套区分的前缀");
        assertTrue(Files.isRegularFile(presets.resolve("example_file_mine-stone.yml")));
        assertTrue(warnings.isEmpty(), "正常铺示例不该产生告警: " + warnings);
    }

    @Test
    @DisplayName("铺出来的任务能原样读回来（含目标、奖励、前置与刷新费用）")
    void questFilesRoundTrip(@TempDir Path dir) {
        ExampleFiles.writeQuests(folder(dir, "quests"), ExampleQuests.all(REFRESH_COST));

        Map<String, Quest> loaded = YamlDefinitions.questSources(folder(dir, "quests")).all().stream()
                .collect(Collectors.toMap(Quest::id, Function.identity()));
        List<String> expectedIds = ExampleQuests.all(REFRESH_COST).stream()
                .map(quest -> ExampleFiles.fileId(quest.id())).sorted().toList();

        assertEquals(expectedIds, loaded.keySet().stream().sorted().toList(),
                "id 只差前缀，一个不多一个不少");
        for (Quest quest : ExampleQuests.all(REFRESH_COST)) {
            Quest actual = loaded.get(ExampleFiles.fileId(quest.id()));
            assertEquals(quest.name(), actual.name(), quest.id());
            assertEquals(quest.description(), actual.description(), quest.id());
            assertEquals(quest.icon(), actual.icon(), quest.id());
            assertEquals(quest.category(), actual.category(), quest.id());
            assertEquals(quest.type(), actual.type(), quest.id());
            assertEquals(quest.refreshCost(), actual.refreshCost(), 1.0e-9, quest.id());
            assertEquals(quest.objectives(), actual.objectives(), quest.id());
            assertEquals(quest.rewards(), actual.rewards(), quest.id());
        }
    }

    @Test
    @DisplayName("前置 id 也跟着换前缀：文件里的任务链不会串到库里的任务上")
    void prerequisitesFollowThePrefix(@TempDir Path dir) {
        ExampleFiles.writeQuests(folder(dir, "quests"), ExampleQuests.all(REFRESH_COST));

        Quest build = YamlDefinitions.questSources(folder(dir, "quests")).find("example_file_daily_build")
                .orElseThrow();

        assertEquals(List.of("example_file_daily_mine"), build.prerequisites(),
                "漏改前缀会让文件里的前置指向库里的那条，两套示例被悄悄串起来");
    }

    @Test
    @DisplayName("文件里不写 id：文件名即 id，复制改名就是一个新任务")
    void filesDoNotRepeatTheId(@TempDir Path dir) throws IOException {
        ExampleFiles.writeQuests(folder(dir, "quests"), ExampleQuests.all(REFRESH_COST));

        String text = Files.readString(dir.resolve("example_file_daily_mine.yml"), StandardCharsets.UTF_8);
        assertFalse(text.lines().anyMatch(line -> line.startsWith("id:")),
                "文件名已经说明了一切，正文再写一遍 id 只会让人以为改文件名没用");
        assertTrue(text.startsWith("#"), "文件头写了怎么改、怎么删，先看到的是它而不是字段");
    }

    @Test
    @DisplayName("目录里已经有任何 YAML 就一个字节都不写")
    void skipsWhenFolderHasAnyDefinition(@TempDir Path dir) throws IOException {
        Path mine = dir.resolve("my_own_quest.yml");
        Files.writeString(mine, "name: 我自己的任务\n", StandardCharsets.UTF_8);

        assertEquals(0, ExampleFiles.writeQuests(folder(dir, "quests"), ExampleQuests.all(REFRESH_COST)));

        assertEquals(List.of("my_own_quest.yml"), listYaml(dir),
                "管理员删掉几个示例、或放了自己的定义之后，重启不该把示例补回来");
        assertEquals("name: 我自己的任务\n", Files.readString(mine, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("预设：同样只在空目录里铺，且读回来与库里那套等价")
    void presetFilesRoundTrip(@TempDir Path dir) {
        ExampleFiles.writePresets(folder(dir, "presets"), ExamplePresets.all());

        Map<String, Preset> loaded = YamlDefinitions.presetSources(folder(dir, "presets")).all().stream()
                .collect(Collectors.toMap(Preset::id, Function.identity()));

        assertEquals(ExamplePresets.all().size(), loaded.size());
        for (Preset preset : ExamplePresets.all()) {
            Preset actual = loaded.get(ExampleFiles.fileId(preset.id()));
            assertEquals(preset.kind(), actual.kind(), preset.id());
            assertEquals(preset.name(), actual.name(), preset.id());
            assertEquals(preset.type(), actual.type(), preset.id());
            assertEquals(preset.properties(), actual.properties(), preset.id());
            assertEquals(preset.description(), actual.description(), preset.id());
        }

        assertEquals(0, ExampleFiles.writePresets(folder(dir, "presets"), ExamplePresets.all()),
                "目录里已经有预设文件时不再铺");
    }

    private DefinitionFolder folder(Path dir, String label) {
        return new DefinitionFolder(dir, label, warnings::add);
    }

    private static List<String> listYaml(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
