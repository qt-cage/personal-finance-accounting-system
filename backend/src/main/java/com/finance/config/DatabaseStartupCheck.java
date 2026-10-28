package com.finance.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/** 只读检查应用所需的表列和唯一约束；迁移由部署流程显式执行。 */
@Component
public class DatabaseStartupCheck implements ApplicationRunner {
    private final DataSource dataSource;

    public DatabaseStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String[][] SCHEMA = {
        {"user", "id,username,password,token_version,role,status,nickname,avatar,language,theme,create_time,update_time,deleted", "id;username"},
        {"category", "id,user_id,parent_id,name,type,icon,sort_order,create_time,update_time,deleted,active_key", "id;user_id,name,type,active_key"},
        {"bill", "id,user_id,category_id,account_id,transfer_account_id,request_id,amount,type,remark,bill_date,create_time,update_time,deleted", "id;user_id,request_id"},
        {"finance_account", "id,user_id,name,account_type,opening_balance,opening_date,create_time,update_time,deleted,active_key", "id;user_id,name,active_key"},
        {"budget", "id,user_id,category_id,amount,month,create_time,update_time,deleted,category_key,active_key", "id;user_id,month,category_key,active_key"},
        {"recurring_bill", "id,user_id,name,type,category_id,account_id,amount,remark,frequency,start_date,end_date,next_date,enabled,last_error,create_time,update_time,deleted", "id"},
        {"recurring_run", "id,template_id,due_date,bill_id,create_time", "id;template_id,due_date"},
        {"bill_attachment", "id,user_id,bill_id,filename,storage_name,media_type,size,create_time,deleted", "id"},
        {"notification_preference", "user_id,enabled", "user_id"},
        {"notification", "id,user_id,budget_id,month,stage,content,read_flag,create_time", "id;user_id,budget_id,month,stage"},
        {"system_dictionary", "id,kind,code,name,enabled,sort_order", "id;kind,code"},
        {"system_setting", "name,value", "name"},
        {"operation_log", "id,actor_id,method,route,outcome,create_time", "id"},
        {"data_revision", "user_id,revision", "user_id"}
    };

    @Override
    public void run(ApplicationArguments args) {
        String checking = "connection";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String[] table : SCHEMA) {
                checking = table[0] + " columns";
                String columns = Arrays.stream(table[1].split(",")).map(column -> "`" + column + "`").collect(Collectors.joining(","));
                statement.executeQuery("SELECT " + columns + " FROM `" + table[0] + "` WHERE 1 = 0").close();
            }
            for (String[] table : SCHEMA) {
                checking = table[0] + " unique constraints";
                Map<String, SortedMap<Integer, String>> indexes = new HashMap<>();
                try (ResultSet rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table[0], true, false)) {
                    while (rows.next()) {
                        String column = rows.getString("COLUMN_NAME"), name = rows.getString("INDEX_NAME");
                        if (column != null && name != null && !rows.getBoolean("NON_UNIQUE")) {
                            indexes.computeIfAbsent(name, key -> new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
                        }
                    }
                }
                Set<String> actual = indexes.values().stream().map(index -> String.join(",", index.values())).collect(Collectors.toSet());
                for (String expected : table[2].split(";")) {
                    if (!actual.contains(expected)) {
                        throw new IllegalStateException("Database schema incompatible: missing unique constraint " + table[0] + "(" + expected + "). Apply reviewed migrations before starting.");
                    }
                }
            }
        } catch (SQLException e) {
            // 仅输出诊断代码，避免驱动异常把连接参数或凭据写入此异常信息。
            throw new IllegalStateException("Database startup check failed at " + checking + ". Check MySQL, DB_PASSWORD "
                    + "and reviewed schema migrations. Local config: "
                    + System.getProperty("user.home") + "/.personal-finance/application.properties"
                    + " (SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + ")");
        }
    }
}
