package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GraphManager {
    private final Map<String, QuestGraph> graphs = new ConcurrentHashMap<>();

    public void saveGraph(QuestGraph graph) {
        graphs.put(graph.getId(), graph);
    }

    public Optional<QuestGraph> getGraph(String id) {
        return Optional.ofNullable(graphs.get(id));
    }

    public java.util.Collection<QuestGraph> getAllGraphs() {
        return graphs.values();
    }

    public void deleteGraph(String id) {
        graphs.remove(id);
    }
}
