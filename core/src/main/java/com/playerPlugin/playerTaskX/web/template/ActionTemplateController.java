package com.playerPlugin.playerTaskX.web.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.ActionTemplate;
import com.playerPlugin.playerTaskX.api.service.TemplateService;
import com.playerPlugin.playerTaskX.web.ApiResponse;
import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionTemplateController {
    private final Map<String, ActionTemplate> templateStore = new ConcurrentHashMap<>();
    private final Path templateFile;
    private final ObjectMapper mapper;

    public ActionTemplateController(Path dataFolder) {
        this.templateFile = dataFolder.resolve("action-templates.yml");
        this.mapper = new ObjectMapper(new YAMLFactory());
        loadTemplates();
        TemplateService.loadActionTemplates(new ArrayList<>(templateStore.values()));
    }

    private void loadTemplates() {
        if (Files.exists(templateFile)) {
            try {
                List<ActionTemplate> list = mapper.readValue(templateFile.toFile(),
                        mapper.getTypeFactory().constructCollectionType(List.class, ActionTemplate.class));
                for (ActionTemplate template : list) {
                    if (template.getId() != null) {
                        templateStore.put(template.getId(), template);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTemplates() {
        try {
            List<ActionTemplate> list = new ArrayList<>(templateStore.values());
            mapper.writeValue(templateFile.toFile(), list);
            TemplateService.loadActionTemplates(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.success(templateStore.values()));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        ActionTemplate template = templateStore.get(id);
        if (template == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        ctx.json(ApiResponse.success(template));
    }

    public void save(Context ctx) {
        try {
            ActionTemplate template = ctx.bodyAsClass(ActionTemplate.class);
            if (template.getId() == null || template.getId().isEmpty()) {
                template = new ActionTemplate(
                        UUID.randomUUID().toString(),
                        template.getName(),
                        template.getDescription(),
                        template.getType(),
                        template.getDefaultConfig()
                );
            }
            templateStore.put(template.getId(), template);
            saveTemplates();
            ctx.status(201).json(ApiResponse.success(template));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid template data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        ActionTemplate removed = templateStore.remove(id);
        if (removed == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        saveTemplates();
        ctx.json(ApiResponse.success(null));
    }
}
