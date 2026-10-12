package com.smart.trip.server.auth.repository;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.User;
import com.smart.trip.server.auth.model.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * 用户数据访问层（C-011 AC-5，plain JDBC，不引入 JPA）。
 *
 * <p>仅负责数据存取，不包含业务逻辑。</p>
 */
public class UserRepository {

    private static final String INSERT_USER =
            "INSERT INTO trip_user (username, password_hash, nickname, role, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_USERNAME =
            "SELECT id, username, password_hash, nickname, role, created_at, updated_at "
                    + "FROM trip_user WHERE username = ?";

    private final DataSource dataSource;

    /**
     * 构造数据访问层，注入数据源。
     *
     * @param dataSource JDBC 数据源，非 null
     */
    public UserRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不可为 null");
        }
        this.dataSource = dataSource;
    }

    /**
     * 按用户名查询用户，不存在返回空。
     *
     * @param username 用户名
     * @return 匹配的用户，或 empty
     */
    public Optional<User> findByUsername(String username) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "查询用户失败: username=" + username, ex);
        }
    }

    /**
     * 保存新用户，返回自增 ID；用户名重复时返回 -1。
     *
     * @param username     用户名
     * @param passwordHash BCrypt 哈希
     * @param nickname     昵称
     * @param role         角色
     * @return 自增 ID，或 -1 表示重复
     */
    public long save(String username, String passwordHash, String nickname, UserRole role) {
        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, nickname);
            ps.setString(4, role.name());
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("未获取到自增 ID");
            }
        } catch (SQLException ex) {
            if (isDuplicateKeyError(ex)) {
                return -1;
            }
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "保存用户失败: username=" + username, ex);
        }
    }

    private static User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                UserRole.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static boolean isDuplicateKeyError(SQLException ex) {
        return ex.getErrorCode() == 1062
                || (ex.getMessage() != null && ex.getMessage().contains("Duplicate"));
    }
}
