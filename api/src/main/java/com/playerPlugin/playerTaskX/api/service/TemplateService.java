package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.template.ActionTemplate;
import com.playerPlugin.playerTaskX.api.model.template.ObjectiveTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateService {
    private static final Map<String, ObjectiveTemplate> objectiveTemplates = new ConcurrentHashMap<>();
    private static final Map<String, ActionTemplate> actionTemplates = new ConcurrentHashMap<>();

    public static void loadObjectiveTemplates(List<ObjectiveTemplate> templates) {
        objectiveTemplates.clear();
        for (ObjectiveTemplate template : templates) {
            if (template.getId() != null) {
                objectiveTemplates.put(template.getId(), template);
            }
        }
    }

    public static void loadActionTemplates(List<ActionTemplate> templates) {
        actionTemplates.clear();
        for (ActionTemplate template : templates) {
            if (template.getId() != null) {
                actionTemplates.put(template.getId(), template);
            }
        }
    }

    public static ObjectiveTemplate getObjectiveTemplate(String id) {
        return objectiveTemplates.get(id);
    }

    public static ActionTemplate getActionTemplate(String id) {
        return actionTemplates.get(id);
    }

    public static Map<String, ObjectiveTemplate> getAllObjectiveTemplates() {
        return objectiveTemplates;
    }

    public static Map<String, ActionTemplate> getAllActionTemplates() {
        return actionTemplates;
    }
}
