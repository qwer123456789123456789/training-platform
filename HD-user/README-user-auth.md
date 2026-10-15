# 后端 1｜feature/user-auth 用户与权限模块

负责人：后端 1（用户模块 + 登录/token/权限校验）
依赖：Spring Boot 4.1.1 + Spring Framework 7 + JDK 21 + MySQL 8

---

## 1. 交付范围

| # | 接口 | 方法 | 说明 | 权限 |
|---|---|---|---|---|
| 1 | /api/user/login | POST | 登录，返回 token + 用户信息 | 匿名（白名单） |
| 2 | /api/user/page | GET | 分页查询（current/size/username/role/classId/status） | admin（教师查询待确认） |
| 3 | /api/user/{id} | GET | 查询单个用户 | 本人或 admin |
| 4 | /api/user | POST | 新增用户 | admin |
| 5 | /api/user/{id} | PUT | 修改用户 | admin 改任何人；其他角色只能改自己 |
| 6 | /api/user/{id} | DELETE | 逻辑删除 | admin |

> `/api/user/page` 的 `role`、`status` 两个过滤参数是本次为"管理员用户列表页"补的（可选，不传即不过滤），
> 已在 README 第 8 节和设计说明里注明，前端可以不使用，不影响原有调用。

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

## 3. 怎么测试（五层，从快到慢）

整批 `mvnw.cmd test` 现在是 **8 个测试类、110 个用例**：前 5 个类用 Mock（秒级、不连库），
后面 2 个类**真连 MySQL**，最后 1 个是容器启动冒烟（也连库）。

### 第 1 层：单元测试（Mock，不连数据库，最快，适合反复跑）

```powershell
cd D:\网络工程\AAA-Stu\实践2\HD-user

# 只跑这一层（秒级，不需要 MySQL）
.\mvnw.cmd test -Dtest="UserServiceTest,UserServicePageTest,AuthInterceptorTest,UserApiContractTest,JwtUtilTest"

# 全量（含下面第 2 层的真库测试，需要 MySQL 已启动）
.\mvnw.cmd test
```

| 测试类 | 用例 | 覆盖点 |
|---|---|---|
| `user.service.UserServiceTest` | 35 | 登录各分支、密码是否真的加密成 BCrypt、**学生提权改 admin 被 403**、改别人 403、账号不可修改、**新密码过短必须在写库前就被拒绝**、角色与班级一致性、不能删自己、不能删/降级最后一个管理员、逻辑删除 |
| `user.service.UserServicePageTest` | 8 | 分页偏移量、脏参数夹紧（current=0、size=999999 → 100）、账号/角色/班级/状态四个过滤条件、总数为 0 时不查列表、实体转 VO、**UserVO 不含 password 字段** |
| `auth.AuthInterceptorTest` | 13 | 无 token / 篡改 token / 过期 → 401，账号被禁用、被删除后旧 token 立即失效，**角色以数据库为准**，角色不匹配 → 403，放行后写入 LoginUser，两种请求头，ThreadLocal 清理 |
| `user.controller.UserApiContractTest` | 16 | MockMvc 跑通「Controller + @Valid + 全局异常 + 拦截器」整条链路：返回体结构、401/403/400 业务码、分页 data 形态、非法 JSON、参数类型错误、`password:""` 必须能保存 |
| `auth.JwtUtilTest` | 8 | token 三段式、篡改 payload、换密钥、过期、空串、密钥过短启动即报错 |

> Mock 单测的价值是"快"：改完 Service 立刻能验证业务分支。但它**不执行任何 SQL**（Repository 是假的），
> 所以 SQL 写错它发现不了——这正是下面第 2 层要补的。

### 第 2 层：真连 MySQL 的集成测试（SQL 真的执行）

```powershell
# 前置：MySQL 已启动 + 已执行 database/create-database.sql
.\mvnw.cmd test -Dtest="UserRepositoryDbTest,UserServiceDbTest"
```

