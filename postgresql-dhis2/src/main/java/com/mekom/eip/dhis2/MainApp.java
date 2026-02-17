package com.mekom.eip.dhis2;

import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Main application entry point for the PostgreSQL to DHIS2 integration.
 * 
 * Note: This class is optional when using the EIP client Docker container,
 * which automatically discovers and loads RouteBuilder classes.
 */
public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private static org.apache.camel.main.Main camelMain;

    public static void main(String[] args) throws Exception {
        log.info("Starting PostgreSQL to DHIS2 EIP Routes Application");

        // Load properties from application.properties
        loadProperties();

        // Create Camel Main
        camelMain = new org.apache.camel.main.Main();
        camelMain.configure().addRoutesBuilder(new PostgresqlDhis2Route());

        // Register DataSource - routes will handle this automatically, but we can pre-register
        // The route's configure() method will also register it if needed

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down application...");
            if (camelMain != null) {
                try {
                    camelMain.stop();
                } catch (Exception e) {
                    log.error("Error stopping Camel context", e);
                }
            }
        }));

        // Start Camel
        camelMain.run(args);
    }

    /**
     * Loads properties from application.properties and sets them as system properties.
     */
    private static void loadProperties() {
        try (InputStream input = MainApp.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                log.warn("application.properties not found, using system properties only");
                return;
            }

            Properties properties = new Properties();
            properties.load(input);

            // Set properties as system properties if not already set
            for (String key : properties.stringPropertyNames()) {
                if (System.getProperty(key) == null) {
                    System.setProperty(key, properties.getProperty(key));
                }
            }

            log.info("Loaded {} properties from application.properties", properties.size());
        } catch (Exception e) {
            log.error("Error loading application.properties", e);
        }
    }
}
