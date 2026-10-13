# 课程实训成果管理平台 - 后端2（feature/task-class）

> 负责人：施炀
> 范围：班级模块 + 实训任务模块（class、training_task 表数据库操作）

## 一、本模块交付内容

- 班级：新增 / 修改 / 删除（逻辑删除）/ 分页查询 / 单条查询
- 实训任务：发布 / 修改 / 删除（逻辑删除）/ 分页查询 / 单条查询
- 共 10 个接口，路径严格按组长下发的【接口文档】实现，详见 `docs/后端2-接口文档.md`

## 二、技术选型说明（需在群里同步）

| 项 | 选择 | 说明 |
|----|------|------|
| 框架 | Spring Boot 2.7.18 | 稳定、课程常用 |
| ORM | MyBatis-Plus 3.5.5 | 分页结构 records/total/current/size 天然匹配统一规范 |
| JDK | 8 | 兼容性最好 |
| 逻辑删除 | `@TableLogic` | DELETE 自动变 UPDATE is_deleted=1，查询自动过滤已删数据 |
| 日期格式 | 全局 Jackson 配置 | 统一 `yyyy-MM-dd HH:mm:ss` |
| 鉴权 | JWT（jjwt 0.11.5） | 见下方「与后端1 的对接点」 |

## 三、项目结构

```
backend/
├─ pom.xml
├─ src/main/java/com/training/
│  ├─ TrainingPlatformApplication.java   启动类
│  ├─ common/                            通用层（后端1/3 共用）
│  │  ├─ Result.java                     统一返回体
│  │  ├─ PageResult.java                 分页返回结构
│  │  ├─ BizException.java               业务异常
│  │  ├─ GlobalExceptionHandler.java     全局异常处理
│  │  └─ UserContext.java                当前登录用户上下文（ThreadLocal）
│  ├─ util/JwtUtil.java                  JWT 工具
│  ├─ config/                            配置层
│  │  ├─ MybatisPlusConfig.java          Mapper扫描 + 分页插件
│  │  ├─ JacksonConfig.java              日期格式
│  │  ├─ MyMetaObjectHandler.java        create_time/update_time 自动填充
│  │  ├─ AuthInterceptor.java            登录态拦截器（占位，待与后端1统一）
│  │  └─ WebConfig.java                  拦截器注册
│  ├─ entity/                            ClassInfo、TrainingTask
│  ├─ mapper/                            ClassMapper、TrainingTaskMapper
│  ├─ dto/                               ClassDTO、TaskDTO
│  ├─ service/ (+impl)                   服务层
│  └─ controller/                        ClassController、TrainingTaskController
├─ src/main/resources/application.yml
└─ docs/后端2-接口文档.md
```

## 四、如何运行

1. 用组长下发的【创建数据库源码】在本地 MySQL 8.0 建库 `training_platform`。
2. 修改 `src/main/resources/application.yml` 中的 `username` / `password`。
3. 启动：
   ```bash
   mvn spring-boot:run
   ```
   或 IDEA 里直接运行 `TrainingPlatformApplication`。

## 五、与后端1 的对接点（重要，需群内对齐）

1. **token 格式**：本模块鉴权拦截器按 `Authorization: Bearer {token}`、JWT、`subject = userId`、`claim role` 解析。
   - 后端1 生成 token 时请保持一致（`JwtUtil.createToken(userId, role)` 已提供，可直接复用）。
   - `jwt.secret` 必须两边一致，否则校验不过。
2. **登录态拦截器归属**：`AuthInterceptor` 目前是占位实现，仅拦截 `/api/class/**` 和 `/api/training-task/**`。
   - 建议后端1 把它上收为全局拦截器（拦 `/api/**`，放行 `/api/user/login`），并统一写入 `UserContext`。
   - 业务代码只依赖 `UserContext`，拦截器怎么改都不影响我的 Controller。
3. **发布任务的创建人**：`POST /api/training-task` 的 `createUserId` 从 token 取当前教师 id，前端不用传。

## 六、与后端3 的对接点

- `submission.task_id` 关联本模块 `training_task.id`；后端3 查提交时如需联表拿任务名，可用 `training_task.id` 反查，我这里任务详情接口 `GET /api/training-task/{id}` 已就绪。

## 七、待办 / 可后续补充

- [ ] 单元自测（当前仅覆盖契约工具类，见 `src/test`）
- [ ] `status` 是否需要按 deadline 自动流转（not_start → ongoing → finished），待组内确认
- [ ] 角色级权限（仅教师/管理员可发布任务）细化，待后端1 统一拦截器落地后补充
