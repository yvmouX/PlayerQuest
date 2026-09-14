package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「数据库 + YAML 文件」合并仓储的规则测试。
 *
 * <p>核心不变量：库优先、冲突告警、只有文件定义的那些是只读、写操作<b>永远落库</b>。
 * 这些规则一旦走偏，表现是「管理员改了任务、重启后被打回」或者「库里悄悄多出一条
 * 与文件同 id 的记录」——都不会报错，只有对着两份数据才能发现。</p>
 */
class MergedDefinitionRepositoryTest {

    private final List<String> warnings = new ArrayList<>();

    @Test
    @DisplayName("库优先：同 id 时用库里的定义，文件那份被忽略并告警")
    void databaseWinsOverFile(@TempDir Path dir) throws IOException {
        write(dir.resolve("mine.yml"), questYaml("挖矿（文件版）"));
        InMemoryQuestRepository database = new InMemoryQuestRepository();
        database.save(quest("mine", "挖矿（库版）"));

        MergedQuestRepository merged = merged(dir, database);
        List<Quest> all = merged.findAll();

        assertEquals(1, all.size(), "同 id 不该出现两条");
        assertEquals("挖矿（库版）", all.get(0).name());
        assertEquals(1, warnings.size(), "冲突必须告警: " + warnings);
        assertTrue(warnings.get(0).contains("mine"), warnings.get(0));
        assertTrue(warnings.get(0).contains("库优先"), warnings.get(0));
    }

    @Test
    @DisplayName("库里没有的任务由文件补充，并按只读处理")
    void fileDefinitionsFillTheGap(@TempDir Path dir) throws IOException {
        write(dir.resolve("from_file.yml"), questYaml("只在文件里"));
        InMemoryQuestRepository database = new InMemoryQuestRepository();
        database.save(quest("from_db", "只在库里"));

        MergedQuestRepository merged = merged(dir, database);

        assertEquals(2, merged.findAll().size());
        assertTrue(merged.isReadOnly("from_file"), "只由文件定义的 id 是只读的");
        assertFalse(merged.isReadOnly("from_db"), "库里的定义可写");
        assertEquals("只在库里", merged.findById("from_db").orElseThrow().name(), "按 id 取时库优先");
    }

    @Test
    @DisplayName("写操作永远落库；写到只读 id 抛异常而不是在库里造一份同 id")
    void writesGoToDatabaseAndRefuseReadOnlyIds(@TempDir Path dir) throws IOException {
        write(dir.resolve("from_file.yml"), questYaml("文件版"));
        InMemoryQuestRepository database = new InMemoryQuestRepository();
        MergedQuestRepository merged = merged(dir, database);
        merged.findAll();

        merged.save(quest("new_one", "新建的"));
        assertTrue(database.exists("new_one"), "普通保存必须落库");

        DefinitionReadOnlyException failure = assertThrows(DefinitionReadOnlyException.class,
                () -> merged.save(quest("from_file", "想改文件里的")),
                "改只读定义必须被拒绝，而不是悄悄写进库");
        assertTrue(failure.getMessage().contains("from_file.yml"),
                "提示要说清是哪个文件: " + failure.getMessage());
        assertFalse(database.exists("from_file"), "被拒绝的写操作不该留下任何记录");

        assertThrows(DefinitionReadOnlyException.class, () -> merged.delete("from_file"));
    }

    @Test
    @DisplayName("count 的口径是「插件实际能用多少」：库为空但文件里有定义时不为 0")
    void countReflectsMergedView(@TempDir Path dir) throws IOException {
        write(dir.resolve("only_file.yml"), questYaml("文件里的"));
        MergedQuestRepository merged = merged(dir, new InMemoryQuestRepository());

        assertEquals(1, merged.count(), "文件里的定义也算「能用」，列表与统计看的是这个数");
    }

    @Test
    @DisplayName("播种看的是库而不是合并视图：文件里有定义也不影响库里那套示例")
    void seedingLooksAtDatabaseOnly(@TempDir Path dir) throws IOException {
        write(dir.resolve("example_file_x.yml"), questYaml("文件里的示例"));
        InMemoryQuestRepository database = new InMemoryQuestRepository();
        MergedQuestRepository merged = merged(dir, database);

        assertTrue(merged.databaseEmpty(), "库确实还是空的");
        assertEquals(1, merged.count(), "但合并视图里已经有文件那份");

        database.save(quest("in_db", "库里的"));
        assertFalse(merged.databaseEmpty(), "库里有记录之后就不再是空库");
    }

