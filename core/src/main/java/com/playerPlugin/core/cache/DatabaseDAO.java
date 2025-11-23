package com.playerPlugin.core.cache;

import cn.yvmou.ylib.tools.LoggerTools;
import com.playerPlugin.core.common.Enum.PTXTaskStatus;
import com.playerPlugin.core.domain.Task.TaskDefinition;
import com.playerPlugin.core.domain.Task.TaskProgress;
import com.playerPlugin.core.domain.Task.TaskTarget;
import com.playerPlugin.core.storage.RepositoryCreator;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DatabaseDAO {
    private final LoggerTools log;
    private final RepositoryCreator creator;
    private final CacheDAO cache;

    public DatabaseDAO(LoggerTools log, RepositoryCreator creator, CacheDAO cache) {
        this.log = log;
        this.creator = creator;
        this.cache = cache;
    }


    /**
     * 开始任务
     * 状态: 0:进行中, 1:完成, 2:失败
     *
     * @param tasks 任务列表
     */
    public void startTask(List<TaskProgress> tasks) throws SQLException {
        String sql = "INSERT OR IGNORE INTO player_tasks (player_uuid, task_id, status, start_time) VALUES (?, ?, 0, ?)";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            for (TaskProgress task : tasks) {
                ps.setString(1, task.getUUID().toString());
                ps.setString(2, task.getTask().getId());
                ps.setLong(3, System.currentTimeMillis());
                ps.addBatch();
            }
            int[] a = ps.executeBatch();

            if (a.length > 0) {
                log.debug("成功插入玩家任务数据：" + tasks.size() + "影响行数：" + Arrays.toString(a));
            } else {
                log.debug("插入玩家任务数据失败：" + tasks.size() + "影响行数：" + Arrays.toString(a));
            }
        }
    }

    /**
     * 更新任务列表
     *
     * @param tasks 任务列表
     */
    public void updateTasks(List<TaskProgress> tasks) throws SQLException {
        String sql = "UPDATE player_tasks SET status = ? WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            for (TaskProgress task : tasks) {
                int statusId = switch (task.getStatus()) {
                    case IN_PROGRESS -> 0;
                    case COMPLETED -> 1;
                    case FAILED -> 2;
                };
                ps.setInt(1, statusId);
                ps.setString(2, task.getUUID().toString());
                ps.setString(3, task.getTask().getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * 完成任务
     *
     * @param uuid   uuid
     * @param taskId 任务 ID
     */
    public void finishTask(String uuid, String taskId) throws SQLException {
        String sql = "UPDATE player_tasks SET status = 1, finish_time = ? WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid);
            ps.setString(3, taskId);
            ps.executeUpdate();
        }
    }




    /**
     * 初始化进度为0
     *
     * @param taskProgresses 玩家任务
     */
    public void initProgress(List<TaskProgress> taskProgresses) {
        taskProgresses.forEach(task -> {
            task.getTask().getTargets().forEach(target -> {
                UUID uuid = task.getUUID();
                String taskId = task.getTask().getId();
                int index = target.getIndex();

                String sql = "INSERT OR IGNORE INTO player_task_progress (player_uuid, task_id, target_index, current_amount) VALUES (?, ?, ?, 0)";
                try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, taskId);
                    ps.setInt(3, index);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.error(
                            String.format(
                                    "Failed to initialize task progress for player %s in task %s: %s",
                                    uuid, taskId, e.getMessage()
                            )
                    );
                }
            });
        });
    }

    /**
     * 设置任务
     *
     * @param taskProgresses 玩家任务
     */
    public void setProgress(List<TaskProgress> taskProgresses) {
        taskProgresses.forEach(task -> {
            task.getTask().getTargets().forEach(target -> {
                UUID uuid = task.getUUID();
                String taskId = task.getTask().getId();
                int index = target.getIndex();
                int currentAmount = target.getCurrent();

                String SQL = "INSERT OR IGNORE INTO player_task_progress (player_uuid, task_id, target_index, current_amount) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = creator.getConnection().prepareStatement(SQL)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, taskId);
                    ps.setInt(3, index);
                    ps.setInt(4, currentAmount);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.error(
                            String.format(
                                    "Failed to initialize task progress for player %s in task %s: %s",
                                    uuid, taskId, e.getMessage()
                            )
                    );
                }
            });
        });
    }

    public void updateProgress(List<TaskProgress> taskProgresses) {
        taskProgresses.forEach(task -> {
            task.getTask().getTargets().forEach(target -> {
                UUID uuid = task.getUUID();
                String taskId = task.getTask().getId();
                int targetIndex = target.getIndex();
                int currentAmount = target.getCurrent();

                boolean isValid = isValidTaskProgress(uuid, taskId, targetIndex);
                log.debug(String.format("isValidTaskProgress is:" + isValid));
                if (!isValid) { // TODO 暂时使用这种方法
                    initProgress(taskProgresses);
                    log.info(
                            String.format(
                                    "初始化玩家 %s 在任务 %s 需求索引 %d 的任务进度",
                                    uuid, taskId, targetIndex
                            )
                    );
                }

                String sql = "UPDATE player_task_progress SET current_amount = ? WHERE player_uuid = ? AND task_id = ? AND target_index = ?";
                try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
                    ps.setInt(1, currentAmount);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, taskId);
                    ps.setInt(4, targetIndex);
                    ps.executeUpdate();
                    log.debug(
                            String.format(
                                    "更新任务进度 玩家 %s 任务 %s 需求索引 %d 进度为 %d",
                                    uuid, taskId, targetIndex, currentAmount
                            )
                    );
                } catch (SQLException e) {
                    log.error(
                            String.format(
                                    "Failed to update task progress for player %s in task %s at index %d: %s",
                                    uuid, taskId, targetIndex, e.getMessage()
                            )
                    );
                }
            });
        });

    }

    private boolean isValidTaskProgress(UUID uuid, String taskId, int targetIndex) {
        String sql = "SELECT COUNT(*) FROM player_task_progress WHERE player_uuid = ? AND task_id = ? AND target_index = ?";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            ps.setString(1, String.valueOf(uuid));
            ps.setString(2, taskId);
            ps.setInt(3, targetIndex);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error(
                    String.format(
                            "Failed to check task progress for player %s in task %s at index %d: %s",
                            uuid, taskId, targetIndex, e.getMessage()
                    )
            );
        }
        return false;
    }


    /**
     * 获取进度
     *
     * <p>
     *     return 任务进度映射，键为目标索引，值为当前进度
     * </p>
     *
     * @param uuid   uuid
     * @param taskId 任务 ID
     * @return {@link Map }<{@link Integer }, {@link Integer }>
     */
    public Map<Integer, Integer> getProgress(String uuid, String taskId) {
        Map<Integer, Integer> progress = new HashMap<>();
        String sql = "SELECT target_index, current_amount FROM player_task_progress WHERE player_uuid = ? AND task_id = ?";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, taskId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                progress.put(rs.getInt("target_index"), rs.getInt("current_amount"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return progress;
    }

    /**
     * 从数据库加载数据到缓存
     *
     */
    public void databaseToCache(Player p) {
        List<TaskProgress> inProgressTaskIdList = new LinkedList<>();
        try {
            inProgressTaskIdList = getTaskListFromDatabase(p.getUniqueId(), PTXTaskStatus.IN_PROGRESS);
        } catch (SQLException e) {
            log.error("从数据库获取玩家 " + p.getName() + " 进行中的任务时失败：" + e.getMessage());
        }

        if (inProgressTaskIdList != null) {
            getCacheDAO().updatePlayerTaskToCache(inProgressTaskIdList, false);
            log.debug("已加载玩家 " + p.getName() + " 进行中的任务：" + inProgressTaskIdList + " 共 " + inProgressTaskIdList.size() + " 个");
        }
    }

    public void cacheToDatabase(Player p) {
        // TODO
    }


    /**
     * 获取指定玩家的进行中任务ID列表
     * 状态: 0:进行中
     *
     * @param uuid           玩家UUID
     * @param requiredStatus 必需状态
     * @return {@link List }<{@link String }>
     * @throws SQLException sql异常
     */
    public List<String> getTaskIdListFromDatabase(UUID uuid, PTXTaskStatus requiredStatus) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT * FROM player_tasks WHERE player_uuid = ? AND status = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, requiredStatus.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (isRequiredStatus(requiredStatus, rs)) continue;

                result.add(rs.getString("task_id"));
            }
        }
        return result;
    }

    /**
     * 从数据库获取任务列表
     *
     * @param uuid           uuid
     * @param requiredStatus 必需状态
     * @return {@link List }<{@link TaskProgress }>
     * @throws SQLException sql异常
     */
    public List<TaskProgress> getTaskListFromDatabase(UUID uuid, PTXTaskStatus requiredStatus) throws SQLException {
        List<TaskProgress> result = new ArrayList<>();
        String sql = "SELECT * FROM player_tasks WHERE player_uuid = ? AND status = 0";
        try (PreparedStatement ps = creator.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (isRequiredStatus(requiredStatus, rs)) continue;

                String taskId = rs.getString("task_id");

                // 处理任务目标，设置为玩家当前的进度
                List<TaskTarget> handledTaskTargetList = tm.getTaskTargets(taskId);
                for (TaskTarget taskTarget : handledTaskTargetList) {
                    Map<Integer, Integer> progressMap = getDatabaseDAO().getProgress(uuid.toString(), taskId);
                    taskTarget.setCurrent(
                            progressMap.get(taskTarget.getIndex())
                    );
                }

                result.add(
                        new TaskProgress(uuid,
                                new TaskDefinition
                                        (
                                                taskId,
                                                tm.getTaskType(taskId),
                                                tm.getTaskName(taskId),
                                                handledTaskTargetList,
                                                tm.getTaskTrigger(taskId)
                                        )));
            }
        }
        return result;
    }

    private boolean isRequiredStatus(PTXTaskStatus requiredStatus, ResultSet rs) throws SQLException {
        PTXTaskStatus status = switch (rs.getInt("status")) {
            case 0 -> PTXTaskStatus.IN_PROGRESS;
            case 1 -> PTXTaskStatus.COMPLETED;
            case 2 -> PTXTaskStatus.FAILED;
            default -> throw new IllegalStateException("Unexpected value: " + rs.getInt("status"));
        };

        if (status != requiredStatus) {
            return true;
        }
        return false;
    }
}
