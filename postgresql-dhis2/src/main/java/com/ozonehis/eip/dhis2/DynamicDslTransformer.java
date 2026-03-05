package com.ozonehis.eip.dhis2;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Processor that applies dynamic DSL transformations to data.
 * The transformation DSL is loaded from configuration and executed using Groovy.
 */
public class DynamicDslTransformer implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicDslTransformer.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        
        if (body == null) {
            log.warn("Received null body, skipping transformation");
            return;
        }

        // Check if body is already a DataValueSet (from aggregator)
        Map<String, Object> dataMap;
        if (body instanceof Map) {
            dataMap = (Map<String, Object>) body;
            
            // If it already has dataValues array, it's already a DataValueSet - pass through
            if (dataMap.containsKey("dataValues") && dataMap.get("dataValues") instanceof List) {
                log.debug("Body is already a DataValueSet, passing through");
                // Ensure completeDate is set if missing
                if (!dataMap.containsKey("completeDate")) {
                    dataMap.put("completeDate", java.time.LocalDate.now().toString());
                }
                return;
            }
        } else {
            log.warn("Body is not a Map, cannot transform");
            return;
        }

        // Get transformation DSL from exchange properties or configuration
        String transformationDsl = exchange.getProperty("transformation.dsl", String.class);
        if (transformationDsl == null || transformationDsl.isEmpty()) {
            // Try to get from exchange headers or use default
            transformationDsl = exchange.getIn().getHeader("transformation.dsl", String.class);
        }
        
        if (transformationDsl == null || transformationDsl.isEmpty()) {
            // Use default transformation or load from configuration
            // Try EIP-prefixed property first, then fallback to non-prefixed
            transformationDsl = exchange.getContext().resolvePropertyPlaceholders("{{eip.transformation.dsl:{{transformation.dsl:}}}}");
        }

        // If no DSL provided, apply default transformation to DHIS2 format
        if (transformationDsl == null || transformationDsl.isEmpty()) {
            log.debug("No transformation DSL provided, applying default DHIS2 transformation");
            Map<String, Object> dhis2Data = applyDefaultTransformation(dataMap);
            exchange.getIn().setBody(dhis2Data);
            return;
        }

        // Execute dynamic DSL transformation
        try {
            Map<String, Object> transformedData = executeDslTransformation(dataMap, transformationDsl, exchange);
            exchange.getIn().setBody(transformedData);
            log.debug("Successfully applied DSL transformation");
        } catch (Exception e) {
            log.error("Error executing DSL transformation: {}", e.getMessage(), e);
            throw new RuntimeException("DSL transformation failed", e);
        }
    }

    /**
     * Executes the Groovy DSL transformation script.
     */
    private Map<String, Object> executeDslTransformation(
            Map<String, Object> inputData, 
            String dslScript, 
            Exchange exchange) {
        
        Binding binding = new Binding();
        binding.setVariable("data", inputData);
        binding.setVariable("exchange", exchange);
        binding.setVariable("headers", exchange.getIn().getHeaders());
        binding.setVariable("properties", exchange.getProperties());
        
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate(dslScript);
        
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        } else {
            log.warn("DSL transformation did not return a Map, wrapping result");
            Map<String, Object> resultMap = new java.util.HashMap<>();
            resultMap.put("result", result);
            return resultMap;
        }
    }

    /**
     * Default transformation to DHIS2 dataValueSet format.
     */
    private Map<String, Object> applyDefaultTransformation(Map<String, Object> inputData) {
        Map<String, Object> dhis2Data = new java.util.HashMap<>();
        
        // Default transformation assumes input has: dataElement, period, orgUnit, value, categoryOptionCombo
        dhis2Data.put("dataSet", inputData.getOrDefault("dataSet", "default"));
        dhis2Data.put("completeDate", inputData.getOrDefault("completeDate", java.time.LocalDate.now().toString()));
        dhis2Data.put("period", inputData.getOrDefault("period", ""));
        dhis2Data.put("orgUnit", inputData.getOrDefault("orgUnit", ""));
        
        java.util.List<Map<String, Object>> dataValues = new java.util.ArrayList<>();
        Map<String, Object> dataValue = new java.util.HashMap<>();
        dataValue.put("dataElement", inputData.getOrDefault("dataElement", ""));
        // Only include categoryOptionCombo if it's not "default" or empty
        Object categoryOptionCombo = inputData.getOrDefault("categoryOptionCombo", "default");
        if (categoryOptionCombo != null && !categoryOptionCombo.toString().trim().isEmpty() 
                && !"default".equalsIgnoreCase(categoryOptionCombo.toString().trim())) {
            dataValue.put("categoryOptionCombo", categoryOptionCombo);
        }
        dataValue.put("value", inputData.getOrDefault("value", ""));
        dataValues.add(dataValue);
        
        dhis2Data.put("dataValues", dataValues);
        
        return dhis2Data;
    }
}
