# Task Editor Web UI - 技术规格

## 概述

PlayerTaskX 插件的 Web 管理界面，提供可视化任务编辑器，支持通过连线方式管理任务逻辑。

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 前端框架 | Vue 3 | 渐进式 JS 框架 |
| 可视化库 | Vue Flow | 节点编辑器，支持连线/拖拽 |
| 后端 | Java + Javalin | 插件内嵌 HTTP 服务器 |
| 认证 | htpasswd | 基础认证 |
| 部署 | nginx/caddy 反代 | HTTPS + 端口转发 |

## 架构

```
[Admin Browser] --HTTPS--> [nginx/caddy] --proxy--> [127.0.0.1:22222]
                                                      |
                                                      +-- static files (Vue Flow app)
                                                      +-- /api/* (REST endpoints)
```

## 部署方案

### 开发环境
- 插件监听 `127.0.0.1:22222`
- 前端 `npm run dev` 独立运行（Vite proxy 到后端）

### 生产环境
- 插件打包内嵌静态文件
- nginx/caddy 反向代理 + HTTPS + htpasswd 认证
- 标准 80/443 端口对外

## API 设计

### 统一响应格式
```json
{
  "code": 0,
  "msg": "ok",
  "data": {}
}
```

### 端点列表

#### 任务管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/quests | 获取所有任务 |
| GET | /api/quests/:id | 获取单个任务 |
| POST | /api/quests | 创建任务 |
| PUT | /api/quests/:id | 更新任务 |
| DELETE | /api/quests/:id | 删除任务 |

#### 奖励模板
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/rewards/templates | 获取所有模板 |
| POST | /api/rewards/templates | 创建/更新模板 |
| DELETE | /api/rewards/templates/:id | 删除模板 |

#### 玩家进度
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/players/progress | 获取玩家进度（分页+筛选） |

#### 统计数据
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/stats/completion | 完成状态分布 |
| GET | /api/stats/activity | 活动趋势 |

## 前端功能

### 可视化任务编辑器
- 节点表示任务
- 连线表示任务依赖/触发关系
- 支持拖拽创建节点
- 支持编辑节点属性（名称、目标、奖励等）
- 支持缩放/平移画布
- 支持导出/导入任务配置

### 奖励库
- 奖励模板 CRUD
- 模板关联到任务节点

### 玩家进度
- 玩家列表
- 进度查看
- 状态筛选

### 统计面板
- 任务完成率饼图
- 活动趋势折线图

## 数据模型

### Quest (任务)
```typescript
interface Quest {
  id: string;
  name: Record<string, string>;  // 多语言 { "zh-CN": "任务名", "en-US": "Quest" }
  description: Record<string, string>;
  type: 'single' | 'multi' | 'series';
  objectives: QuestObjective[];
  rewards: QuestReward[];
  createdAt: number;
  updatedAt: number;
}
```

### QuestObjective (任务目标)
```typescript
interface QuestObjective {
  id: string;
  type: string;       // 'break' | 'kill' | 'collect' | ...
  target: string;     // 方块ID/实体类型
  count: number;
  finished: boolean;
}
```

### QuestReward (任务奖励)
```typescript
interface QuestReward {
  id: string;
  type: 'item' | 'xp' | 'money' | 'command';
  value: string | number;
  meta?: any;
}
```

### RewardTemplate (奖励模板)
```typescript
interface RewardTemplate {
  id: string;
  name: string;
  type: 'item' | 'xp' | 'money' | 'command';
  value: string | number;
  meta?: any;
}
```

## 后端实现

### EditorServer
- 监听 `127.0.0.1:22222`
- Serve 静态文件（Vue build output）
- CORS 配置（仅允许本地访问）
- 统一异常处理

### TaskEditorController
- 调用 `TaskManager` 进行 CRUD
- 统一 `ApiResponse` 封装
- 参数校验

### 持久化
- 复用 `TaskManager.taskStorage`
- 奖励模板存储待定（可复用现有 rewardStorage）

## 文件结构

```
PlayerTaskX/
├── core/src/main/java/.../web/
│   ├── EditorServer.java       # HTTP 服务器
│   ├── TaskEditorController.java
│   ├── ApiResponse.java        # 统一响应封装
│   └── static/                 # Vue build 输出
│       ├── index.html
│       ├── assets/
│       └── ...
└── task-editor/                # Vue 源码
    ├── src/
    │   ├── components/
    │   │   ├── QuestCanvas.vue    # Vue Flow 画布
    │   │   ├── QuestNode.vue      # 任务节点
    │   │   └── ...
    │   ├── views/
    │   │   ├── Editor.vue         # 可视化编辑器页
    │   │   ├── RewardLibrary.vue   # 奖励库页
    │   │   ├── PlayerProgress.vue # 玩家进度页
    │   │   └── Statistics.vue     # 统计页
    │   ├── services/
    │   │   └── api.ts
    │   └── main.ts
    └── ...
```

## 部署配置示例

### Caddyfile
```
example.com {
    reverse_proxy /api/* localhost:22222
    reverse_proxy /* localhost:22222
    basicauth /* {
        admin $2a$12$...
    }
}
```

### nginx (备选)
```nginx
server {
    listen 443 ssl;
    server_name example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location /api/ {
        proxy_pass http://127.0.0.1:22222;
    }
    location / {
        proxy_pass http://127.0.0.1:22222;
    }
    
    auth_basic "Restricted";
    auth_basic_user_file /path/to/.htpasswd;
}
```

## 安全考虑

1. 仅监听 `127.0.0.1`，不暴露公网
2. 通过反向代理 + HTTPS + 认证保护
3. 插件内部无权限验证（依赖代理层）
4. 静态文件由插件托管，防止目录遍历

## 待定事项

- [ ] 奖励模板存储方案（复用现有还是独立）
- [ ] 任务节点的具体 UI 设计
- [ ] 连线条件的表达方式
