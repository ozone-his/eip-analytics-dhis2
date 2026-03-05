package com.ozonehis.eip.dhis2;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Aggregates individual data rows into DataValueSet objects grouped by dataSet, period, and orgUnit.
 * 
 * This follows the pattern from OpenMRS DHIS2 Reporting Module where data is grouped
 * before sending to DHIS2 for efficiency.
 * 
 * Uses configuration file (dhis2-mappings.yml) to map columns to DHIS2 data elements,
 * similar to how OpenMRS module uses DataValueTemplate mappings.
 */
public class DataValueSetAggregator implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DataValueSetAggregator.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Dhis2MappingConfig.ReportMapping reportMapping = exchange.getProperty(
                "DHIS2_REPORT_MAPPING",
                Dhis2MappingConfig.ReportMapping.class);

        if (reportMapping == null) {
            reportMapping = ConfigurationLoader.getFirstReportMapping();
        }

        if (reportMapping == null) {
            log.warn("No report mapping configuration found. Will use default column mapping.");
        } else {
            log.debug("Aggregating using report mapping: {} - {}", reportMapping.getId(), reportMapping.getName());
        }

        Object body = exchange.getIn().getBody();
        
        if (body == null) {
            log.warn("Received null body, skipping aggregation");
            return;
        }

        // Body should be a List of Maps (from SQL query results)
        List<Map<String, Object>> rows;
        if (body instanceof List) {
            rows = (List<Map<String, Object>>) body;
        } else if (body instanceof Map) {
            // Single row - wrap in list
            rows = Collections.singletonList((Map<String, Object>) body);
        } else {
            log.warn("Body is not a List or Map, cannot aggregate");
            return;
        }

        if (rows.isEmpty()) {
            log.info("No rows to aggregate");
            exchange.getIn().setBody(Collections.emptyList());
            return;
        }

        log.info("Aggregating {} rows into DataValueSets", rows.size());

        // Use configuration if available, otherwise fall back to default mapping
        if (reportMapping != null) {
            aggregateUsingConfiguration(rows, exchange, reportMapping);
        } else {
            aggregateUsingDefaultMapping(rows, exchange);
        }
    }

    /**
     * Aggregates rows using configuration file mapping (OpenMRS pattern).
     */
        private void aggregateUsingConfiguration(
            List<Map<String, Object>> rows,
            Exchange exchange,
            Dhis2MappingConfig.ReportMapping reportMapping) {
        
        Dhis2MappingConfig.GroupByConfig groupBy = reportMapping.getGroupBy();
        List<Dhis2MappingConfig.DataValueMapping> mappings = reportMapping.getDataValueMappings();
        
        if (groupBy == null || mappings == null || mappings.isEmpty()) {
            log.warn("Invalid configuration, falling back to default mapping");
            aggregateUsingDefaultMapping(rows, exchange);
            return;
        }

        // Group rows by (dataSet, period, orgUnit)
        Map<String, List<Map<String, Object>>> groupedData = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            // Extract grouping keys from configuration
            String dataSet = getGroupingValue(row, groupBy.getDataSet());
            String period = getGroupingValue(row, groupBy.getPeriod());
            String orgUnit = getGroupingValue(row, groupBy.getOrgUnit());

            if (dataSet == null || period == null || orgUnit == null) {
                log.warn("Skipping row with missing grouping keys: {}", row);
                continue;
            }

            String groupKey = dataSet + "|" + period + "|" + orgUnit;
            groupedData.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        log.info("Grouped into {} DataValueSets", groupedData.size());

        // Convert each group into a DataValueSet using configuration mappings
        List<Map<String, Object>> dataValueSets = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedData.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            String dataSet = keys[0];
            String period = keys[1];
            String orgUnit = keys[2];
            List<Map<String, Object>> groupRows = entry.getValue();

            // Build DataValueSet for this group
            Map<String, Object> dataValueSet = new HashMap<>();
            dataValueSet.put("dataSet", dataSet);
            dataValueSet.put("period", period);
            dataValueSet.put("orgUnit", orgUnit);
            // Note: do NOT include completeDate – sending it triggers a dataset-completion
            // registration in DHIS2, which fails with 409 when only a subset of the
            // dataset's data elements are being submitted.

            // Build dataValues list using configuration mappings
            List<Map<String, Object>> dataValues = new ArrayList<>();
            
            // For each mapping, create dataValues from all rows in the group
            for (Dhis2MappingConfig.DataValueMapping mapping : mappings) {
                for (Map<String, Object> row : groupRows) {
                    // Apply filter if present
                    if (mapping.getFilter() != null && !mapping.getFilter().trim().isEmpty()) {
                        if (!evaluateFilter(row, mapping.getFilter())) {
                            continue;
                        }
                    }

                    // Get value from configured column
                    Object valueObj = getValueFromRow(row, mapping.getValueColumn());
                    if (valueObj == null) {
                        continue;
                    }

                    String value = valueObj.toString().trim();
                    if (value.isEmpty()) {
                        continue;
                    }

                    // Create dataValue
                    Map<String, Object> dataValue = new HashMap<>();
                    dataValue.put("dataElement", mapping.getDataElement());
                    dataValue.put("value", value);
                    
                    // Handle categoryOptionCombo (can be column or constant)
                    String categoryOptionCombo = mapping.getCategoryOptionCombo();
                    if (categoryOptionCombo != null && !categoryOptionCombo.trim().isEmpty()) {
                        // Check if it's a column name or constant
                        Object cocValue = getValueFromRow(row, categoryOptionCombo);
                        if (cocValue != null && !cocValue.toString().trim().isEmpty() 
                                && !"default".equalsIgnoreCase(cocValue.toString().trim())) {
                            dataValue.put("categoryOptionCombo", cocValue.toString().trim());
                        } else if (!"default".equalsIgnoreCase(categoryOptionCombo.trim())) {
                            // It's a constant value
                            dataValue.put("categoryOptionCombo", categoryOptionCombo.trim());
                        }
                    }

                    dataValues.add(dataValue);
                }
            }

            if (!dataValues.isEmpty()) {
                dataValueSet.put("dataValues", dataValues);
                dataValueSets.add(dataValueSet);
                log.debug("Created DataValueSet: dataSet={}, period={}, orgUnit={}, dataValues={}", 
                         dataSet, period, orgUnit, dataValues.size());
            }
        }

        log.info("Created {} DataValueSets from {} rows", dataValueSets.size(), rows.size());
        exchange.getIn().setBody(dataValueSets);
    }

    /**
     * Aggregates rows using default column mapping (backward compatibility).
     */
    private void aggregateUsingDefaultMapping(
            List<Map<String, Object>> rows, 
            Exchange exchange) {
        
        // Group rows by (dataSet, period, orgUnit)
        Map<String, List<Map<String, Object>>> groupedData = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            // Extract grouping keys
            String dataSet = getStringValue(row, "dataSet", "dataset");
            String period = getStringValue(row, "period", "period");
            String orgUnit = getStringValue(row, "orgUnit", "orgunit", "org_unit");

            if (dataSet == null || period == null || orgUnit == null) {
                log.warn("Skipping row with missing grouping keys: {}", row);
                continue;
            }

            String groupKey = dataSet + "|" + period + "|" + orgUnit;
            groupedData.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        log.info("Grouped into {} DataValueSets", groupedData.size());

        // Convert each group into a DataValueSet
        List<Map<String, Object>> dataValueSets = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedData.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            String dataSet = keys[0];
            String period = keys[1];
            String orgUnit = keys[2];
            List<Map<String, Object>> groupRows = entry.getValue();

            // Build DataValueSet for this group
            Map<String, Object> dataValueSet = new HashMap<>();
            dataValueSet.put("dataSet", dataSet);
            dataValueSet.put("period", period);
            dataValueSet.put("orgUnit", orgUnit);
            // completeDate omitted intentionally - see aggregateUsingConfiguration comment

            // Build dataValues list
            List<Map<String, Object>> dataValues = new ArrayList<>();
            for (Map<String, Object> row : groupRows) {
                Map<String, Object> dataValue = new HashMap<>();
                
                String dataElement = getStringValue(row, "dataElement", "dataelement", "data_element");
                String value = getStringValue(row, "value", "value");
                String categoryOptionCombo = getStringValue(row, "categoryOptionCombo", "categoryoptioncombo", "category_option_combo");

                if (dataElement == null || value == null) {
                    log.warn("Skipping row with missing dataElement or value: {}", row);
                    continue;
                }

                dataValue.put("dataElement", dataElement);
                dataValue.put("value", value);
                
                // Only include categoryOptionCombo if it's not "default" or empty
                if (categoryOptionCombo != null && !categoryOptionCombo.trim().isEmpty() 
                        && !"default".equalsIgnoreCase(categoryOptionCombo.trim())) {
                    dataValue.put("categoryOptionCombo", categoryOptionCombo);
                }

                dataValues.add(dataValue);
            }

            if (!dataValues.isEmpty()) {
                dataValueSet.put("dataValues", dataValues);
                dataValueSets.add(dataValueSet);
                log.debug("Created DataValueSet: dataSet={}, period={}, orgUnit={}, dataValues={}", 
                         dataSet, period, orgUnit, dataValues.size());
            }
        }

        log.info("Created {} DataValueSets from {} rows", dataValueSets.size(), rows.size());
        exchange.getIn().setBody(dataValueSets);
    }

    /**
     * Gets a grouping value (can be column name or constant).
     */
    private String getGroupingValue(Map<String, Object> row, String keyOrConstant) {
        if (keyOrConstant == null) {
            return null;
        }
        // Try as column first
        Object value = getValueFromRow(row, keyOrConstant);
        if (value != null) {
            return value.toString().trim();
        }
        // If not found, assume it's a constant
        return keyOrConstant.trim();
    }

    /**
     * Gets a value from a row, trying multiple key variations (case-insensitive).
     */
    private Object getValueFromRow(Map<String, Object> row, String key) {
        if (key == null) {
            return null;
        }
        Object value = row.get(key);
        if (value == null) {
            // Try case-insensitive match
            for (String mapKey : row.keySet()) {
                if (mapKey.equalsIgnoreCase(key)) {
                    return row.get(mapKey);
                }
            }
        }
        return value;
    }

    /**
     * Evaluates a Groovy filter expression on a row.
     */
    private boolean evaluateFilter(Map<String, Object> row, String filterExpression) {
        try {
            Binding binding = new Binding();
            binding.setVariable("row", row);
            // Add all row values as variables for easier access
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
            
            GroovyShell shell = new GroovyShell(binding);
            Object result = shell.evaluate(filterExpression);
            
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return false;
        } catch (Exception e) {
            log.warn("Error evaluating filter '{}': {}", filterExpression, e.getMessage());
            return false;
        }
    }

    /**
     * Gets a string value from a map, trying multiple key variations (case-insensitive).
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = getValueFromRow(map, key);
            if (value != null) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
