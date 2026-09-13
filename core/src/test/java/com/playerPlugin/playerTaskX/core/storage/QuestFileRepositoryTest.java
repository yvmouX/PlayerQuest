package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件仓储的测试。
 *
 * <p>重点覆盖两件数据库白送、文件方案必须自己保证的事：
 * <b>写入的原子性</b>与<b>载入的分级容错</b>。用户手改文件是本方案的核心使用方式，
 * 因此「一个文件写坏了会怎样」必须被钉死——绝不能演变成「全部任务消失」。
 */
class QuestFileRepositoryTest {

    private static Quest sample(String id, String name) {
        return new Quest(id, name, List.of("第一行", "第二行"), "STONE_PICKAXE", "每日", QuestType.DAILY,
                List.of(
                        QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 64)),
                        QuestObjective.of("chat", Map.of("target", "你好", "amount", 1))),
                List.of(QuestReward.of("money", Map.of("amount", 500))),
                1000.0, true);
    }

    private static QuestFileRepository repository(Path folder, List<String> warnings) {
        return new QuestFileRepository(new JsonFileStore(folder), warnings::add);
    }

    @Test
    @DisplayName("写入后能读回，字段完整（含目标顺序与属性）")
    void roundTrip(@TempDir Path folder) throws Exception {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        Quest original = sample("daily_mine", "挖矿日常");
        repository.save(original);

        // 一任务一文件
        assertTrue(Files.isRegularFile(folder.resolve("daily_mine.json")), "应生成 daily_mine.json");

        Optional<Quest> loaded = repository.findById("daily_mine");
        assertTrue(loaded.isPresent());
        Quest quest = loaded.get();
        assertEquals(original.id(), quest.id());
        assertEquals(original.name(), quest.name());
        assertEquals(original.description(), quest.description());
        assertEquals(original.icon(), quest.icon());
        assertEquals(original.type(), quest.type());
        assertEquals(original.refreshCost(), quest.refreshCost(), 0.0001);
        assertTrue(quest.enabled());
        assertEquals(2, quest.objectives().size());
        assertEquals("break_block", quest.objectives().get(0).type());
        assertEquals("STONE", quest.objectives().get(0).properties().get("target"));
        assertEquals(64, ((Number) quest.objectives().get(0).properties().get("amount")).intValue());
        assertEquals("chat", quest.objectives().get(1).type());
        assertEquals(1, quest.rewards().size());
        assertEquals("money", quest.rewards().get(0).type());
    }

    @Test
    @DisplayName("属性值保持字符串，不被类型隐式转换破坏")
    void propertyTypesSurvive(@TempDir Path folder) {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        // NO 是合法的方块材质名；若用 YAML 1.1 会被解析成布尔 false
        Quest quest = new Quest("t", "t", List.of(), "PAPER", "", QuestType.NORMAL,
                List.of(QuestObjective.of("break_block", Map.of("target", "NO", "amount", 8))),
                List.of(), 0.0, true);
        repository.save(quest);

        Object target = repository.findById("t").orElseThrow()
                .objectives().get(0).properties().get("target");
        assertEquals("NO", String.valueOf(target), "材质名 NO 必须保持字符串");
    }

    @Test
    @DisplayName("覆盖保存：同 id 只留一个文件，内容被替换")
    void saveOverwrites(@TempDir Path folder) {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("q1", "旧名字"));
        repository.save(sample("q1", "新名字"));

        assertEquals(1, repository.count(), "同 id 不应产生第二个文件");
        assertEquals("新名字", repository.findById("q1").orElseThrow().name());
    }

    @Test
    @DisplayName("删除：文件消失，findById 返回空")
    void deleteRemovesFile(@TempDir Path folder) {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("q1", "任务"));
        assertTrue(repository.delete("q1"));
        assertEquals(0, repository.count());
        assertTrue(repository.findById("q1").isEmpty());
        assertFalse(repository.delete("q1"), "重复删除应返回 false");
    }

    @Test
    @DisplayName("单个文件 JSON 语法错：跳过它，其余任务照常载入")
    void brokenFileIsSkipped(@TempDir Path folder) throws Exception {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("good_a", "好任务A"));
        repository.save(sample("good_b", "好任务B"));

        // 模拟用户手改坏了其中一个文件
        Files.writeString(folder.resolve("broken.json"), "{ 这不是合法 JSON ", StandardCharsets.UTF_8);

        List<Quest> loaded = repository.findAll();
        assertEquals(2, loaded.size(), "坏文件不该影响其它任务: " + loaded);
        assertTrue(loaded.stream().anyMatch(q -> q.id().equals("good_a")));
        assertTrue(loaded.stream().anyMatch(q -> q.id().equals("good_b")));
    }

    @Test
    @DisplayName("坏文件会留下可定位的警告（含文件名）")
    void brokenFileWarns(@TempDir Path folder) throws Exception {
        List<String> warnings = new ArrayList<>();
        QuestFileRepository repository = repository(folder, warnings);
        Files.writeString(folder.resolve("oops.json"), "not json at all", StandardCharsets.UTF_8);

        repository.findAll();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("oops.json")),
                "警告里应指出是哪个文件: " + warnings);
    }

    @Test
    @DisplayName("缺少 id 的文件被跳过，且不会用文件名推断 id")
    void fileWithoutIdIsSkipped(@TempDir Path folder) throws Exception {
        List<String> warnings = new ArrayList<>();
        QuestFileRepository repository = repository(folder, warnings);
        Files.writeString(folder.resolve("guess_me.json"),
                "{\"name\":\"没有id\"}", StandardCharsets.UTF_8);

        assertTrue(repository.findAll().isEmpty(), "缺 id 的文件必须跳过");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("guess_me.json") && w.contains("id")),
                "警告应说明缺少 id: " + warnings);
    }

    @Test
    @DisplayName("文件名与 id 不一致：以 id 为准并告警，不产生幽灵任务")
    void fileNameMismatchUsesId(@TempDir Path folder) throws Exception {
        List<String> warnings = new ArrayList<>();
        QuestFileRepository repository = repository(folder, warnings);
        // 文件名叫 renamed，内容里的 id 是 real_id
        repository.save(sample("real_id", "真任务"));
        Files.move(folder.resolve("real_id.json"), folder.resolve("renamed.json"));

        List<Quest> loaded = repository.findAll();
        assertEquals(1, loaded.size());
        assertEquals("real_id", loaded.get(0).id(), "应以 id 字段为准，而不是文件名");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("renamed.json")),
                "应提示文件名与 id 不一致: " + warnings);
    }

    @Test
    @DisplayName("空目录返回空列表，不报错")
    void emptyFolder(@TempDir Path folder) {
        assertEquals(0, repository(folder, new ArrayList<>()).count());
        assertTrue(repository(folder, new ArrayList<>()).findAll().isEmpty());
    }

    @Test
    @DisplayName("目录不存在时读取返回空，不抛异常")
    void missingFolder(@TempDir Path folder) {
        Path missing = folder.resolve("not_created_yet");
        assertTrue(repository(missing, new ArrayList<>()).findAll().isEmpty());
    }

    @Test
    @DisplayName("写入是原子的：目录里不留 .tmp 碎片")
    void noTempLeftBehind(@TempDir Path folder) throws Exception {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("q1", "任务"));

        try (var stream = Files.list(folder)) {
            List<String> names = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of("q1.json"), names, "不应残留临时文件: " + names);
        }
    }

    @Test
    @DisplayName("崩溃残留的 .tmp 会在下次载入时被清理")
    void staleTempIsCleaned(@TempDir Path folder) throws Exception {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("q1", "任务"));
        Files.writeString(folder.resolve("q1.json.tmp"), "半截内容", StandardCharsets.UTF_8);

        repository.findAll();
        assertFalse(Files.exists(folder.resolve("q1.json.tmp")), "残留临时文件应被清理");
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("id 里的路径分隔符被替换，不会写到目录之外")
    void idWithPathSeparatorStaysInside(@TempDir Path folder) throws Exception {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("../../evil", "越界"));

        try (var stream = Files.list(folder)) {
            assertEquals(1, stream.count(), "文件必须留在目标目录内");
        }
        assertFalse(Files.exists(folder.getParent().resolve("evil.json")), "不应写到父目录");
    }

    @Test
    @DisplayName("多个任务按文件名稳定排序载入")
    void loadOrderIsStable(@TempDir Path folder) {
        QuestFileRepository repository = repository(folder, new ArrayList<>());
        repository.save(sample("c_third", "C"));
        repository.save(sample("a_first", "A"));
        repository.save(sample("b_second", "B"));

        List<String> ids = repository.findAll().stream().map(Quest::id).toList();
        assertEquals(List.of("a_first", "b_second", "c_third"), ids);
    }
}
