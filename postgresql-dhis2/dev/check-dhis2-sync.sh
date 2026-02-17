#!/bin/bash

# Check DHIS2 data sync status
# Queries DHIS2 for synced dataValues and counts them

echo "=========================================="
echo "DHIS2 Data Sync Verification"
echo "=========================================="
echo ""

# DHIS2 credentials and endpoint
DHIS2_URL="http://localhost:8080"
DHIS2_USER="admin"
DHIS2_PASSWORD="district"
DATA_SET="BfMAe6Itzgt"
ORG_UNITS="ImspTQPwCqd&orgUnit=DiszpKrYNg8"
PERIODS="202401&period=202402&period=202403"

# Query DHIS2 API
echo "Querying DHIS2 API..."
RESPONSE=$(curl -s -u "${DHIS2_USER}:${DHIS2_PASSWORD}" \
  "${DHIS2_URL}/api/dataValueSets.json?dataSet=${DATA_SET}&orgUnit=${ORG_UNITS}&period=${PERIODS}")

# Check if curl was successful
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to connect to DHIS2"
  exit 1
fi

# Count dataValues
DATA_VALUES_COUNT=$(echo "$RESPONSE" | jq '.dataValues | length' 2>/dev/null)

if [ -z "$DATA_VALUES_COUNT" ] || [ "$DATA_VALUES_COUNT" == "null" ]; then
  echo "ERROR: Invalid response from DHIS2"
  echo ""
  echo "Response:"
  echo "$RESPONSE" | jq . 2>/dev/null || echo "$RESPONSE"
  exit 1
fi

echo ""
echo "Results:"
echo "--------"
echo "Total dataValues synced: $DATA_VALUES_COUNT"
echo ""

# Show breakdown by org unit if data exists
if [ "$DATA_VALUES_COUNT" -gt 0 ]; then
  echo "Breakdown by Organization Unit:"
  FACILITY_A_COUNT=$(echo "$RESPONSE" | jq '[.dataValues[] | select(.orgUnit == "ImspTQPwCqd")] | length')
  FACILITY_B_COUNT=$(echo "$RESPONSE" | jq '[.dataValues[] | select(.orgUnit == "DiszpKrYNg8")] | length')
  
  echo "  - Facility A (ImspTQPwCqd): $FACILITY_A_COUNT dataValues"
  echo "  - Facility B (DiszpKrYNg8): $FACILITY_B_COUNT dataValues"
  echo ""
  
  echo "Breakdown by Period:"
  for period in 202401 202402 202403; do
    PERIOD_COUNT=$(echo "$RESPONSE" | jq "[.dataValues[] | select(.period == \"$period\")] | length")
    echo "  - $period: $PERIOD_COUNT dataValues"
  done
  echo ""
  
  # Show sample dataValue
  echo "Sample dataValue:"
  echo "$RESPONSE" | jq '.dataValues[0]' 2>/dev/null
else
  echo "No data has been synced yet."
  echo ""
  echo "Checking sync state file..."
  docker compose exec -T eip-client cat /eip-home/sync-state.json 2>/dev/null | jq . || echo "Sync state file not found or empty"
fi

echo ""
echo "=========================================="
