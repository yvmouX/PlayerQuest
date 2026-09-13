package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.QuestClaimRepository;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@link QuestClaimRepository} 的 JDBC 实现（SQLite 与 MySQL 共用）。
 * <p>
 * 与玩家任务仓储同样的约定：UUID 以 {@code toString()} 存 VARCHAR(36)，写入走
 * {@link com.playerPlugin.playerTaskX.core.storage.Dialect#upsert}，因此两种方言都不用分支。
 */
public final class JdbcQuestClaimRepository implements QuestClaimRepository {

    private static final String COLUMNS = "player_id,quest_id,claimed_at";
    private static final String KEY_COLUMNS = "player_id,quest_id";

    private final Database database;

    private final String sqlSelectByPlayer;
    private final String sqlUpsert;

    public JdbcQuestClaimRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database 不能为空");
        // ORDER BY quest_id：集合本身无序，但让读取结果稳定，便于排查「同一玩家两次读为什么不同」
        this.sqlSelectByPlayer = "SELECT quest_id FROM quest_claim WHERE player_id = ? ORDER BY quest_id";
        this.sqlUpsert = database.dialect().upsert("quest_claim", KEY_COLUMNS, COLUMNS);
    }

    @Override
    public Set<String> claimedQuestIds(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String questId : database.query(sqlSelectByPlayer, rs -> rs.getString("quest_id"),
                playerId.toString())) {
            if (questId != null && !questId.isBlank()) {
                ids.add(questId);
            }
        }
        return ids;
    }

    @Override
    public void markClaimed(UUID playerId, String questId, long claimedAt) {
        if (playerId == null || questId == null || questId.isBlank()) {
            // 两列都是主键的一部分，为空无法落库：跳过而不是让整次领取失败
            warn("领取账本的 playerId/questId 为空，已跳过写入");
            return;
        }
        database.execute(sqlUpsert, playerId.toString(), questId, claimedAt);
    }

    private static void warn(String message) {
        System.err.println("[PlayerTaskX] " + message);
    }
}
