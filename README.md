# EIP Analytics DHIS2 Integration

Apache Camel routes for scheduled data extraction from PostgreSQL and transformation to DHIS2 format.

## Overview

This project provides Enterprise Integration Patterns (EIP) routes using Apache Camel to:
- Read data from PostgreSQL database on a scheduled basis (configurable via cron)
- Transform data using dynamic DSL (Groovy-based)
- Send transformed data to DHIS2 via REST API

## Project Structure

```
eip-analytics-dhis2/
├── pom.xml                          # Parent POM
├── postgresql-dhis2/                # Main module
│   ├── pom.xml                      # Module POM
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/mekom/eip/dhis2/
│       │   │       ├── MainApp.java                 # Application entry point
│       │   │       ├── PostgresqlDhis2Route.java    # Main Camel route
│       │   │       ├── DynamicDslTransformer.java   # DSL transformation processor
│       │   │       ├── SyncStateService.java        # File-based sync state tracking
│       │   │       └── DataSourceConfig.java        # PostgreSQL DataSource configuration
│       │   └── resources/
│       │       ├── application.properties           # Configuration
│       │       └── logback.xml                      # Logging configuration
│       └── test/
└── README.md
```

## Prerequisites

- Java 17 or higher
- Maven 3.9+
- PostgreSQL database
- DHIS2 instance (or access to DHIS2 API)

## Quick Start with Docker Compose

For local development and testing, a Docker Compose setup is provided:

```bash
# First, build the EIP routes JAR
cd postgresql-dhis2
mvn clean package

# Then start all services
cd dev
cp .env.example .env  # Optional: customize configuration
docker-compose up -d
```

This will start:
- PostgreSQL (Analytics DB) on port 5432
- DHIS2 on port 8080 (admin/district)
- DHIS2 Database on port 5433
- EIP Client on port 8085 (runs the Camel routes)

The PostgreSQL database is automatically initialized with test data. The EIP client will automatically start extracting and syncing data to DHIS2 based on the configured schedule.

## Configuration

### Database Configuration

Edit `postgresql-dhis2/src/main/resources/application.properties`:

```properties
# PostgreSQL Configuration
postgresql.jdbc.url=jdbc:postgresql://localhost:5432/analytics
postgresql.username=postgres
postgresql.password=postgres
postgresql.pool.max.size=10
```

### Scheduling Configuration

Configure the cron expression for data extraction:

```properties
# Cron expression (default: every hour)
postgresql.query.cron=0 0 * * * ?
```

Cron format: `second minute hour day month weekday`

Examples:
- `0 0 * * * ?` - Every hour
- `0 0 0 * * ?` - Every day at midnight
- `0 */15 * * * ?` - Every 15 minutes

### SQL Query Configuration

Configure the SQL query to extract data:

```properties
postgresql.query.sql=SELECT test_date, facility_id, facility_name, test_type, patient_age, patient_gender, test_result, test_count, calculate_period(test_date) as period FROM lims_test_results WHERE test_date > :lastSyncTimestamp ORDER BY test_date, facility_id LIMIT 1000
```

The query should return columns that can be mapped to DHIS2 data elements. Common columns:
- `dataElement` - DHIS2 data element ID
- `period` - Period (e.g., "202401")
- `orgUnit` - Organization unit ID
- `value` - Data value
- `categoryOptionCombo` - Category option combo ID
- `dataSet` - Data set ID

### DHIS2 Configuration

```properties
dhis2.base.url=http://localhost:8080
dhis2.username=admin
dhis2.password=district
```

### Dynamic DSL Transformation

The transformation DSL uses Groovy and allows you to customize how data is transformed before sending to DHIS2.

#### Default Transformation

If no DSL is provided, the system applies a default transformation that maps PostgreSQL columns to DHIS2 dataValueSet format.

#### Custom DSL Transformation

You can provide a Groovy DSL script in the configuration:

```properties
transformation.dsl=return [
    dataSet: data.dataSet,
    period: data.period,
    orgUnit: data.orgUnit,
    completeDate: java.time.LocalDate.now().toString(),
    dataValues: [[
        dataElement: data.dataElement,
        categoryOptionCombo: data.categoryOptionCombo,
        value: data.value
    ]]
]
```

The DSL has access to:
- `data` - The input data map (from PostgreSQL row)
- `exchange` - The Camel Exchange object
- `headers` - Exchange headers
- `properties` - Exchange properties

#### Example: Complex Transformation

```groovy
// Aggregate multiple values
def dataValues = []
if (data.value1) {
    dataValues.add([
        dataElement: data.dataElement1,
        categoryOptionCombo: data.categoryOptionCombo1,
        value: data.value1
    ])
}
if (data.value2) {
    dataValues.add([
        dataElement: data.dataElement2,
        categoryOptionCombo: data.categoryOptionCombo2,
        value: data.value2
    ])
}

return [
    dataSet: data.dataSet,
    period: data.period,
    orgUnit: data.orgUnit,
    completeDate: java.time.LocalDate.now().toString(),
    dataValues: dataValues
]
```

## Building the Project

```bash
mvn clean install
```

## Running the Application

### Using Maven

```bash
cd postgresql-dhis2
mvn exec:java -Dexec.mainClass="com.mekom.eip.dhis2.MainApp"
```

