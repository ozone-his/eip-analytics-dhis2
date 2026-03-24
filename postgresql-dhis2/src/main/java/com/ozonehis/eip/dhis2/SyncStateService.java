package com.ozonehis.eip.dhis2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based sync state service that stores sync state in /eip-home/sync-state.json
 * Replaces database-based storage for better portability and self-contained operation.
 */
@Service
public class SyncStateService {
    
    private static final Logger log = LoggerFactory.getLogger(SyncStateService.class);
    private static final String SYNC_STATE_FILE = "sync-state.json";
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    @Value("${eip.home:/eip-home}")
    private String eipHome;
    
    @Value("${eip.sync.default-start:2020-01-01T00:00:00Z}")
    private String configuredDefaultSyncStart;
    
    private ObjectMapper objectMapper;
    private File syncStateFile;
    private Instant defaultSyncStart;
    
    public SyncStateService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    private Instant getDefaultSyncStart() {
        if (defaultSyncStart != null) {
            return defaultSyncStart;
        }
        
        String configuredValue = configuredDefaultSyncStart != null ? configuredDefaultSyncStart.trim() : "";
        try {
            defaultSyncStart = Instant.parse(configuredValue);
        } catch (Exception e) {
            defaultSyncStart = Instant.parse("2020-01-01T00:00:00Z");
            log.warn("Invalid eip.sync.default-start value '{}'. Using fallback {}",
                    configuredValue, defaultSyncStart);
        }
        return defaultSyncStart;
    }
    
    /**
     * Enum for sync status
     */
    public enum SyncStatus {
        PENDING("PENDING"),
        IN_PROGRESS("IN_PROGRESS"),
        SUCCESS("SUCCESS"),
        FAILED("FAILED");
        
        private final String value;
        