    @Test
    @DisplayName("预设：文件里有定义时，库里那套出厂预设照样播种（两套并存）")
    void presetSeedingIgnoresFiles(@TempDir Path dir) throws IOException {
        write(dir.resolve("exp.yml"), """
                kind: rewards
                type: exp
                properties: { amount: 100 }
                """);
        InMemoryPresetRepository database = new InMemoryPresetRepository();
        YamlSources<Preset> files = YamlDefinitions.presetSources(
                new DefinitionFolder(dir, "presets", warnings::add));
        MergedPresetRepository merged = new MergedPresetRepository(database, files, warnings::add);

        merged.seedIfEmpty(List.of(new Preset(Preset.OBJECTIVES, "default", "默认", "chat", Map.of(), "")));

        assertEquals(2, merged.findAll().size(), "库里的示例与文件里的预设并存");
        assertEquals(1, database.findAll().size(), "库里那套照样写进去（编辑器里能改、能禁用的就是它）");
        assertFalse(merged.databaseEmpty(), "库里已经有记录了");
    }

    @Test
    @DisplayName("预设：文件定义只读、库定义可写")
    void presetReadOnlyFlag(@TempDir Path dir) throws IOException {
        write(dir.resolve("exp.yml"), """
                kind: rewards
                type: exp
                properties: { amount: 100 }
                """);
        InMemoryPresetRepository database = new InMemoryPresetRepository();
        database.save(new Preset(Preset.OBJECTIVES, "db_preset", "库里的", "chat", Map.of(), ""));
        YamlSources<Preset> files = YamlDefinitions.presetSources(
                new DefinitionFolder(dir, "presets", warnings::add));
        MergedPresetRepository merged = new MergedPresetRepository(database, files, warnings::add);

        assertTrue(merged.isReadOnly("exp"));
        assertFalse(merged.isReadOnly("db_preset"));
        assertThrows(DefinitionReadOnlyException.class,
                () -> merged.save(new Preset(Preset.REWARDS, "exp", "改名", "exp", Map.of(), "")));
    }

    // ---------- 辅助 ----------

    private MergedQuestRepository merged(Path dir, InMemoryQuestRepository database) {
        YamlSources<Quest> files = YamlDefinitions.questSources(
                new DefinitionFolder(dir, "quests", warnings::add));
        return new MergedQuestRepository(database, files, warnings::add);
    }

    private static String questYaml(String name) {
        return "name: " + name + "\n"
                + "type: NORMAL\n"
                + "objectives:\n"
                + "  - type: chat\n"
                + "    properties: { target: \"\", amount: 1 }\n";
    }

    private static Quest quest(String id, String name) {
        return new Quest(id, name, List.of(), "PAPER", null, QuestType.NORMAL,
                List.of(QuestObjective.of("chat", Map.of("target", "", "amount", 1))),
                List.of(), 0.0, true);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** 内存版任务定义仓储：只实现测试用得到的几个方法。 */
    private static final class InMemoryQuestRepository
            implements com.playerPlugin.playerTaskX.core.storage.QuestRepository {

        private final Map<String, Quest> data = new LinkedHashMap<>();

        @Override
        public List<Quest> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Quest> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public void save(Quest quest) {
            data.put(quest.id(), quest);
        }

        @Override
        public boolean delete(String id) {
            return data.remove(id) != null;
        }

        @Override
        public long count() {
            return data.size();
        }
    }

    /** 内存版预设仓储。 */
    private static final class InMemoryPresetRepository
            implements com.playerPlugin.playerTaskX.core.storage.PresetRepository {

        private final Map<String, Preset> data = new LinkedHashMap<>();

        @Override
        public List<Preset> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Preset> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public void save(Preset preset) {
            data.put(preset.id(), preset);
        }

        @Override
        public boolean delete(String id) {
            return data.remove(id) != null;
        }

        @Override
        public long count() {
            return data.size();
        }

        @Override
        public void seedIfEmpty(List<Preset> defaults) {
            if (data.isEmpty()) {
                defaults.forEach(this::save);
            }
        }
    }
}