### Using Java

```bash
cd postgresql-dhis2
java -cp target/classes:target/dependency/* com.mekom.eip.dhis2.MainApp
```

### Using System Properties

You can override configuration using system properties:

```bash
java -Dpostgresql.jdbc.url=jdbc:postgresql://localhost:5432/mydb \
     -Dpostgresql.username=user \
     -Dpostgresql.password=pass \
     -Ddhis2.base.url=http://dhis2.example.com \
     -Ddhis2.username=admin \
     -Ddhis2.password=district \
    -cp target/classes:target/dependency/* \
    com.mekom.eip.dhis2.MainApp
```

## DHIS2 Data Format

The application sends data to DHIS2 in the `dataValueSets` format:

```json
{
  "dataSet": "dataSetId",
  "period": "202401",
  "orgUnit": "orgUnitId",
  "completeDate": "2024-01-15",
  "dataValues": [
    {
      "dataElement": "dataElementId",
      "categoryOptionCombo": "categoryOptionComboId",
      "value": "123"
    }
  ]
}
```

## Error Handling

The application includes error handling:
- Failed messages are retried up to 3 times with 5-second delays
- Errors are logged to `logs/postgresql-dhis2.log`
- Failed messages are sent to a dead letter queue for manual review

## File-Based Sync State Tracking

Sync state is stored in `/eip-home/sync-state.json` so the integration can resume after restarts without relying on source DB schema changes.

**What is tracked:**
- `last_sync_timestamp` for incremental queries
- `sync_status` (SUCCESS/FAILED/IN_PROGRESS)
- `records_synced`
- timestamps for observability

**Example file:**
```json
{
    "pcr-test-results": {
        "report_id": "pcr-test-results",
        "report_name": "PCR Test Results from LIM",
        "last_sync_timestamp": "2024-01-27T10:30:45.123456Z",
        "sync_status": "SUCCESS",
        "records_synced": 45,
        "error_message": null,
        "created_at": "2024-01-26T08:00:00.000000Z",
        "updated_at": "2024-01-27T10:30:50.456789Z"
    }
}
```

**View/monitor sync state:**
```bash
cat /eip-home/sync-state.json
jq . /eip-home/sync-state.json
watch 'jq . /eip-home/sync-state.json'
```

**Reset sync state (force full re-sync):**
```bash
rm /eip-home/sync-state.json
```

## Timestamp-Based Incremental Sync

The SQL queries use `:lastSyncTimestamp` which is populated from `sync-state.json` by the route.

Example mapping in [postgresql-dhis2/src/main/resources/dhis2-mappings.yml](postgresql-dhis2/src/main/resources/dhis2-mappings.yml):
```yaml
sql: |
    SELECT test_date, facility_id, facility_name, test_type, patient_age, patient_gender, test_result, test_count,
                 calculate_period(test_date) as period
    FROM lims_test_results
    WHERE test_date > :lastSyncTimestamp
    ORDER BY test_date, facility_id
    LIMIT 1000
```

If no prior sync exists, a default timestamp is used and the route will sync all available data.

## Development Environment Setup (Docker Compose)

### Services
- **PostgreSQL (Analytics Database)** - Port 5432
- **DHIS2** - Port 8080 (admin/district)
- **DHIS2 Database** - Port 5433
- **EIP Client** - Port 8085
- **Artemis** - Port 8161/61616

### Database Initialization
The analytics DB is initialized with:
- Schema: [postgresql-dhis2/dev/init-db/01-init-schema.sql](postgresql-dhis2/dev/init-db/01-init-schema.sql)
- Test data: [postgresql-dhis2/dev/init-db/02-insert-test-data.sql](postgresql-dhis2/dev/init-db/02-insert-test-data.sql)

### DHIS2 Metadata Setup
Use the helper script to create data elements, org units, and data sets:
```bash
cd postgresql-dhis2/dev
./configure-dhis2.sh
```

### Verify Sync to DHIS2
Use the helper script:
```bash
cd postgresql-dhis2/dev
bash check-dhis2-sync.sh
```

Or query the API directly:
```bash
curl -u admin:district "http://localhost:8080/api/dataValueSets.json?dataSet=BfMAe6Itzgt&orgUnit=ImspTQPwCqd&orgUnit=DiszpKrYNg8&period=202401&period=202402&period=202403" | jq .
```

## Troubleshooting

### DHIS2 won't start
- Check logs: `docker compose logs dhis2`
- Ensure `dhis2-db` is healthy: `docker compose ps`
- Wait 2–3 minutes on first start

### EIP Client won't start
- Check logs: `docker compose logs eip-client`
- Ensure the JAR exists at `postgresql-dhis2/target/postgresql-dhis2-*.jar`
- Rebuild: `mvn clean package`

### No data appears in DHIS2
- Confirm SQL query returns rows
- Check [postgresql-dhis2/src/main/resources/dhis2-mappings.yml](postgresql-dhis2/src/main/resources/dhis2-mappings.yml) for IDs
- Verify sync state file under `/eip-home`

## Logging

Logs are written to:
- Console (STDOUT)
- File: `logs/postgresql-dhis2.log` (with daily rotation)

Log levels can be configured in `logback.xml`.

## Development

### Running Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```
