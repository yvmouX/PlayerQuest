package com.playerPlugin.playerTaskX.storage.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class YamlSessionStorage implements SessionStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public YamlSessionStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "sessions");
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.dataFolder.mkdirs();
    }

    private File getPlayerFolder(UUID playerId) {
        return new File(dataFolder, playerId.toString());
    }

    private File getSessionFile(UUID playerId, String questId) {
        return new File(getPlayerFolder(playerId), questId + ".yml");
    }

    @Override
    public void save(QuestSession session) {
        File playerFolder = getPlayerFolder(session.getPlayerId());
        playerFolder.mkdirs();
        File file = getSessionFile(session.getPlayerId(), session.getQuestId());
        try {
            mapper.writeValue(file, session);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session for player: " + session.getPlayerId(), e);
        }
    }

    @Override
    public Optional<QuestSession> find(UUID playerId, String questId) {
        File file = getSessionFile(playerId, questId);
        if (!file.exists()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file, QuestSession.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Collection<QuestSession> findByPlayer(UUID playerId) {
        List<QuestSession> sessions = new ArrayList<>();
        File playerFolder = getPlayerFolder(playerId);
        if (!playerFolder.exists()) return sessions;
        File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return sessions;
        for (File file : files) {
            try {
                sessions.add(mapper.readValue(file, QuestSession.class));
            } catch (IOException e) {
                // Skip invalid files
            }
        }
        return sessions;
    }

    @Override
    public Collection<QuestSession> findAllActive() {
        List<QuestSession> sessions = new ArrayList<>();
        File[] playerFolders = dataFolder.listFiles(File::isDirectory);
        if (playerFolders == null) return sessions;
        for (File playerFolder : playerFolders) {
            File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                try {
                    QuestSession session = mapper.readValue(file, QuestSession.class);
                    if (session.getStatus() == com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus.IN_PROGRESS) {
                        sessions.add(session);
                    }
                } catch (IOException e) {
                    // Skip invalid files
                }
            }
        }
        return sessions;
    }

    @Override
    public void delete(UUID playerId, String questId) {
        getSessionFile(playerId, questId).delete();
    }
}