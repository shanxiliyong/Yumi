# Yumi - 智能 AI Agent 平台

<div align="center">

**优秘** - 基于 Spring AI Alibaba 的多 Agent 智能对话平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Vue](https://img.shields.io/badge/Vue-3-green.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2-blue.svg)](https://github.com/alibaba/spring-ai-alibaba)

</div>

---

## 📖 项目简介

Yumi（优秘）是一个基于 **Spring AI Alibaba** 框架开发的企业级多 Agent 智能对话平台，支持多种大语言模型（智谱 AI、通义千问等），提供完整的 Agent 管理、工具注册、技能管理、会话管理等功能。

### ✨ 核心特性

- 🤖 **多模型支持** - 支持智谱 AI（GLM）、通义千问（Qwen）等多种大模型，可灵活切换
- 🧠 **多 Agent 架构** - 支持父子 Agent 层级协作，每个 Agent 可配置不同的技能和工具
- 🔧 **工具系统** - 支持工具注册、权限管理、并发执行，可扩展 RPC 泛化调用
- 📚 **技能管理** - 支持 Markdown 格式的技能定义，按租户隔离加载
- 💬 **会话管理** - 完整的会话生命周期管理，支持多会话切换
- 🧵 **长记忆** - 基于 JDBC 的对话记忆存储，支持上下文自动压缩
- 🎭 **数字人** - 支持多种数字人角色，个性化提示词配置
- 🔄 **流式输出** - 支持 SSE 流式响应，实时展示 AI 回复
-  **性能监控** - 各阶段埋点耗时统计，Token 消耗追踪

---

## ️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (Vue 3)                            │
│  ┌──────────┐  ┌──────────  ┌──────────┐  ┌──────────┐        │
│  │ 对话界面  │  │ 管理后台  │  │ Dashboard│  │ 工具管理  │        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  ────┬─────┘        │
───────┼─────────────┼─────────────┼─────────────┼───────────────┘
        │             │             │             │
        └─────────────┴─────────────┴─────────────
                      │ HTTP / SSE
┌─────────────────────┼───────────────────────────────────────────┐
│                     ▼                                           │
│                    后端层 (Spring Boot 3.5.4)                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Controller 层                          │   │
│  │  ChatController │ SessionController │ ToolController     │   │
│  └────────────────────────┬─────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼─────────────────────────────────┐   │
│  │                    Service 层                             │   │
│  │  ChatService │ SessionService │ ToolService │ SkillService│   │
│  └────────────────────────┬─────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼─────────────────────────────────   │
│  │                  Agent 核心层                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ BaseYumiAgent│ │AgentBuilder │ │ YumiContext │       │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────       │   │
│  │         │                │                │               │   │
│  │  ┌──────▼────────────────▼────────────────▼──────┐        │   │
│  │  │              ReactAgent (LangGraph)            │        │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────────────┐  │        │   │
│  │  │  │  Tools  │ │ Skills  │ │  Sub-Agents     │  │        │   │
│  │  │  └─────────┘ └─────────┘ └─────────────────┘  │        │   │
│  │  └────────────────────────────────────────────────        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼─────────────────────────────────   │
│  │                    Hook 钩子层                            │   │
│  │  LogAgentHook │ LogModelHook │ SummarizationHook         │   │
│  │  ShellToolAgentHook │ SkillsAgentHook                    │   │
│  └──────────────────────────────────────────────────────────┘   │
─────────────────────────────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
┌──────────────┐ ┌──────────┐ ┌──────────┐
│    MySQL     │ │  Redis   │ │  LLM API │
│  8.0+        │ │ +Redisson│ │ 智谱/通义 │
└──────────────┘ └──────────┘ └──────────┘
```

### 多 Agent 协作架构

```
                    ┌─────────────────────┐
                    │     父 Agent        │
                    │  (任务调度/协调者)   │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼────────┐ ┌────▼─────┐ ┌───────▼──────
    │   子 Agent A      │ │ 子 Agent B│ │  子 Agent C   │
    │  (数据库查询)     │ │(写作工具) │ │ (翻译工具)   │
    └──────────────────┘ └────────── └──────────────┘
              │                │                │
    ┌─────────▼────────┐ ┌────▼─────┐ ┌───────▼──────┐
    │   Tools:         │ │ Tools:   │ │ Tools:       │
    │ - execute_sql    │ │ - writer │ │ - translator │
    │ - show_tables    │ │          │ │              │
    └──────────────────┘ └──────────┘ └──────────────┘
```

### 数据流转图

```
用户输入
  │
  ▼
┌─────────────┐    ┌──────────────┐    ┌──────────────┐
│ ChatController│──▶│ YumiContext  │──▶│ AgentBuilder │
└─────────────┘    ──────────────┘    └─────────────┘
                                              │
                                    ┌─────────▼──────────┐
                                    │   ReactAgent       │
                                    │  (LangGraph 图执行) │
                                    └─────────┬──────────┘
                                              │
                        ┌─────────────────────┼─────────────────────┐
                        │                     │                     │
                  ┌─────▼─────┐        ┌──────▼──────┐      ┌───────▼───────┐
                  │  LLM 推理  │        │  Tool 调用  │      │  Skill 加载   │
                  └─────┬─────┘        └────────────┘      └──────────────┘
                        │                     │                     │
                        └─────────────────────┼─────────────────────┘
                                              │
                                    ┌─────────▼──────────┐
                                    │   Hook 处理        │
                                    │ - 日志记录         │
                                    │ - 上下文压缩       │
                                    │ - 权限校验         │
                                    └─────────┬──────────┘
                                              │
                                    ┌─────────▼──────────┐
                                    │   SSE 流式返回     │
                                    ─────────┬──────────┘
                                              │
                                              ▼
                                          前端展示
```

---

## 🛠️ 技术栈

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.4 | 应用框架 |
| Spring AI Alibaba | 1.1.2 | AI Agent 框架 |
| Java | 21 | 开发语言 |
| MySQL | 8.0+ | 数据存储 |
| Redis + Redisson | 6.0+ | 缓存与分布式锁 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| Spring Data JPA | - | 数据访问 |
| Lombok | - | 代码简化 |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | - | 前端框架 |
| Vite | - | 构建工具 |
| Element Plus | - | UI 组件库 |
| Marked | - | Markdown 渲染 |

### AI 模型
| 模型 | 提供商 | 用途 |
|------|--------|------|
| GLM-4-Flash | 智谱 AI | 通用对话 |
| qwen3.7-max | 通义千问 | 复杂任务处理 |

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+（前端开发）

### 1. 克隆项目

```bash
git clone https://github.com/your-username/Yumi.git
cd Yumi
```

### 2. 配置环境变量

项目使用环境变量管理敏感的 API Key，请按照以下方式配置：

#### 方式一：IDEA 运行配置（推荐开发环境）

1. 打开 IDEA，点击 **Run → Edit Configurations**
2. 找到 `YumiApplication` 配置
3. 在 **Environment variables** 中添加：

```
ZHIPUAI_API_KEY=你的智谱AI_API_Key;DASHSCOPE_API_KEY=你的通义千问_API_Key
```

#### 方式二：命令行环境变量

```bash
# macOS/Linux
export ZHIPUAI_API_KEY=你的智谱AI_API_Key
export DASHSCOPE_API_KEY=你的通义千问_API_Key

# Windows (CMD)
set ZHIPUAI_API_KEY=你的智谱AI_API_Key
set DASHSCOPE_API_KEY=你的通义千问_API_Key

# Windows (PowerShell)
$env:ZHIPUAI_API_KEY="你的智谱AI_API_Key"
$env:DASHSCOPE_API_KEY="你的通义千问_API_Key"
```

### 3. 初始化数据库

#### 执行初始化脚本

```bash
# 使用 MySQL 命令行执行初始化脚本
mysql -u root -p < src/main/resources/schema/init.sql
```

或者在 MySQL 客户端中执行：

```sql
source /path/to/Yumi/src/main/resources/schema/init.sql;
```

#### 数据库表结构

| 表名 | 说明 | 用途 |
|------|------|------|
| `chat_sessions` | 会话表 | 存储用户会话信息，关联数字人ID |
| `GRAPH_THREAD` | 线程表 | Agent 图执行线程管理 |
| `GRAPH_CHECKPOINT` | 检查点表 | Agent 执行状态断点保存 |
| `digital_human` | 数字人/Agent表 | 数字人和 Agent 统一管理，支持父子层级 |
| `skill` | 技能表 | 技能定义和管理 |
| `tool` | 工具表 | 工具注册和配置 |
| `rpc_interface` | RPC接口表 | RPC 接口定义 |
| `id_generator` | ID生成器表 | 分布式 ID 生成 |

#### 修改数据库配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yumi?useUnicode=true&characterEncoding=UTF-8&useSSL=false
    username: your_username
    password: your_password
```

#### 修改 Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

### 4. 启动后端

```bash
# 编译项目
mvn clean install

# 启动应用
mvn spring-boot:run
```

或者直接运行主类 `yumi.YumiApplication`

启动成功后，后端服务将运行在 `http://localhost:8080`

### 5. 启动前端（可选）

```bash
cd src/frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将运行在 `http://localhost:5173`

---

## 📋 功能说明

### 核心功能

#### 1. 智能对话
- 支持流式（SSE）和非流式对话
- 多会话管理，可同时维护多个对话
- 对话历史记录和上下文管理
- 会话名称自动生成（数字人名称-序号）

#### 2. Agent 管理
- 创建和配置多个 AI Agent
- 父子 Agent 层级协作架构
- 为 Agent 关联不同的技能（Skill）和工具（Tool）
- 自定义系统提示词
- 支持多 Agent 并发执行

#### 3. 工具系统（Tool）
- 工具注册和管理（system/rpc 类型）
- 工具执行前权限确认
- 支持并发执行多个工具
- 可扩展 RPC 泛化调用
- 内置工具：Web 搜索、Shell 执行、上下文压缩

#### 4. 技能管理（Skill）
- Markdown 格式技能定义
- 技能预览和编辑
- 按租户隔离加载
- 底层调用 Skill 运营 Agent 创建

#### 5. 数字人
- 多种数字人角色管理
- 父子数字人层级关系
- 个性化提示词配置
- 多数字人切换

#### 6. 会话管理
- 创建、更新、删除会话
- 会话历史记录
- 断点续传支持（基于 LangGraph Checkpoint）
- 会话关联数字人ID

#### 7. 上下文压缩
- 自动压缩长对话历史
- 减少 Token 消耗
- 基于 SummarizationHook 实现

---

## 📁 项目结构

```
Yumi/
├── src/
│   ├── main/
│   │   ├── java/yumi/
│   │   │   ├── agent/              # Agent 核心
│   │   │   │   ├── BaseYumiAgent.java      # Agent 基础实现
│   │   │   │   ├── YumiAgent.java          # Agent 接口
│   │   │   │   └── AgentBuilderService.java # Agent 构建服务
│   │   │   ├── common/             # 公共组件
│   │   │   │   ├── YumiContext.java        # 上下文对象
│   │   │   │   └── JackJsonUtil.java       # JSON 工具
│   │   │   ├── config/             # 配置类
│   │   │   │   ├── CorsConfig.java         # 跨域配置
│   │   │   │   ├── MemConfig.java          # 记忆配置
│   │   │   │   └── StrategyConfig.java     # 策略配置
│   │   │   ├── entity/             # 实体类
│   │   │   │   ├── DigitalHumanEntity.java # 数字人实体
│   │   │   │   ├── SessionEntity.java      # 会话实体
│   │   │   │   ├── SkillEntity.java        # 技能实体
│   │   │   │   └── ToolEntity.java         # 工具实体
│   │   │   ├── hook/               # Hook 钩子
│   │   │   │   ├── LogAgentHook.java       # Agent 日志
│   │   │   │   ├── LogModelHook.java       # 模型日志
│   │   │   │   └── PersonalizedPromptInterceptor.java
│   │   │   ├── mapper/             # MyBatis Mapper
│   │   │   ├── mvc/                # Controller 层
│   │   │   │   ├── ChatController.java     # 对话接口
│   │   │   │   ├── SessionController.java  # 会话管理
│   │   │   │   ├── SkillController.java    # 技能管理
│   │   │   │   ├── ToolController.java     # 工具管理
│   │   │   │   └── DigitalHumanController.java
│   │   │   ├── service/            # 业务逻辑层
│   │   │   ├── skill/              # 技能注册
│   │   │   │   └── DatabaseSkillRegistry.java
│   │   │   └── tool/               # 工具实现
│   │   │       ├── SystemToolRegistry.java # 系统工具注册
│   │   │       ├── RpcToolCallback.java    # RPC 工具
│   │   │       └── RpcToolConfig.java      # RPC 配置
│   │   └── resources/
│   │       ├── application.yml     # 应用配置
│   │       ├── config/
│   │       │   └── strategies.json # 策略配置
│   │       ├── mapper/             # MyBatis XML
│   │       ── schema/
│   │           └── init.sql        # 数据库初始化脚本
│   └── frontend/                   # Vue 前端项目
│       ├── src/
│       │   ├── components/
│       │   │   ├── ChatPage.vue    # 对话页面
│       │   │   ├── AdminPage.vue   # 管理后台
│       │   │   ├── DashboardPage.vue # 数据看板
│       │   │   ├── ToolManage.vue  # 工具管理
│       │   │   └── SkillManage.vue # 技能管理
│       │   └── App.vue
│       ├── package.json
│       └── vite.config.js
├── doc/                            # 项目文档
│   ├── prd/                        # 产品需求文档
│   └── 待实现功能列表.md
├── .gitignore
├── pom.xml                         # Maven 配置
└── readme.md                       # 项目说明
```

---

## 🔌 API 接口

### 对话接口

```
POST /api/chat/stream          # 流式对话（SSE）
POST /api/chat/send            # 非流式对话
```

### 会话管理

```
GET    /api/sessions           # 获取用户会话列表
POST   /api/sessions           # 创建新会话
PUT    /api/sessions/{id}      # 更新会话
DELETE /api/sessions/{id}      # 删除会话
```

### 工具管理

```
GET    /api/tool               # 工具列表（分页）
GET    /api/tool/all           # 所有工具
GET    /api/tool/{id}          # 工具详情
POST   /api/tool               # 创建工具
PUT    /api/tool/{id}          # 更新工具
DELETE /api/tool/{id}          # 删除工具
```

### 技能管理

```
GET    /api/skill              # 技能列表（分页）
GET    /api/skill/all          # 所有技能
POST   /api/skill              # 创建技能
PUT    /api/skill/{id}         # 更新技能
DELETE /api/skill/{id}         # 删除技能
```

### 数字人管理

```
GET    /api/digital-human      # 数字人列表
GET    /api/digital-human/children  # 子数字人列表
```

---

## ️ 开发指南

### 添加新工具

1. 在数据库 `tool` 表中添加工具配置
2. 实现工具执行逻辑
3. 通过 `/api/tool` 接口注册工具

### 添加新技能

1. 创建 Skill 实体
2. 编写 Markdown 格式的技能描述
3. 通过 `/api/skill` 接口注册技能
4. 将技能关联到 Agent

### 自定义 Hook

继承相应的 Hook 类，实现自定义逻辑：

```java
public class MyCustomHook extends MessagesAgentHook {
    @Override
    public void beforeAgent(...) {
        // Agent 执行前的逻辑
    }
    
    @Override
    public void afterAgent(...) {
        // Agent 执行后的逻辑
    }
}
```

### 添加子 Agent

1. 在 `digital_human` 表中创建子 Agent 记录
2. 设置 `parent_code` 为父 Agent 的 code
3. 设置 `agent_type = 'child'`
4. 设置 `multi_agent_enabled = 1`
5. 配置子 Agent 的技能和工具

---

##  配置说明

### application.yml 主要配置项

```yaml
spring:
  ai:
    zhipuai:
      api-key: ${ZHIPUAI_API_KEY}     # 智谱 AI API Key
      chat:
        options:
          model: GLM-4-Flash          # 模型选择
    
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}   # 通义千问 API Key
      chat:
        options:
          model: qwen3.7-max          # 模型选择
    
    chat:
      memory:
        store:
          type: jdbc                  # 记忆存储类型
  
  datasource:                         # 数据库配置
  data:
    redis:                            # Redis 配置
```

### strategies.json 策略配置

```json
{
  "id": "strategy-multi-stream",
  "name": "多智能体协作—流式",
  "description": "基于工具的多智能体协作模式",
  "requestType": "stream",
  "beanName": "multiReactAgent",
  "instruction": "系统提示词..."
}
```

---

## 🧪 测试

### 使用 promptfoo 进行 Agent 评测

```bash
cd src/promptfoo
promptfoo eval
```

---

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

##  许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## 🙏 致谢

- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) - AI 框架支持
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Vue.js](https://vuejs.org/) - 前端框架
- [Element Plus](https://element-plus.org/) - UI 组件库
- [LangGraph](https://langchain-ai.github.io/langgraph/) - Agent 图执行框架

---

## 📧 联系方式

如有问题或建议，欢迎加V(13260425736)沟通。

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！**

Made with ❤️ by Yumi Team

</div>