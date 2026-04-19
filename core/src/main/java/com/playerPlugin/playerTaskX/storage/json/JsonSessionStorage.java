package com.playerPlugin.playerTaskX.storage.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.storage.SessionStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JsonSessionStorage implements SessionStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public JsonSessionStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "sessions");
        this.mapper = new ObjectMapper();
        this.dataFolder.mkdirs();
    }

    private File getSessionFile(UUID playerId, String questId) {
        return new File(dataFolder, playerId.toString() + "_" + questId + ".json");
    }

    @Override
    public void save(QuestSession session) {
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
        File[] files = dataFolder.listFiles((dir, name) -> name.startsWith(playerId.toString() + "_") && name.endsWith(".json"));
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
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return sessions;
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
        return sessions;
    }

    @Override
    public void delete(UUID playerId, String questId) {
        getSessionFile(playerId, questId).delete();
    }
}