        SyncStatus(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Initialize the sync state file path
     */
    private File getSyncStateFile() {
        if (syncStateFile == null) {
            syncStateFile = new File(eipHome, SYNC_STATE_FILE);
        }
        return syncStateFile;
    }
    
    /**
     * Load all sync states from file
     */
    private Map<String, Map<String, Object>> loadAllStates() {
        lock.readLock().lock();
        try {
            File file = getSyncStateFile();
            if (!file.exists()) {
                log.debug("Sync state file does not exist: {}", file.getAbsolutePath());
                return new HashMap<>();
            }
            
            String content = new String(Files.readAllBytes(file.toPath()));
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> states = objectMapper.readValue(content, Map.class);
            return states != null ? states : new HashMap<>();
            
        } catch (IOException e) {
            log.warn("Error loading sync state file: {}", e.getMessage());
            return new HashMap<>();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Save all sync states to file
     */
    private void saveAllStates(Map<String, Map<String, Object>> states) {
        lock.writeLock().lock();
        try {
            File file = getSyncStateFile();
            File directory = file.getParentFile();
            
            // Create directory if it doesn't exist
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    log.info("Created directory: {}", directory.getAbsolutePath());
                } else {
                    log.warn("Could not create directory: {}", directory.getAbsolutePath());
                }
            }
            
            // Write to file
            objectMapper.writeValue(file, states);
            log.debug("Saved sync state to: {}", file.getAbsolutePath());
            
        } catch (IOException e) {
            log.error("Error saving sync state file: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the last successful sync state for a report
     */
    public Map<String, Object> getLastSyncState(String reportId) {
        Map<String, Map<String, Object>> allStates = loadAllStates();
        
        if (allStates.containsKey(reportId)) {
            Map<String, Object> state = allStates.get(reportId);
            log.info("Retrieved sync state for report {}: status={}", 
                    reportId, state.get("sync_status"));
            return state;
        }
        
        log.info("No sync state found for report: {}. Starting fresh sync.", reportId);
        Map<String, Object> freshState = new HashMap<>();
        freshState.put("report_id", reportId);
        freshState.put("last_sync_timestamp", getDefaultSyncStart().toString());
        freshState.put("sync_status", SyncStatus.PENDING.getValue());
        return freshState;
    }
    
    /**
     * Mark a sync as in-progress
     */
    public void markSyncInProgress(String reportId, String reportName) {
        Map<String, Map<String, Object>> allStates = loadAllStates();
        
        Map<String, Object> state = allStates.getOrDefault(reportId, new HashMap<>());
        state.put("report_id", reportId);
        state.put("report_name", reportName);
        state.put("sync_status", SyncStatus.IN_PROGRESS.getValue());
        state.put("updated_at", Instant.now().toString());
        
        allStates.put(reportId, state);
        saveAllStates(allStates);
        
        log.debug("Marked sync as IN_PROGRESS for report: {}", reportId);
    }
    
    /**
     * Update sync state after successful sync
     */
    public void updateSyncSuccess(String reportId, String reportName, int recordsCount) {
        Map<String, Map<String, Object>> allStates = loadAllStates();
        
        Map<String, Object> state = allStates.getOrDefault(reportId, new HashMap<>());
        state.put("report_id", reportId);
        state.put("report_name", reportName);
        state.put("sync_status", SyncStatus.SUCCESS.getValue());
        state.put("records_synced", recordsCount);
        state.put("error_message", null);
        state.put("updated_at", Instant.now().toString());

        // Advance checkpoint on every successful run (including zero-row runs)
        // so incremental queries don't stay pinned to an old/null timestamp.
        state.put("last_sync_timestamp", Instant.now().toString());
        
        if (!state.containsKey("created_at")) {
            state.put("created_at", Instant.now().toString());
        }
        
        allStates.put(reportId, state);
        saveAllStates(allStates);
        
        log.info("Updated sync success for report {}: {} records synced", reportId, recordsCount);
    }
    
    /**
     * Update sync state after failed sync
     */
    public void updateSyncFailure(String reportId, String errorMessage) {
        Map<String, Map<String, Object>> allStates = loadAllStates();
        
        Map<String, Object> state = allStates.getOrDefault(reportId, new HashMap<>());
        state.put("report_id", reportId);
        state.put("sync_status", SyncStatus.FAILED.getValue());
        state.put("error_message", errorMessage);
        state.put("updated_at", Instant.now().toString());
        
        if (!state.containsKey("created_at")) {
            state.put("created_at", Instant.now().toString());
        }
        
        allStates.put(reportId, state);
        saveAllStates(allStates);
        
        log.warn("Updated sync failure for report {}: {}", reportId, errorMessage);
    }
    
    /**
     * Get last sync timestamp for a report (useful for incremental queries)
     */
    public Instant getLastSyncTimestamp(String reportId) {
        Map<String, Object> state = getLastSyncState(reportId);
        
        if (state.get("last_sync_timestamp") == null) {
            Instant defaultStart = getDefaultSyncStart();
            log.debug("No previous sync timestamp for report {}. Using default start {}",
                    reportId, defaultStart);
            return defaultStart;
        }
        
        try {
            String timestamp = state.get("last_sync_timestamp").toString();
            return Instant.parse(timestamp);
        } catch (Exception e) {
            Instant defaultStart = getDefaultSyncStart();
            log.warn("Error parsing last sync timestamp for report {}: {}. Using default start {}",
                    reportId, e.getMessage(), defaultStart);
            return defaultStart;
        }
    }
    
    /**
     * Reset sync state (useful for manual full syncs)
     */
    public void resetSyncState(String reportId) {
        Map<String, Map<String, Object>> allStates = loadAllStates();
        
        Map<String, Object> state = allStates.getOrDefault(reportId, new HashMap<>());
        state.put("report_id", reportId);
        state.put("last_sync_timestamp", getDefaultSyncStart().toString());
        state.put("sync_status", SyncStatus.PENDING.getValue());
        state.put("records_synced", 0);
        state.put("error_message", null);
        state.put("updated_at", Instant.now().toString());
        
        if (!state.containsKey("created_at")) {
            state.put("created_at", Instant.now().toString());
        }
        
        allStates.put(reportId, state);
        saveAllStates(allStates);
        
        log.info("Reset sync state for report: {}", reportId);
    }
    
    /**
     * Get the sync state file location for reference
     */
    public String getSyncStateLocation() {
        return getSyncStateFile().getAbsolutePath();
    }
}
