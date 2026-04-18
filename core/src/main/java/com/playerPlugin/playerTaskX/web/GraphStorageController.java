package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.manager.GraphManager;
import io.javalin.http.Context;

import java.util.Collection;

public class GraphStorageController {
    private final GraphManager graphManager;

    public GraphStorageController(GraphManager graphManager) {
        this.graphManager = graphManager;
    }

    public void getAll(Context ctx) {
        Collection<QuestGraph> graphs = graphManager.getAllGraphs();
        ctx.json(ApiResponse.success(graphs));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        QuestGraph graph = graphManager.getGraph(id).orElse(null);
        if (graph == null) {
            ctx.status(404).json(ApiResponse.error(404, "Graph not found"));
            return;
        }
        ctx.json(ApiResponse.success(graph));
    }

    public void save(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            QuestGraph graph = ctx.bodyAsClass(QuestGraph.class);
            graphManager.saveGraph(graph);
            ctx.json(ApiResponse.success(graph));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid graph data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        graphManager.deleteGraph(id);
        ctx.json(ApiResponse.success(null));
    }
}
