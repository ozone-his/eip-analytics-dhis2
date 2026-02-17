package com.mekom.eip.dhis2;

import java.util.List;
import java.util.Map;

/**
 * Configuration model for DHIS2 column mappings.
 * This mirrors the structure from OpenMRS DHIS2 Reporting Module's ReportDefinition.
 */
public class Dhis2MappingConfig {
    
    private List<ReportMapping> reports;

    public List<ReportMapping> getReports() {
        return reports;
    }

    public void setReports(List<ReportMapping> reports) {
        this.reports = reports;
    }

    public static class ReportMapping {
        private String id;
        private String name;
        private String description;
        private String dataSet;
        private String sql;
        private GroupByConfig groupBy;
        private List<DataValueMapping> dataValueMappings;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDataSet() {
            return dataSet;
        }

        public void setDataSet(String dataSet) {
            this.dataSet = dataSet;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public GroupByConfig getGroupBy() {
            return groupBy;
        }

        public void setGroupBy(GroupByConfig groupBy) {
            this.groupBy = groupBy;
        }

        public List<DataValueMapping> getDataValueMappings() {
            return dataValueMappings;
        }

        public void setDataValueMappings(List<DataValueMapping> dataValueMappings) {
            this.dataValueMappings = dataValueMappings;
        }
    }

    public static class GroupByConfig {
        private String dataSet;  // Can be column name or constant
        private String period;   // Column name
        private String orgUnit;  // Column name

        public String getDataSet() {
            return dataSet;
        }

        public void setDataSet(String dataSet) {
            this.dataSet = dataSet;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public String getOrgUnit() {
            return orgUnit;
        }

        public void setOrgUnit(String orgUnit) {
            this.orgUnit = orgUnit;
        }
    }

    public static class DataValueMapping {
        private String name;
        private String dataElement;
        private String valueColumn;
        private String categoryOptionCombo;
        private String filter;  // Optional Groovy filter expression

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDataElement() {
            return dataElement;
        }

        public void setDataElement(String dataElement) {
            this.dataElement = dataElement;
        }

        public String getValueColumn() {
            return valueColumn;
        }

        public void setValueColumn(String valueColumn) {
            this.valueColumn = valueColumn;
        }

        public String getCategoryOptionCombo() {
            return categoryOptionCombo;
        }

        public void setCategoryOptionCombo(String categoryOptionCombo) {
            this.categoryOptionCombo = categoryOptionCombo;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }
    }
}
