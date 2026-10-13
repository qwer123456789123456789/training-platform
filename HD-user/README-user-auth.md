# 后端 1｜feature/user-auth 用户与权限模块

负责人：后端 1（用户模块 + 登录/token/权限校验）
依赖：Spring Boot 4.1.1 + Spring Framework 7 + JDK 21 + MySQL 8

---

## 1. 交付范围

| 接口 | 方法 | 说明 | 权限 |
|---|---|---|---|
| /api/user/login | POST | 登录，返回 token | 匿名 |
| /api/user/page | GET | 分页查询（current/size/username/classId） | admin（教师查询待确认） |
| /api/user/{id} | GET | 查询单个用户 | 本人或 admin |
| /api/user | POST | 新增用户 | admin |
| /api/user/{id} | PUT | 修改用户 | admin 改任何人；其他角色只能改自己 |
| /api/user/{id} | DELETE | 逻辑删除 | admin |

同时提供了全组共用的基础设施：统一返回体 `Result`、分页体 `PageResult`、全局异常处理、
token 签发与校验、`@RequireRole` 权限注解、跨域配置。

```
src/main/java/com/util/demo2/
├── common/     Result、PageResult、BusinessException、GlobalExceptionHandler
├── auth/       JwtUtil、LoginUser(ThreadLocal)、RequireRole、AuthInterceptor
├── config/     WebConfig(拦截器+跨域)、AppConfig(BCrypt)、AdminInitializer(初始化管理员)
└── user/       entity / dto / vo / repository / service / controller
```

## 2. 启动步骤

1. 先执行建库脚本 `create-database.sql`（本仓库 database 目录 / 群文件里的"创建数据库源码"）。
2. 改 `src/main/resources/application.properties` 里的 `spring.datasource.password`。
3. 启动 `Demo2Application`，首次启动会自动插入管理员账号 **admin / 123456**（开关 `app.init-admin`）。
4. 用 admin 登录拿 token，再调其它接口。

## 3. 怎么测试（四层，从快到慢）

### 第 1 层：单元测试（不用数据库，最快，适合反复跑）

```powershell
cd D:\网络工程\AAA-Stu\实践2\demo2
.\mvnw.cmd test -Dtest="JwtUtilTest,UserServiceTest"
```

- `JwtUtilTest`：token 签发/解析、篡改 payload、换密钥、过期、空串、密钥过短等 8 个用例。
- `UserServiceTest`：登录 4 个分支（成功/密码错/账号不存在/被禁用）、密码是否真的加密成 BCrypt、
  角色非法、密码过短、**学生提权改 admin 被 403**、改别人被 403、不能删自己、不能删最后一个管理员、逻辑删除等 14 个用例。

> 注意：工程里原有的 `Demo2ApplicationTests` 是 `@SpringBootTest`，会启动整个容器并连数据库。
> 如果数据库还没准备好，用上面的 `-Dtest=` 只跑本模块单测；想全跑就先建好库。

### 第 2 层：接口连通性冒烟（需要先启动应用）

```powershell
cd D:\网络工程\AAA-Stu\实践2\demo2
.\api-test.ps1
# 或指定参数
.\api-test.ps1 -BaseUrl http://localhost:8080 -AdminUser admin -AdminPassword 123456
```

`api-test.ps1` 是端到端自测脚本，共 40+ 条断言，覆盖：登录成功/失败、无 token 与非法 token、
`Authorization: Bearer` 与 `token:` 两种请求头、分页参数边界（current=0、size=500）、
新增用户与 4 类参数校验、学生的越权访问、提权防护、禁用后旧 token 立即失效、重置密码、
删除自己的护栏、逻辑删除后 token 失效、同名账号被已删除用户占用的坑。
全部通过退出码 0，有失败退出码 1（可直接贴到实训报告里）。

### 第 3 层：手工验证数据库落库结果

```sql
USE training_platform;

-- 1. 密码必须是 BCrypt 哈希（$2a$ 开头），绝对不能是明文
SELECT id, username, password, role, status, is_deleted FROM `user`;

-- 2. 逻辑删除后 is_deleted 变成 1（行还在，这是逻辑删除；注意别写成 NULL）
SELECT id, username, is_deleted, update_time FROM `user` WHERE is_deleted = 1;

-- 3. 逻辑删除不会释放 username 唯一索引（这是个已知坑）
SELECT username, COUNT(*) FROM `user` GROUP BY username HAVING COUNT(*) > 1;
```

### 第 4 层：前端联调前，用 Apifox/Postman 按《接口文档》再点一遍

