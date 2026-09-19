package com.finance.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 在接受业务操作前验证登录所需的数据库连接和字段，不读取用户记录。 */
@Component
public class DatabaseStartupCheck implements ApplicationRunner {
    private final DataSource dataSource;

    public DatabaseStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT id, username, password, token_version, nickname, avatar, language, theme, "
                    + "create_time, update_time, deleted FROM `user` WHERE 1 = 0").close();
        } catch (SQLException e) {
            // 仅输出诊断代码，避免驱动异常把连接参数或凭据写入此异常信息。
            throw new IllegalStateException("Database startup check failed. Check MySQL, DB_PASSWORD "
                    + "and the user table schema. Local config: "
                    + System.getProperty("user.home") + "/.personal-finance/application.properties"
                    + " (SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + ")");
        }
    }
}
