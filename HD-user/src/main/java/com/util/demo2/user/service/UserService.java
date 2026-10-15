package com.util.demo2.user.service;

import com.util.demo2.auth.JwtUtil;
import com.util.demo2.auth.LoginUser;
import com.util.demo2.common.BusinessException;
import com.util.demo2.common.PageResult;
import com.util.demo2.common.Result;
import com.util.demo2.user.dto.LoginRequest;
import com.util.demo2.user.dto.UserSaveRequest;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import com.util.demo2.user.vo.LoginVO;
import com.util.demo2.user.vo.UserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户模块业务逻辑：登录、分页查询、详情、新增、修改、逻辑删除。
 */
@Service
public class UserService {

    private static final Set<String> ROLES =
            Set.of(LoginUser.ROLE_STUDENT, LoginUser.ROLE_TEACHER, LoginUser.ROLE_ADMIN);
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final long PAGE_SIZE_MAX = 100;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /** 登录：账号密码 + 启用状态校验，通过后签发 token */
    public LoginVO login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        // 账号不存在和密码错误返回同一句提示，避免被人枚举出系统里有哪些账号
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(Result.CODE_UNAUTHORIZED, "账号或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setClassId(user.getClassId());
        vo.setToken(jwtUtil.generate(user.getId()));
        return vo;
    }

