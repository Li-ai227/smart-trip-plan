package com.smart.trip.server.auth.service;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.User;
import com.smart.trip.server.auth.model.UserRole;
import com.smart.trip.server.auth.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户注册与认证服务（C-011 AC-1/AC-4）。
 *
 * <p>BCrypt 哈希存储，响应/日志不返回密码明文或哈希值。
 * 登录失败不区分"用户不存在"与"密码错误"（防枚举攻击）。</p>
 */
public class UserService {

    /** 密码最小长度。 */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** 登录失败统一提示（防枚举）。 */
    public static final String AUTH_FAILURE_MESSAGE = "用户名或密码错误";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造用户服务（默认 BCrypt）。
     *
     * @param userRepository 用户数据访问层
     */
    public UserService(UserRepository userRepository) {
        this(userRepository, new BCryptPasswordEncoder());
    }

    /**
     * 构造用户服务（可注入自定义编码器，便于测试）。
     *
     * @param userRepository  用户数据访问层
     * @param passwordEncoder 密码编码器
     */
    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户。
     *
     * @param username 用户名，非空白
     * @param password 密码，长度 ≥ 8
     * @param nickname 昵称
     * @return 新建的用户实体
     * @throws BaseException INVALID_REQUEST（参数非法）或 CONFLICT（用户名重复）
     */
    public User register(String username, String password, String nickname) {
        if (username == null || username.isBlank()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "username 不可为空");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                    "密码长度不得少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        String hash = passwordEncoder.encode(password);
        String effectiveNickname = (nickname == null || nickname.isBlank()) ? username.trim() : nickname;
        long id = userRepository.save(username.trim(), hash, effectiveNickname, UserRole.USER);
        if (id < 0) {
            throw new BaseException(ErrorCode.CONFLICT, "用户名已存在: " + username);
        }
        return userRepository.findByUsername(username.trim()).orElseThrow();
    }

    /**
     * 认证用户（登录）。
     *
     * @param username 用户名
     * @param password 密码
     * @return 认证通过的用户实体
     * @throws BaseException UNAUTHORIZED（用户名或密码错误，不区分——防枚举）
     */
    public User authenticate(String username, String password) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty() || !passwordEncoder.matches(password, opt.get().passwordHash())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, AUTH_FAILURE_MESSAGE);
        }
        return opt.get();
    }

    /**
     * 按用户名查找用户。
     *
     * @param username 用户名
     * @return 用户，或 empty
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