重点确认三件事：返回体是 `{code,msg,data}`、分页 data 只有 `records/total/current/size`、
401 时 HTTP 状态码仍是 200（前端要判断 `res.data.code`）。

### 常见问题排查

| 现象 | 原因 |
|---|---|
| 启动报 `Unknown database 'training_platform'` | 还没执行建库脚本 |
| 启动报 `Access denied for user 'root'` | `application.properties` 里密码没改 |
| 启动报 `app.jwt.secret 长度不能小于 32` | 密钥被改短了 |
| 所有接口都 401 | 请求头没带 token，或 token 已过期（默认 120 分钟） |
| 学生调接口返回 403 | 正常，权限注解拦住了；查 `/api/user/page` 需要 admin |
| 新增用户报"账号名被一个已删除的用户占用" | `username` 唯一索引 + 逻辑删除的必然结果，见第 5 节 |

## 4. 必须和组内确认的三件事（不要私自定）

1. **token 放哪个请求头**：《统一接口规范》写 `token: xxx`，《接口文档》写 `Authorization: Bearer {token}`，两份文档冲突。
   现在**两种都支持**，但前端只能选一种，请群内定一个。
2. **HTTP 状态码**：本模块一律返回 HTTP 200，业务状态放在 body 的 `code`。
   前端 axios 拦截器必须判断 `res.data.code === 401` 跳登录，**不要判断 HTTP 状态码**。需要同步给 6 号对接员。
3. **错误码扩展**：规范只定义了 200/401/403/500。参数错误、账号已存在这类业务失败如果返回 500，前端会统一弹"服务器异常"，体验很差。
   本模块新增了 **400 = 参数/业务校验失败**，需要全组接受（否则我把 400 改成 500，但那样前端提示会不准确）。

补充：登录返回体在文档基础上补了 `id` 和 `classId`（学生提交成果要 studentId、教师查本班学生要 classId），也需要同步。

## 5. 需要同步给"建库脚本负责人"的表结构问题

| 问题 | 影响 | 建议 |
|---|---|---|
| `username` 有唯一索引 + 逻辑删除 | 删掉张三后再建同名账号会报 1062，用户以为系统坏了 | 删除时改写 username（如 `s001_deleted_1699...`），或唯一键改成 `(username, is_deleted)` |
| `is_deleted TINYINT DEFAULT 0` 可空 | NULL 行会被 `WHERE is_deleted = 0` 漏掉 | 改成 `NOT NULL DEFAULT 0` |
| `role` 是 VARCHAR 无约束 | 写错角色字符串就变"无权限"，排查困难 | `ENUM('student','teacher','admin')` 或 CHECK |
| 无索引 | 分页查询全表扫 | `KEY idx_user_class(class_id)`、`KEY idx_user_role(role)` |

## 6. 技术选型说明（为什么没用 MyBatis-Plus）

本工程是 **Spring Boot 4.1.1 + Spring Framework 7 + Jackson 3**，而 MyBatis-Plus 目前主线面向 Boot 3.x，
自动装配与 API 都可能有兼容问题，课程项目里一旦跑不起来很耽误事。所以：

- 数据访问用官方 `spring-boot-starter-jdbc` + `JdbcTemplate`，SQL 全部显式书写，逻辑删除也是显式带 `is_deleted = 0`；
- token 用 **手写 HS256 JWT**（JDK 自带 HMAC），不引 jjwt——jjwt 依赖 Jackson 2，和 Boot 4 的 Jackson 3 混用有风险；
- 密码用 `spring-security-crypto` 的 BCrypt（无传递依赖）。

如果组里最终统一用 MyBatis-Plus，需要先确认它对 Boot 4 的支持；确认可用后，本模块只需把
`UserRepository` 换成 `UserMapper` 接口 + XML/注解，`UserService` 及上层代码不用改。

## 7. 验证状态

- ✅ **编译验证通过**：main 19 个类 + test 2 个类，用 Spring Framework 7.0.9 / Boot 4.1.1 /
  Jackson 3.1.5 / jakarta.servlet 6.1.0 / spring-security-crypto 7.1.1 真实依赖编译，
  18 + 2 个 class 文件全部生成，无错误无警告。
- ⏳ **尚未运行验证**：单元测试与 `api-test.ps1` 还没在跑起来的应用上执行过（生成环境的沙箱无法连本机 MySQL）。
  你跑完把结果发我，有失败项我直接改。
- 需要"退出登录/强制下线"功能的话要额外做（黑名单或 token 表），先确认是否算在本模块范围。
