package com.mekom.eip.dhis2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and caches DHIS2 mapping configuration from YAML file.
 * Similar to how OpenMRS module loads ReportTemplates.
 */
public class ConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationLoader.class);
    private static final String CONFIG_FILE = "dhis2-mappings.yml";
    private static final String ENV_CONFIG_PATH = "EIP_DHIS2_MAPPING_FILE";
    private static final String SYS_CONFIG_PATH = "eip.dhis2.mapping.file";
    
    private static Dhis2MappingConfig config;
    private static Map<String, Dhis2MappingConfig.ReportMapping> reportCache = new HashMap<>();

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Loads the configuration file from classpath.
     */
    public static synchronized Dhis2MappingConfig loadConfiguration() {
        if (config != null) {
            return config;
        }

        try (InputStream inputStream = openConfigStream()) {
            if (inputStream == null) {
                log.error("DHIS2 mapping configuration not found. Set {} (env) or {} (system property) or place {} on the classpath.",
                        ENV_CONFIG_PATH, SYS_CONFIG_PATH, CONFIG_FILE);
                return null;
            }

            Yaml yaml = new Yaml();
            config = yaml.loadAs(inputStream, Dhis2MappingConfig.class);

            // Resolve ${...} placeholders against system properties and environment variables
            resolvePlaceholders(config);

            if (config != null && config.getReports() != null) {
                for (Dhis2MappingConfig.ReportMapping report : config.getReports()) {
                    reportCache.put(report.getId(), report);
                    log.info("Loaded report mapping: {} - {}", report.getId(), report.getName());
                }
            }

            log.info("Successfully loaded {} report mappings", reportCache.size());
            return config;

        } catch (Exception e) {
            log.error("Error loading configuration file: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Recursively resolve ${...} placeholders in loaded configuration using system properties first, then env vars.
     */
    private static void resolvePlaceholders(Object node) {
        if (node == null) {
            return;
        }

        if (node instanceof Map<?, ?>) {
            ((Map<?, ?>) node).forEach((k, v) -> resolvePlaceholders(v));
        } else if (node instanceof List<?>) {
            ((List<?>) node).forEach(ConfigurationLoader::resolvePlaceholders);
        } else if (node instanceof Dhis2MappingConfig.ReportMapping) {
            Dhis2MappingConfig.ReportMapping r = (Dhis2MappingConfig.ReportMapping) node;
            r.setId(resolveString(r.getId()));
            r.setName(resolveString(r.getName()));
            r.setDescription(resolveString(r.getDescription()));
            r.setDataSet(resolveString(r.getDataSet()));
            r.setSql(resolveString(r.getSql()));
            resolvePlaceholders(r.getGroupBy());
            resolvePlaceholders(r.getDataValueMappings());
        } else if (node instanceof Dhis2MappingConfig.GroupByConfig) {
            Dhis2MappingConfig.GroupByConfig g = (Dhis2MappingConfig.GroupByConfig) node;
            g.setDataSet(resolveString(g.getDataSet()));
            g.setPeriod(resolveString(g.getPeriod()));
            g.setOrgUnit(resolveString(g.getOrgUnit()));
        } else if (node instanceof Dhis2MappingConfig.DataValueMapping) {
            Dhis2MappingConfig.DataValueMapping d = (Dhis2MappingConfig.DataValueMapping) node;
            d.setName(resolveString(d.getName()));
            d.setDataElement(resolveString(d.getDataElement()));
            d.setValueColumn(resolveString(d.getValueColumn()));
            d.setCategoryOptionCombo(resolveString(d.getCategoryOptionCombo()));
            d.setFilter(resolveString(d.getFilter()));
        }
    }

    /**
     * Resolve placeholders within a single string using system properties then environment variables.
     */
    private static String resolveString(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = System.getProperty(key);
            if (replacement == null) {
                replacement = System.getenv(key);
            }
            if (replacement == null) {
                replacement = matcher.group(0); // keep original if not found
                log.warn("No value found for placeholder {}", key);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static InputStream openConfigStream() {
        String externalPath = resolveExternalPath();
        if (externalPath != null) {
            try {
                Path path = Paths.get(externalPath);
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    log.info("Loading DHIS2 mapping config from external file: {}", path.toAbsolutePath());
                    return Files.newInputStream(path);
                }
                log.warn("External config path {} not found or not a file. Falling back to classpath resource {}.",
                        externalPath, CONFIG_FILE);
            } catch (Exception e) {
                log.warn("Failed to read external config {}: {}. Falling back to classpath resource {}.",
                        externalPath, e.getMessage(), CONFIG_FILE);
            }
        }

        InputStream classpathStream = ConfigurationLoader.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE);

        if (classpathStream != null) {
            log.info("Loading DHIS2 mapping config from classpath resource {}", CONFIG_FILE);
        }
        return classpathStream;
    }

    private static String resolveExternalPath() {
        String path = System.getProperty(SYS_CONFIG_PATH);
        if (path == null || path.trim().isEmpty()) {
            path = System.getenv(ENV_CONFIG_PATH);
        }
        return (path != null && !path.trim().isEmpty()) ? path.trim() : null;
    }

    /**
     * Gets a report mapping by ID.
     */
    public static Dhis2MappingConfig.ReportMapping getReportMapping(String reportId) {
        if (config == null) {
            loadConfiguration();
        }
        return reportCache.get(reportId);
    }

    /**
     * Gets the first report mapping (for single-report scenarios).
     */
    public static Dhis2MappingConfig.ReportMapping getFirstReportMapping() {
        if (config == null) {
            loadConfiguration();
        }
        if (config != null && config.getReports() != null && !config.getReports().isEmpty()) {
            return config.getReports().get(0);
        }
        return null;
    }
}
