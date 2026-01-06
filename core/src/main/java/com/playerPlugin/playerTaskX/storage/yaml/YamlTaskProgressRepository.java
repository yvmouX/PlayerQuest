package com.playerPlugin.playerTaskX.storage.yaml;

import cn.yvmou.ylib.api.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.utils.StorageUtil;
import com.playerPlugin.playerTaskX.api.utils.TimeUtil;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class YamlTaskProgressRepository implements TaskProgressRepository {
    private final ObjectMapper yamlMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final PlayerTaskX plugin;
    private final Logger log;

    public YamlTaskProgressRepository(PlayerTaskX plugin, Logger log){
        this.plugin = plugin;
        this.log = log;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public void create(Player player, TaskDefinition taskDefinition) {
        Path path = getTaskFile(player.getUniqueId(), taskDefinition.getId());

        writeLock.lock();
        try {
            if (isValidFile(path)) {
                log.warn("Task progress repository for player {} already exists, skipping creation.", player.getName());
                return;
            }

            TaskProgress taskProgress = new TaskProgress(player.getUniqueId(), taskDefinition, PTXTaskStatus.IN_PROGRESS, TimeUtil.getTime(), TimeUtil.getTime());
            ensureParentDirectory(path);
            writeAtomically(path, taskProgress);
            
            log.debug("Created task progress file {} for player {}.", path, player.getName());
        } catch (IOException e) {
            log.error("Error creating task progress file for player {}: {}", player.getName(), path, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<TaskProgress> find(Player player, String taskId) {
        Path progressFile = getTaskFile(player.getUniqueId(), taskId);
        
        Optional<TaskProgress> result = loadTaskProgress(progressFile, player.getName());
        if (!result.isPresent()) {
            log.debug("Task progress for player {} not loaded (not found or invalid).", player.getName());
        }
        return result;
    }

    @Override
    public List<TaskProgress> findAll(Player player) {
        List<TaskProgress> result = new ArrayList<>();
        Path dir = StorageUtil.getProgressDir(plugin, player.getUniqueId().toString());

        List<String> fileNames = StorageUtil.getFileNamesBySuffix(dir,".yml", false);
        if (fileNames.isEmpty()) {
            log.debug("Task progress for player {} not loaded, possibly because it hasn't been created.", player.getName());
            return result;
        }

        for (String fileName : fileNames) {
            Path progressFile = dir.resolve(fileName);
            loadTaskProgress(progressFile, player.getName()).ifPresent(result::add);
        }

        return result;
    }

    @Override
    public void update(TaskProgress progress) {
        save(progress);
    }

    @Override
    public void save(TaskProgress progress) {
        Path path = getTaskFile(progress.getUuid(), progress.getTaskDefinition().getId());

        writeLock.lock();
        try {
            // 1. Load or initialize Root Node
            ObjectNode rootNode = loadOrCreateRootNode(path, progress.getUuid());

            // 2. Update data to Node
            updateNodeData(rootNode, progress);

            // 3. Atomic write
            writeAtomically(path, rootNode);

            log.debug("Successfully saved task for player {}", progress.getUuid());
        } catch (IOException e) {
            log.error("Error saving task progress for player {}: {}", progress.getUuid(), path, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void delete(UUID uuid, String taskId) {
        Path path = getTaskFile(uuid, taskId);

        writeLock.lock();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Error deleting task progress repository for player {}: ", uuid, e);
        } finally {
            writeLock.unlock();
        }
    }

    // --- Private Helper Methods ---

    /**
     * Get task progress file path
     */
    private Path getTaskFile(UUID uuid, String taskId) {
        return StorageUtil.getProgressDir(plugin, uuid.toString()).resolve(taskId + ".yml");
    }

    /**
     * Check if file is valid (exists and not empty)
     */
    private boolean isValidFile(Path path) throws IOException {
        return Files.exists(path) && Files.size(path) > 0;
    }

    /**
     * Ensure parent directory exists
     */
    private void ensureParentDirectory(Path path) throws IOException {
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
    }

    /**
     * Load task progress from file safely with locking and error handling
     */
    private Optional<TaskProgress> loadTaskProgress(Path path, String playerName) {
        readLock.lock();
        try {
            if (!isValidFile(path)) {
                return Optional.empty();
            }

            TaskProgress taskProgress = yamlMapper.readValue(path.toFile(), TaskProgress.class);
            return Optional.ofNullable(taskProgress);
        } catch (IOException e) {
            log.error("Error loading task progress for player {}, file may be corrupted: {}", playerName, path, e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Load existing YAML node, or create new if not exists or invalid
     */
    private ObjectNode loadOrCreateRootNode(Path path, UUID uuid) throws IOException {
        // Initialize new file if not exists or empty
        if (!isValidFile(path)) {
            if (!Files.exists(path)) {
                ensureParentDirectory(path);
                Files.createFile(path);
                log.warn("Task progress repository for player {} does not exist, created automatically.", uuid);
            }
            return yamlMapper.createObjectNode();
        }

        // Try to read existing content
        JsonNode rootNode = yamlMapper.readTree(path.toFile());
        if (rootNode == null || !rootNode.isObject()) {
            return yamlMapper.createObjectNode();
        }
        return (ObjectNode) rootNode;
    }

    /**
     * Update TaskProgress data to ObjectNode
     */
    private void updateNodeData(ObjectNode mutNode, TaskProgress progress) {
        if (progress.getUuid() != null) {
            mutNode.put("uuid", progress.getUuid().toString());
        }
        if (progress.getStatus() != null) {
            mutNode.put("status", progress.getStatus().toString());
        }
        if (progress.getTaskDefinition() != null) {
            mutNode.set("taskDefinition", yamlMapper.valueToTree(progress.getTaskDefinition()));
        }
    }

    /**
     * Write file atomically (Write to temp -> Move and replace)
     */
    private void writeAtomically(Path targetPath, Object content) throws IOException {
        // Use .tmp suffix as temp file to avoid overwriting original file
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        
        try {
            yamlMapper.writeValue(tempPath.toFile(), content);
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            throw e;
        }
    }
}
