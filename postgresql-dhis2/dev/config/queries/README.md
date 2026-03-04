# SQL Queries Directory

This directory contains SQL query templates for DHIS2 data synchronization reports.

## Structure

Each SQL file corresponds to a report defined in `dhis2-mappings.yml`. The mapping uses the `sqlFile` property to reference the query file.

## Query Files

- **lims-pcr-facility-a.sql** - LIMS PCR test results for Facility A
- **lims-pcr-facility-b.sql** - LIMS PCR test results for Facility B
- **hiv-testing-facility-a.sql** - HIV testing indicators for Facility A
- **hiv-testing-facility-b.sql** - HIV testing indicators for Facility B

## Adding New Queries

1. Create a new `.sql` file in this directory with an appropriate name (e.g., `my-report.sql`)
2. Write the SQL query. Use the following placeholders:
   - `:#lastSyncTimestamp` - Replaced with the timestamp of the last successful sync
   - Column names must match those referenced in the `dataValueMappings` section
3. Update `dhis2-mappings.yml` to reference the new query:
   ```yaml
   - id: my-report
     name: My Report
     dataSet: <dataset-id>
     sqlFile: queries/my-report.sql
     # ... rest of config
   ```

## Query Guidelines

- Ensure the SQL SELECT includes:
  - A `data_set` column with the dataset ID
  - A `period` column in YYYYMM format
  - Alias columns exactly as named in `dataValueMappings` under `valueColumn`

Example:
```sql
SELECT
  'BfMAe6Itzgt' AS data_set,
  TO_CHAR(test_date, 'YYYYMM') AS period,
  SUM(positive_count) AS positive_tests,
  SUM(total_count) AS total_tests
FROM my_table
WHERE test_date > :#lastSyncTimestamp
GROUP BY TO_CHAR(test_date, 'YYYYMM')
ORDER BY period
```

## File Resolution

SQL files are resolved from the config directory (where `dhis2-mappings.yml` is located). Both relative and absolute paths are supported:
- `queries/my-report.sql` - Relative to config directory
- `/absolute/path/to/my-report.sql` - Absolute path
