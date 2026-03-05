package com.ozonehis.eip.dhis2;

import org.apache.camel.CamelContext;
import org.apache.camel.component.sql.SqlComponent;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Configuration class for PostgreSQL DataSource.
 * Uses PostgreSQL's simple datasource to avoid HikariCP version conflicts with EIP client runtime.
 */
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    /**
     * Registers the DataSource with Camel context.
     * Uses Camel's property resolver to read from Spring Boot properties/environment variables.
     */
    public static void registerDataSource(CamelContext camelContext) {
        // Use Camel's property resolver to get values (supports Spring Boot properties)
        String jdbcUrl = camelContext.resolvePropertyPlaceholders("{{eip.postgresql.jdbc.url:jdbc:postgresql://postgresql:5432/analytics}}");
        String username = camelContext.resolvePropertyPlaceholders("{{eip.postgresql.username:postgres}}");
        String password = camelContext.resolvePropertyPlaceholders("{{eip.postgresql.password:postgres}}");

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        // Parse JDBC URL to extract host, port, database
        // Format: jdbc:postgresql://host:port/database
        String urlWithoutPrefix = jdbcUrl.replace("jdbc:postgresql://", "");
        String[] parts = urlWithoutPrefix.split("/");
        if (parts.length >= 2) {
            String[] hostPort = parts[0].split(":");
            dataSource.setServerNames(new String[]{hostPort[0]});
            if (hostPort.length > 1) {
                dataSource.setPortNumbers(new int[]{Integer.parseInt(hostPort[1])});
            }
            dataSource.setDatabaseName(parts[1]);
        } else {
            log.warn("Unable to parse JDBC URL: {}, using defaults", jdbcUrl);
            dataSource.setServerNames(new String[]{"postgresql"});
            dataSource.setDatabaseName("analytics");
        }
        
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setConnectTimeout(30); // 30 seconds

        log.info("Creating PostgreSQL DataSource with URL: {}", jdbcUrl);
        
        camelContext.getRegistry().bind("postgresqlDataSource", dataSource);
        
        // Configure SQL component
        SqlComponent sqlComponent = camelContext.getComponent("sql", SqlComponent.class);
        if (sqlComponent != null) {
            sqlComponent.setDataSource(dataSource);
        }
        
        log.info("PostgreSQL DataSource registered with Camel context");
    }
}

