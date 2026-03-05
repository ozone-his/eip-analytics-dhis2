package com.ozonehis.eip.dhis2;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/**
 * Main Camel route for scheduled data extraction from PostgreSQL and sending to DHIS2.
 * 
 * The @Component annotation enables Spring Boot component scanning to discover this route.
 * 
 * This route supports resume-from-checkpoint capability via SyncStateService, allowing
 * it to recover from crashes or restarts by continuing from the last successful sync.
 */
@Component
public class PostgresqlDhis2Route extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(PostgresqlDhis2Route.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private SyncStateService syncStateService;

    @Override
    public void configure() throws Exception {
        // Register DataSource if not already registered
        if (getContext().getRegistry().lookupByName("postgresqlDataSource") == null) {
            DataSourceConfig.registerDataSource(getContext());
        }
        
        // Get cron expression for scheduling (default: every 2 minutes)
        String cronExpression = getContext().resolvePropertyPlaceholders("{{eip.postgresql.query.cron:0 */2 * * * ?}}");
        log.info("PostgreSQL query cron expression: {}", cronExpression);

        // Main route: iterate over configured reports, run each report's SQL, aggregate, transform, send
        // Users duplicate a report per facility and set orgUnit in the mapping; SQL should already be filtered per report
        from("quartz://postgresqlQuery?cron={{eip.postgresql.query.cron:0 */2 * * * ?}}")
                .routeId("postgresql-to-dhis2-route")
                .description("Scheduled route to extract data from PostgreSQL and send to DHIS2")
                .log("Starting scheduled data extraction from PostgreSQL")
                .process(exchange -> {
                    Dhis2MappingConfig config = ConfigurationLoader.loadConfiguration();
                    if (config != null && config.getReports() != null && !config.getReports().isEmpty()) {
                        exchange.getIn().setBody(config.getReports());
                    } else {
                        log.warn("No reports configured in dhis2-mappings.yml. Skipping sync.");
                        exchange.getIn().setBody(java.util.Collections.emptyList());
                    }
                })
                .split(body())
                    .process(exchange -> {
                        Dhis2MappingConfig.ReportMapping report = exchange.getIn().getBody(Dhis2MappingConfig.ReportMapping.class);
                        exchange.setProperty("DHIS2_REPORT_MAPPING", report);
                        
                        // Determine report ID and name for sync tracking
                        String reportId = (report != null && report.getId() != null) ? report.getId() : "default";
                        String reportName = (report != null && report.getName() != null) ? report.getName() : "Default Report";
                        
                        exchange.setProperty("SYNC_REPORT_ID", reportId);
                        exchange.setProperty("SYNC_REPORT_NAME", reportName);
                        
                        // Mark sync as in-progress
                        syncStateService.markSyncInProgress(reportId, reportName);
                        
                        // Get last sync timestamp for incremental queries
                        Instant lastSyncTime = syncStateService.getLastSyncTimestamp(reportId);
                        exchange.setProperty("LAST_SYNC_TIMESTAMP", lastSyncTime);
                        
                        // Set as SQL parameter (use epoch start if no previous sync)
                        java.sql.Timestamp lastSyncTimestamp = lastSyncTime != null 
                            ? java.sql.Timestamp.from(lastSyncTime)
                            : java.sql.Timestamp.valueOf("2020-01-01 00:00:00");
                        
                        // For Camel SQL component, pass parameters as a Map in the body
                        // Camel will substitute :#paramName with values from this map
                        java.util.Map<String, Object> sqlParams = new java.util.HashMap<>();
                        sqlParams.put("lastSyncTimestamp", lastSyncTimestamp);
                        exchange.getIn().setBody(sqlParams);

                        if (report == null || report.getSql() == null || report.getSql().trim().isEmpty()) {
                            log.warn("No SQL query for report '{}' (id: {}). Skipping.", reportName, reportId);
                            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
                            return;
                        }

                        // Normalize multi-line SQL to single line for Camel SQL component
                        String sqlQuery = report.getSql().trim().replaceAll("\\s+", " ");
                        log.info("Running report {} - {} (last sync: {})", reportId, reportName, lastSyncTime);
                        exchange.getIn().setHeader("CamelSqlQuery", sqlQuery);
                    })
                    .to("direct:execute-sql")
                    .process(new DataValueSetAggregator())
                    .split(body())
                        .log("Processing DataValueSet: dataSet=${body[dataSet]}, period=${body[period]}, orgUnit=${body[orgUnit]}, dataValues=${body[dataValues].size()}")
                        .process(new DynamicDslTransformer())
                        // Body is now a DataValueSet Map with multiple dataValues
                        .to("direct:dhis2-send")
                    .end()
                    .process(exchange -> {
                        // After successful processing, update sync state
                        String reportId = (String) exchange.getProperty("SYNC_REPORT_ID");
                        String reportName = (String) exchange.getProperty("SYNC_REPORT_NAME");
                        Object body = exchange.getIn().getBody();
                        
                        // Count records (simplified - in production, track actual record count)
                        int recordCount = body instanceof java.util.List ? ((java.util.List<?>) body).size() : 1;
                        
                        syncStateService.updateSyncSuccess(reportId, reportName, recordCount);
                        log.info("Successfully synced report {}: {} records", reportId, recordCount);
                    })
                .end()
                .log("Completed scheduled data extraction");

        // Route to execute SQL queries dynamically
        from("direct:execute-sql")
                .routeId("execute-sql-route")
                .description("Route to execute SQL queries and return results as list of maps")
                // Body contains Map with parameter values, CamelSqlQuery header contains SQL
                // Use toD() with simple expression to build the sql: endpoint URI
                .toD("sql:${header.CamelSqlQuery}?dataSource=#postgresqlDataSource");

        // Route to send data to DHIS2 using the dedicated DHIS2 component
        from("direct:dhis2-send")
                .routeId("dhis2-send-route")
                .description("Route to send transformed data to DHIS2")
                .log("Sending data to DHIS2: ${body}")
                .process(exchange -> {
                    // Body should already be a Map from DynamicDslTransformer
                    // DHIS2 component expects Map/Object, which it will serialize to JSON internally
                    
                    // Resolve base URL and ensure it includes /api
                    String baseUrl = exchange.getContext().resolvePropertyPlaceholders("{{eip.dhis2.base.url}}");
                    baseUrl = baseUrl.trim();
                    // Remove trailing slash if present
                    if (baseUrl.endsWith("/")) {
                        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                    }
                    // Ensure /api is included in baseApiUrl
                    if (!baseUrl.endsWith("/api")) {
                        baseUrl = baseUrl + "/api";
                    }
                    exchange.getIn().setHeader("DHIS2_BASE_API_URL", baseUrl);
                    log.info("Using DHIS2 base API URL: {}", baseUrl);
                })
                .process(exchange -> {
                    // Store the request body before sending to DHIS2 for error logging
                    Object requestBody = exchange.getIn().getBody();
                    exchange.setProperty("DHIS2_REQUEST_BODY", requestBody);
                })
                .doTry()
                    .toD("dhis2:post/resource?path=dataValueSets" +
                            "&baseApiUrl=${header.DHIS2_BASE_API_URL}" +
                            "&username={{eip.dhis2.username}}" +
                            "&password={{eip.dhis2.password}}")
                    .log("Response from DHIS2: ${body}")
                .doCatch(Exception.class)
                    .process(exchange -> {
                        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                        // Log the full exception chain immediately so the reason is visible in logs
                        // before the message is handed off to the async DLQ consumer
                        StringBuilder errMsg = new StringBuilder("DHIS2 send failed");
                        if (ex != null) {
                            Throwable cause = ex;
                            while (cause != null) {
                                errMsg.append(" -> ").append(cause.getMessage());
                                cause = cause.getCause();
                            }
                        }
                        log.error("DHIS2 POST error: {}", errMsg);

                        // Pass the report ID as a JMS header so the DLQ consumer can attribute the failure
                        String reportId = (String) exchange.getProperty("SYNC_REPORT_ID");
                        if (reportId != null) {
                            exchange.getIn().setHeader("SYNC_REPORT_ID", reportId);
                        }

                        // Serialize body to string for JMS transport
                        Object body = exchange.getIn().getBody();
                        if (body != null && !(body instanceof String)) {
                            try {
                                exchange.getIn().setBody(objectMapper.writeValueAsString(body));
                            } catch (Exception e) {
                                exchange.getIn().setBody(String.valueOf(body));
                            }
                        }
                    })
                    .to("jms:queue:dhis2-dlq?connectionFactory=#bean:connectionFactory&jmsMessageType=Text")
                    .process(exchange -> {
                        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                        if (ex != null) {
                            throw ex;
                        }
                        throw new RuntimeException("Unknown error while sending to DHIS2");
                    })
                .end();

        // Dead Letter Queue consumer - processes messages that failed delivery
        from("jms:queue:dhis2-dlq?connectionFactory=#bean:connectionFactory&concurrentConsumers=1&jmsMessageType=Text")
                .routeId("dhis2-dlq-consumer")
                .log(LoggingLevel.ERROR, "Message received in Dead Letter Queue: ${body}")
                .process(exchange -> {
                    Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    Object body = exchange.getIn().getBody();
                    Integer redeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                    int redeliveryCount = redeliveryCounter == null ? 0 : redeliveryCounter;
                    
                    // Report ID is passed as a JMS header from the send route
                    String reportId = exchange.getIn().getHeader("SYNC_REPORT_ID", String.class);
                    if (reportId == null) {
                        reportId = (String) exchange.getProperty("SYNC_REPORT_ID");
                    }
                    if (reportId == null) {
                        reportId = "unknown";
                    }
                    
                    String errorMsg = String.format("DLQ - Failed to process message after %d redeliveries. Body: %s", redeliveryCount, body);
                    log.error(errorMsg);
                    
                    // Update sync state with failure
                    syncStateService.updateSyncFailure(reportId, errorMsg);
                    
                    if (exception != null) {
                        log.error("DLQ - Exception: ", exception);
                    }
                })
                .to("log:dhis2-dlq?level=ERROR");

        // Error handling route
        from("direct:dhis2-error-handler")
                .routeId("dhis2-error-handler")
                .process(exchange -> {
                    Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (exception == null) {
                        exception = exchange.getIn().getHeader(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    }
                    
                    // Get the original request body (stored before DHIS2 call)
                    Object requestBody = exchange.getProperty("DHIS2_REQUEST_BODY");
                    if (requestBody == null) {
                        // Fallback to current body
                        requestBody = exchange.getIn().getBody();
                    }
                    
                    // Get response body if available (might be in exception or exchange)
                    Object responseBody = exchange.getIn().getBody();
                    
                    // Build detailed error message
                    StringBuilder errorDetails = new StringBuilder();
                    errorDetails.append("\n=== DHIS2 Error Details ===\n");
                    
                    int httpStatusCode = 0;
                    String httpStatusMessage = null;
                    
                    if (exception != null) {
                        errorDetails.append("Exception Type: ").append(exception.getClass().getName()).append("\n");
                        errorDetails.append("Exception Message: ").append(exception.getMessage()).append("\n");
                        
                        // Try to extract HTTP response details from exception
                        String exceptionMsg = exception.getMessage();
                        if (exceptionMsg != null) {
                            // Look for HTTP response code in the message (format: code=409)
                            if (exceptionMsg.contains("code=")) {
                                try {
                                    String codeStr = exceptionMsg.substring(
                                        exceptionMsg.indexOf("code=") + 5,
                                        exceptionMsg.indexOf(",", exceptionMsg.indexOf("code="))
                                    );
                                    httpStatusCode = Integer.parseInt(codeStr.trim());
                                    errorDetails.append("HTTP Status Code: ").append(httpStatusCode).append("\n");
                                } catch (Exception e) {
                                    // Ignore parsing errors
                                }
                            }
                            
                            // Extract URL if present
                            if (exceptionMsg.contains("url=")) {
                                try {
                                    String url = exceptionMsg.substring(
                                        exceptionMsg.indexOf("url=") + 4
                                    );
                                    if (url.contains(",")) {
                                        url = url.substring(0, url.indexOf(","));
                                    }
                                    errorDetails.append("Request URL: ").append(url.trim()).append("\n");
                                } catch (Exception e) {
                                    // Ignore parsing errors
                                }
                            }
                            
                            // Extract full exception chain
                            Throwable cause = exception.getCause();
                            int depth = 0;
                            while (cause != null && depth < 5) {
                                errorDetails.append("Caused by (").append(depth + 1).append("): ")
                                           .append(cause.getClass().getName()).append(" - ")
                                           .append(cause.getMessage()).append("\n");
                                
                                // Try to extract response body from RemoteDhis2ClientException
                                if (cause.getClass().getName().contains("RemoteDhis2ClientException")) {
                                    try {
                                        // Try to get response body using reflection
                                        java.lang.reflect.Method getResponseMethod = cause.getClass().getMethod("getResponse");
                                        if (getResponseMethod != null) {
                                            Object response = getResponseMethod.invoke(cause);
                                            if (response != null) {
                                                errorDetails.append("DHIS2 Response Object: ").append(response.toString()).append("\n");
                                                
                                                // Try to get response body
                                                try {
                                                    java.lang.reflect.Method getBodyMethod = response.getClass().getMethod("body");
                                                    if (getBodyMethod != null) {
                                                        Object body = getBodyMethod.invoke(response);
                                                        if (body != null) {
                                                            errorDetails.append("DHIS2 Response Body: ").append(body.toString()).append("\n");
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    // Ignore if method doesn't exist
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Ignore reflection errors
                                    }
                                }
                                
                                cause = cause.getCause();
                                depth++;
                            }
                        }
                        
                        // Print full stack trace for debugging
                        log.error("DHIS2 Error - Full Stack Trace:", exception);
                    } else {
                        errorDetails.append("No exception found in exchange\n");
                    }
                    
                    errorDetails.append("\nRequest Body: ").append(requestBody).append("\n");
                    errorDetails.append("Response Body: ").append(responseBody).append("\n");
                    
                    // Add specific handling for 409 Conflict
                    if (httpStatusCode == 409) {
                        errorDetails.append("\nNOTE: HTTP 409 (Conflict) typically means the data value already exists in DHIS2.\n");
                        errorDetails.append("This may not be a critical error - the data is already present.\n");
                        log.warn("DHIS2 returned 409 Conflict - data may already exist");
                    }
                    
                    errorDetails.append("===========================\n");
                    
                    log.error(errorDetails.toString());
                })
                .log(LoggingLevel.ERROR, "DHIS2 Error occurred: ${exception.message}")
                .log(LoggingLevel.ERROR, "Failed message body: ${body}")
                .to("log:dhis2-errors?level=ERROR");
    }
}
