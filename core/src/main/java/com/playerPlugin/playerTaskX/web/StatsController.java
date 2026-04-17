package com.playerPlugin.playerTaskX.web;

import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsController {

    public void getCompletion(Context ctx) {
        // TODO: 实现真正的统计数据查询
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of("name", "已完成", "value", 0));
        data.add(Map.of("name", "进行中", "value", 0));
        data.add(Map.of("name", "未开始", "value", 0));
        
        ctx.json(ApiResponse.success(data));
    }

    public void getActivity(Context ctx) {
        String range = ctx.queryParam("range");
        if (range == null) range = "7d";
        
        // TODO: 实现真正的活动趋势查询
        List<Map<String, Object>> data = new ArrayList<>();
        // 模拟近7天数据
        for (int i = 6; i >= 0; i--) {
            data.add(Map.of(
                "day", java.time.LocalDate.now().minusDays(i).toString(),
                "users", 0
            ));
        }
        
        ctx.json(ApiResponse.success(data));
    }
}