| 测试类 | 用例 | 覆盖点 |
|---|---|---|
| `user.repository.UserRepositoryDbTest` | 13 | 每条 SQL 都在真库跑一遍：insert 拿自增主键、`create_time` 由 MySQL 默认值填充、`class_id` 为 NULL 时读出来是 null（不是 0）、逻辑删除后查不到但行还在、`existsUsername` 与"被已删除用户占用"的区别、`existsClass` 排除已删班级、`countByRole` 排除已删用户、分页四个过滤条件 + `ORDER BY id DESC` + `LIMIT/OFFSET` + LEFT JOIN 班级名、`update` 把 `class_id` 写成 SQL NULL、`updatePassword` 只改密码、重复逻辑删除影响 0 行 |
| `user.service.UserServiceDbTest` | 16 | 真库 + 真 JdbcTemplate 的完整链路：内置 admin 的 BCrypt 哈希能登录、新增用户密码真加密且立刻能登录、禁用后登不上、账号重复在唯一索引下仍是 400、班级不存在被拒、教师 `class_id` 落 NULL、改姓名/重置密码真落库、**密码留空哈希一个字符都不变**、**短密码时资料一个字都没被改（回归）**、最后一个管理员护栏、分页真库查询（含删除的用户不再出现）、逻辑删除后行还在但查不到登不上、**禁用/删除后旧 token 立刻 401**（拦截器 + 真库联动）、token 里角色不作数 |

**关键设计：类上 `@Transactional`，每个用例跑完 Spring 自动回滚**，
所以在真库里造的测试用户/班级不会留下任何数据（实测跑完 `user` 表仍只有 admin、`class` 表仍为空）。
测试数据统一用 `it_<随机串>_` 前缀，不会碰你已有的数据。

### 第 3 层：端到端接口冒烟（需要先启动应用，真库 + 真 HTTP）

```powershell
cd D:\网络工程\AAA-Stu\实践2\HD-user
.\api-test.ps1
# 或指定参数
.\api-test.ps1 -BaseUrl http://localhost:8080 -AdminUser admin -AdminPassword 123456
```

`api-test.ps1` 是端到端自测脚本，共 **51 条断言**，覆盖：登录成功/失败、无 token 与非法 token、
`Authorization: Bearer` 与 `token:` 两种请求头、分页参数边界（current=0、size=500）、
新增用户与 4 类参数校验、学生的越权访问、提权防护、禁用后旧 token 立即失效、重置密码、
删除自己的护栏、逻辑删除后 token 失效、同名账号被已删除用户占用的坑。
全部通过退出码 0，有失败退出码 1（可直接贴到实训报告里）。

> **中文乱码的坑（已在脚本里修掉）**：Windows PowerShell 5.1 的 `Invoke-RestMethod` 在响应头没有
> `charset` 时会按 ISO-8859-1 解码，中文提示会变成 `è´¦å·æè¯¯`，导致"提示是否为中文"的断言误报失败。
> 后端返回的是标准 UTF-8（JSON 本身规定就是 UTF-8，Spring 6+ 不再往响应头塞 charset），浏览器/axios 不受影响。
> 脚本现在自己取原始字节按 UTF-8 解码，PowerShell 5.1 与 7 都正确。
> 另外：脚本文件是 **UTF-8 with BOM**，用编辑器改完请保留 BOM，否则 PowerShell 5.1 会把中文读成乱码直接报语法错误。

### 第 4 层：手工验证数据库落库结果

```sql
USE training_platform;

-- 1. 密码必须是 BCrypt 哈希（$2a$ 开头），绝对不能是明文
SELECT id, username, password, role, status, is_deleted FROM `user`;

-- 2. 逻辑删除后 is_deleted 变成 1（行还在，这是逻辑删除；注意别写成 NULL）
SELECT id, username, is_deleted, update_time FROM `user` WHERE is_deleted = 1;

-- 3. 逻辑删除不会释放 username 唯一索引（这是个已知坑）
SELECT username, COUNT(*) FROM `user` GROUP BY username HAVING COUNT(*) > 1;
```

### 第 5 层：前端联调前，用 Apifox/Postman 按《接口文档》再点一遍

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

## 7. 验证状态（本机 MySQL 已启动，全部实际跑过）