    /** 分页查询（管理员） */
    public PageResult<UserVO> page(long current, long size, String username, String role, Long classId, Integer status) {
        long safeCurrent = current < 1 ? 1 : current;
        long safeSize = size < 1 ? 10 : Math.min(size, PAGE_SIZE_MAX);
        // 过滤条件先校验再查库：传了非法值直接 400，避免前端看到"查得到 0 条"却不知道为什么
        String safeRole = trimToNull(role);
        if (safeRole != null && !ROLES.contains(safeRole)) {
            throw new BusinessException("角色只能是 student / teacher / admin");
        }
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("状态只能是 0（禁用）或 1（启用）");
        }
        long total = userRepository.count(username, safeRole, classId, status);
        List<UserVO> records = total == 0
                ? List.of()
                : userRepository.page(username, safeRole, classId, status, (safeCurrent - 1) * safeSize, safeSize);
        return PageResult.of(records, total, safeCurrent, safeSize);
    }

    /** 查询单个用户：本人或管理员 */
    public UserVO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在或已被删除"));
        LoginUser current = LoginUser.get();
        boolean self = current != null && Objects.equals(current.getId(), id);
        if (!self && (current == null || !current.isAdmin())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "只能查看自己的信息");
        }
        return UserVO.from(user);
    }

    /** 新增用户（管理员），密码 BCrypt 加密后入库，返回新用户 id */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveRequest request) {
        String username = trimToNull(request.getUsername());
        if (username == null) {
            throw new BusinessException("账号不能为空");
        }
        if (username.length() > 50) {
            throw new BusinessException("账号长度不能超过 50");
        }
        if (request.getPassword() == null || request.getPassword().length() < PASSWORD_MIN_LENGTH) {
            throw new BusinessException("密码不能少于 " + PASSWORD_MIN_LENGTH + " 位");
        }
        String realName = trimToNull(request.getRealName());
        if (realName == null) {
            throw new BusinessException("真实姓名不能为空");
        }
        if (realName.length() > 50) {
            throw new BusinessException("真实姓名长度不能超过 50");
        }
        String role = validateRole(request.getRole());
        // 说明：classId 对 student 是"推荐必填"而不是"强制必填"——班级模块（/api/class）还没合入时，
        // 管理员需要先把学生建出来再分班，强制要求会卡住联调（api-test.ps1 也是先建学生、后补班级）。
        // 前端在"新增学生"表单里应当让管理员选班级，后端只校验"传了就必须真实存在"。
        if (request.getClassId() != null && !userRepository.existsClass(request.getClassId())) {
            throw new BusinessException("班级不存在：classId = " + request.getClassId());
        }
        if (userRepository.existsUsername(username)) {
            throw new BusinessException("账号已存在：" + username);
        }
        if (userRepository.existsUsernameIgnoreDeleted(username)) {
            throw new BusinessException("账号名被一个已删除的用户占用（逻辑删除不会释放唯一索引），请换一个账号名或先恢复该用户");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(realName);
        user.setRole(role);
        // 教师/管理员不挂班级：班主任关系由 class.teacher_id 表示，避免两处数据打架
        user.setClassId(LoginUser.ROLE_STUDENT.equals(role) ? request.getClassId() : null);
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        try {
            return userRepository.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下两个管理员同时新增同一账号时兜底
            throw new BusinessException("账号已存在：" + username);
        }
    }

    /**
     * 修改用户。
     * 管理员：可改姓名、角色、班级、状态，可重置密码。
     * 其他角色：只能改自己的姓名和密码，且明确拒绝修改角色/状态/班级的请求（防止提权）。
     * <p>
     * 两个容易踩的坑（都有单测盯着）：
     * <ol>
     *   <li>新密码必须在<b>写库之前</b>校验完，否则会出现"接口返回 400 密码太短、但姓名/角色已经被改掉"的脏数据；</li>
     *   <li>方法加事务，"改资料"和"改密码"两条 UPDATE 要么都成功，要么都回滚。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest request) {
        User exist = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在或已被删除"));
        LoginUser current = LoginUser.get();
        if (current == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "未登录");
        }
        boolean admin = current.isAdmin();
        boolean self = Objects.equals(current.getId(), id);
        if (!admin && !self) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "只能修改自己的信息");
        }

        // 登录账号不允许修改：它是登录凭据，改了会让日志、成果提交记录里的引用对不上。
        // 前端编辑表单会原样回填 username，所以只有"确实传了不同的账号"才报错，不做静默忽略
        // （静默忽略会让用户以为改成功了，是最难查的一类问题）。
        String newUsername = trimToNull(request.getUsername());
        if (newUsername != null && !newUsername.equals(exist.getUsername())) {
            throw new BusinessException("登录账号不允许修改，如确需更换请新增账号后删除旧账号");
        }

        String role = exist.getRole();
        Long classId = exist.getClassId();
        Integer status = exist.getStatus();

        if (admin) {
            if (request.getRole() != null) {
                String newRole = validateRole(request.getRole());
                if (!newRole.equals(exist.getRole())
                        && LoginUser.ROLE_ADMIN.equals(exist.getRole())
                        && userRepository.countByRole(LoginUser.ROLE_ADMIN) <= 1) {
                    throw new BusinessException("系统至少保留一个管理员，不能修改最后一个管理员的角色");
                }
                role = newRole;
            }
            if (request.getClassId() != null) {
                if (!userRepository.existsClass(request.getClassId())) {
                    throw new BusinessException("班级不存在：classId = " + request.getClassId());
                }
                classId = request.getClassId();
            }
            if (request.getStatus() != null) {
                if (request.getStatus() != 0 && request.getStatus() != 1) {
                    throw new BusinessException("状态只能是 0（禁用）或 1（启用）");
                }
                if (self && request.getStatus() == 0) {
                    throw new BusinessException("不能禁用自己的账号");
                }
                status = request.getStatus();
            }
        } else {
            if (request.getRole() != null && !request.getRole().equals(exist.getRole())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权修改角色");
            }
            if (request.getStatus() != null && !request.getStatus().equals(exist.getStatus())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权修改账号状态");
            }
            if (request.getClassId() != null && !request.getClassId().equals(exist.getClassId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权修改所属班级");
            }
        }

        String realName = trimToNull(request.getRealName());
        if (realName == null) {
            realName = exist.getRealName();
        }
        if (realName != null && realName.length() > 50) {
            throw new BusinessException("真实姓名长度不能超过 50");
        }

        // 新密码：先校验、先加密，再动数据库。
        // 原来是在 userRepository.update() 之后才校验，密码过短时接口返回 400，
        // 但姓名/角色/班级/状态其实已经写进库里了（JdbcTemplate 自动提交，方法没有事务）——属于"报了错却改了数据"。
        String encodedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < PASSWORD_MIN_LENGTH) {
                throw new BusinessException("密码不能少于 " + PASSWORD_MIN_LENGTH + " 位");
            }
            encodedPassword = passwordEncoder.encode(request.getPassword());
        }

        // 角色与班级必须一致的一点：教师/管理员不挂班级（带班关系由 class.teacher_id 表示），
        // 否则同一件事会有两份数据、迟早打架。学生允许暂时不带班级（等班级模块建好后再分班）。
        if (!LoginUser.ROLE_STUDENT.equals(role)) {
            classId = null;
        }

        User update = new User();
        update.setId(id);
        update.setRealName(realName);
        update.setRole(role);
        update.setClassId(classId);
        update.setStatus(status);
        userRepository.update(update);

        // 密码留空表示不改密码
        if (encodedPassword != null) {
            userRepository.updatePassword(id, encodedPassword);
        }
    }

    /** 逻辑删除（管理员）：不能删自己，也不能删掉最后一个管理员 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        User exist = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在或已被删除"));
        LoginUser current = LoginUser.get();
        if (current != null && Objects.equals(current.getId(), id)) {
            throw new BusinessException("不能删除当前登录的账号");
        }
        if (LoginUser.ROLE_ADMIN.equals(exist.getRole())
                && userRepository.countByRole(LoginUser.ROLE_ADMIN) <= 1) {
            throw new BusinessException("系统至少保留一个管理员，不能删除最后一个管理员");
        }
        userRepository.logicDelete(id);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private static String validateRole(String role) {
        String value = trimToNull(role);
        if (value == null) {
            throw new BusinessException("角色不能为空");
        }
        if (!ROLES.contains(value)) {
            throw new BusinessException("角色只能是 student / teacher / admin");
        }
        return value;
    }
}
