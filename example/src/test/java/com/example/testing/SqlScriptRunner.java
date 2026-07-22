package com.example.testing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;

@Slf4j
public class SqlScriptRunner {

    private final DataSource dataSource;

    public SqlScriptRunner(String url, String username, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        this.dataSource = ds;
    }

    public void run(Path sqlFile) throws Exception {
        log.debug("Running SQL script: {}", sqlFile);
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new FileSystemResource(sqlFile));
        }
    }
}