- ✅ **编译通过**：`mvn test` 编译 main 20 个类 + test 8 个类，Spring Boot 4.1.1 / Spring Framework 7.0.9 / Jackson 3.1.5 / Tomcat 11.0.24 / spring-security-crypto，无错误。
- ✅ **测试全绿（110 个用例）**：

  ```
  mvn -B -o test
  Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

  | 层 | 测试类 | 用例 |
  |---|---|---|
  | Mock 单测（不连库） | `UserServiceTest` 35、`UserApiContractTest` 16、`AuthInterceptorTest` 13、`JwtUtilTest` 8、`UserServicePageTest` 8 | 80 |
  | **真连 MySQL** | `UserServiceDbTest` 16、`UserRepositoryDbTest` 13 | 29 |
  | 容器冒烟（连库） | `Demo2ApplicationTests` | 1 |

- ✅ **端到端接口自测（真库 + 真 HTTP）**：启动 `demo2-0.0.1-SNAPSHOT.jar`（Tomcat 8080 + HikariPool 连上 MySQL）后执行 `api-test.ps1`：

  ```
  === 测试结果 ===
  全部通过：51 项
  ```

- ✅ **测试不污染真库（实测）**：真库测试用 `@Transactional` 自动回滚，跑完 `user` 表仍只有 `admin`、`class` 表仍为 0 行；
  `api-test.ps1` 会创建 1 个临时账号（`t_auto_<时间戳>`）并逻辑删除，脚本结束时会在末尾打印它的名字，可手工清掉。
- ⏳ 需要"退出登录/强制下线"功能的话要额外做（黑名单或 token 表），先确认是否算在本模块范围。

## 8. 本次收尾时改掉/加固的点（都有测试盯着）

| # | 问题 | 影响 | 处理 |
|---|---|---|---|
| 1 | `update()` 先写库、后校验新密码 | 传一个 3 位新密码：接口返回 400「密码不能少于 6 位」，但姓名/角色/班级/状态**已经写进库里了**（JdbcTemplate 自动提交，方法还没有事务）——典型的"报了错却改了数据" | 密码改为**写库前**校验并加密；`create/update/delete` 全部加 `@Transactional(rollbackFor = Exception.class)`；真库回归用例 `UserServiceDbTest#shortPasswordDoesNotTouchDatabase` |
| 2 | 教师/管理员也能挂 `class_id` | 与设计说明「教师/管理员 class_id 为 NULL」矛盾，带班关系会出现两份数据 | 新增/修改一律把非学生的 `class_id` 置 NULL（带班关系由 `class.teacher_id` 表示） |
| 3 | 修改接口对 `username` 静默忽略 | 前端"编辑用户"表单改了账号，提示保存成功但库里没变 | 传了不同账号直接 400「登录账号不允许修改」；原样回填相同账号仍然允许（前端表单不用改） |
| 4 | 分页只能按账号/班级筛 | 管理员用户列表页没法"只看学生/只看禁用账号" | `/api/user/page` 增加可选参数 `role`、`status`（非法值直接 400，不让前端查出一片空白） |
| 5 | 请求体不是合法 JSON / 参数类型写错走 500 | 前端拼错参数时提示"服务器异常"，排查方向被带偏 | 新增两个异常处理：非法 JSON → 400「请求体格式错误」，`?classId=abc` → 400「参数类型错误：classId」 |
| 6 | `UserRepository.update()` 用无类型 null 传 `class_id` | 不同 MySQL 驱动对"无类型 null"处理有差异 | 改成显式 `setNull(3, Types.BIGINT)`，和 `insert()` 的写法保持一致；真库用例验证库里确实是 SQL NULL |
| 7 | `api-test.ps1` 在 Windows PowerShell 5.1 下中文乱码 | 中文提示/姓名的断言误报失败（本次实测 51 项里挂了 2 项），其实是客户端解码问题 | 脚本改成自己取原始字节按 UTF-8 解码，PS 5.1 与 7 都正确；并把脚本另存为 UTF-8 with BOM（PS 5.1 读 .ps1 靠 BOM 识别编码） |

> 有意**没做**的一件事：**没有**强制"学生必须有班级"。班级模块（`/api/class`）还没合入，
> 强制要求会让 `api-test.ps1` 和联调流程建不出学生。目前是"传了就必须真实存在、不传允许 NULL"，
> 建议前端在"新增学生"表单里让管理员选班级，等班级模块合入后再评估是否改成强制。
