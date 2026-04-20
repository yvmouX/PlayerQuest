package com.playerPlugin.playerTaskX.web.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.playerPlugin.playerTaskX.api.model.ObjectiveTemplate;
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

public class ObjectiveTemplateController {
    private final Map<String, ObjectiveTemplate> templateStore = new ConcurrentHashMap<>();
    private final Path templateFile;
    private final ObjectMapper mapper;

    public ObjectiveTemplateController(Path dataFolder) {
        this.templateFile = dataFolder.resolve("objective-templates.yml");
        this.mapper = new ObjectMapper(new YAMLFactory());
        loadTemplates();
        TemplateService.loadObjectiveTemplates(new ArrayList<>(templateStore.values()));
    }

    private void loadTemplates() {
        if (Files.exists(templateFile)) {
            try {
                List<ObjectiveTemplate> list = mapper.readValue(templateFile.toFile(),
                        mapper.getTypeFactory().constructCollectionType(List.class, ObjectiveTemplate.class));
                for (ObjectiveTemplate template : list) {
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
            List<ObjectiveTemplate> list = new ArrayList<>(templateStore.values());
            mapper.writeValue(templateFile.toFile(), list);
            TemplateService.loadObjectiveTemplates(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.success(templateStore.values()));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        ObjectiveTemplate template = templateStore.get(id);
        if (template == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        ctx.json(ApiResponse.success(template));
    }

    public void save(Context ctx) {
        try {
            ObjectiveTemplate template = ctx.bodyAsClass(ObjectiveTemplate.class);
            if (template.getId() == null || template.getId().isEmpty()) {
                template = new ObjectiveTemplate(
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
        ObjectiveTemplate removed = templateStore.remove(id);
        if (removed == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        saveTemplates();
        ctx.json(ApiResponse.success(null));
    }
}
