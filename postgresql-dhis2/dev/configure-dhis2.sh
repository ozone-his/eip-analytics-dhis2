#!/bin/bash

# Script to configure DHIS2 metadata to match test data
# This script imports GEN_LIB metadata and creates organization units and data sets
# that match the IDs used in init-db/02-insert-test-data.sql

set -e

# Configuration
DHIS2_URL="${DHIS2_URL:-http://localhost:8080}"
DHIS2_USERNAME="${DHIS2_USERNAME:-admin}"
DHIS2_PASSWORD="${DHIS2_PASSWORD:-district}"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== DHIS2 Configuration Script ===${NC}"
echo "DHIS2 URL: $DHIS2_URL"
echo "Username: $DHIS2_USERNAME"
echo ""

# Wait for DHIS2 to be ready (before attempting any API calls)
echo -e "${YELLOW}Waiting for DHIS2 to be ready...${NC}"
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -s -f -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" "$DHIS2_URL/api/system/info" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ DHIS2 is ready!${NC}"
        break
    fi
    attempt=$((attempt + 1))
    echo "  Attempt $attempt/$max_attempts: Waiting for DHIS2..."
    sleep 2
done

if [ $attempt -eq $max_attempts ]; then
    echo -e "${RED}DHIS2 did not become ready in time. Please check the logs.${NC}"
    exit 1
fi
echo ""

# Function to make API calls (define early so other functions can use it)
api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    local file=$4  # Optional file path for file uploads
    
    local response
    if [ -n "$file" ]; then
        # File upload (for metadata import)
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            --data-binary "@$file" \
            "$DHIS2_URL/api/$endpoint")
    elif [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            "$DHIS2_URL/api/$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            -d "$data" \
            "$DHIS2_URL/api/$endpoint")
    fi
    
    # Extract HTTP status code (last line)
    local http_code=$(echo "$response" | tail -n1)
    # Extract response body (all but last line)
    local body=$(echo "$response" | sed '$d')
    
    # Return body, but check status code
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "$body"
        return 0
    elif [ "$http_code" -eq 409 ]; then
        # Conflict - resource might already exist, which is OK
        echo "$body"
        return 0
    else
        echo "Error: HTTP $http_code" >&2
        echo "$body" >&2
        return 1
    fi
}

# Function to check if resource exists
resource_exists() {
    local endpoint=$1
    local id=$2
    # Suppress stderr for 404 errors (expected when resource doesn't exist)
    local response=$(api_call "GET" "$endpoint/$id" 2>/dev/null)
    echo "$response" | grep -q "\"id\":\"$id\"" && return 0 || return 1
}

# Function to fetch organisation unit level by level number
fetch_ou_level() {
    local level_num=$1
    local response=$(api_call "GET" "organisationUnitLevels.json?fields=id,level&paging=false")
    echo "$response" | grep -o "\"level\":$level_num[^}]*\"id\":\"[^\"]*\"" | grep -o "\"id\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

# Function to update and import metadata file
import_metadata_file() {
    local file_path=$1
    local ou_level_facility_uid=$2
    local temp_file="/tmp/$(basename "$file_path")"
    
    echo -e "${YELLOW}Preparing metadata file: $(basename "$file_path")${NC}"
    
    # Copy and replace placeholder
    cp "$file_path" "$temp_file"
    
    # Replace <OU_LEVEL_FACILITY_UID> with actual facility level UID
    if grep -q "<OU_LEVEL_FACILITY_UID>" "$temp_file"; then
        sed -i '' "s/<OU_LEVEL_FACILITY_UID>/$ou_level_facility_uid/g" "$temp_file"
        echo -e "  ${GREEN}✓ Replaced OU_LEVEL_FACILITY_UID with: $ou_level_facility_uid${NC}"
    else
        echo -e "  ${YELLOW}No placeholder found${NC}"
    fi
    
    # Import metadata
    echo -e "  Importing metadata..."
    local response=$(api_call "POST" "metadata" "" "$temp_file")
    
    # Check import status - look for status in response object
    local status=$(echo "$response" | grep -o '"response":{"responseType":"ImportReport","status":"[^"]*"' | cut -d'"' -f8)
    
    if [ "$status" = "OK" ] || [ "$status" = "WARNING" ]; then
        # Extract stats from response
        local total=$(echo "$response" | grep -o '"total":[0-9]*' | tail -1 | cut -d':' -f2)
        local updated=$(echo "$response" | grep -o '"updated":[0-9]*' | tail -1 | cut -d':' -f2)
        local created=$(echo "$response" | grep -o '"created":[0-9]*' | tail -1 | cut -d':' -f2)
        
        echo -e "  ${GREEN}✓ Metadata imported successfully${NC}"
        echo -e "    Total: $total | Created: $created | Updated: $updated"
        return 0
    else
        echo -e "  ${YELLOW}⚠ Import completed with status: ${status:-UNKNOWN}${NC}"
        # Check if any objects were processed
        local total=$(echo "$response" | grep -o '"total":[0-9]*' | tail -1 | cut -d':' -f2)
        if [ -n "$total" ] && [ "$total" -gt 0 ]; then
            echo -e "  ${GREEN}✓ Objects were processed: $total${NC}"
            return 0
        else
            echo "  Response: $(echo "$response" | head -c 200)"
            return 1
        fi
    fi
}

# Function to make API calls
api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    local file=$4  # Optional file path for file uploads
    
    local response
    if [ -n "$file" ]; then
        # File upload (for metadata import)
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            --data-binary "@$file" \
            "$DHIS2_URL/api/$endpoint")
    elif [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            "$DHIS2_URL/api/$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -u "$DHIS2_USERNAME:$DHIS2_PASSWORD" \
            -d "$data" \
            "$DHIS2_URL/api/$endpoint")
    fi
    
    # Extract HTTP status code (last line)
    local http_code=$(echo "$response" | tail -n1)
    # Extract response body (all but last line)
    local body=$(echo "$response" | sed '$d')
    
    # Return body, but check status code
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "$body"
        return 0
    elif [ "$http_code" -eq 409 ]; then
        # Conflict - resource might already exist, which is OK
        echo "$body"
        return 0
    else
        echo "Error: HTTP $http_code" >&2
        echo "$body" >&2
        return 1
    fi
}

# Function to check if resource exists
resource_exists() {
    local endpoint=$1
    local id=$2
    # Suppress stderr for 404 errors (expected when resource doesn't exist)
    local response=$(api_call "GET" "$endpoint/$id" 2>/dev/null)
    echo "$response" | grep -q "\"id\":\"$id\"" && return 0 || return 1
}

# ===== IMPORT METADATA PACKAGES =====
echo -e "${GREEN}=== Importing Metadata Packages ===${NC}"

# Fetch facility-level UID (Level 2)
echo -e "${YELLOW}Fetching organisation unit levels...${NC}"
FACILITY_LEVEL_UID=$(fetch_ou_level 2)
if [ -z "$FACILITY_LEVEL_UID" ]; then
    echo -e "${RED}Could not fetch facility-level UID. Using Level 1 as fallback.${NC}"
    FACILITY_LEVEL_UID=$(fetch_ou_level 1)
fi

if [ -z "$FACILITY_LEVEL_UID" ]; then
    echo -e "${RED}Failed to fetch any organisation unit level. Cannot proceed with metadata import.${NC}"
    exit 1
fi

echo -e "${GREEN}Facility-level UID: $FACILITY_LEVEL_UID${NC}"
echo ""

# Import common library metadata (GEN_LIB)
GEN_LIB_FILE="$SCRIPT_DIR/data/GEN_LIB_2.0.0_DHIS2.41/GEN_LIB_COMPLETE_2.0.0_DHIS2.41.json"
if [ -f "$GEN_LIB_FILE" ]; then
    import_metadata_file "$GEN_LIB_FILE" "$FACILITY_LEVEL_UID" || true
    echo ""
else
    echo -e "${YELLOW}Warning: GEN_LIB metadata file not found at $GEN_LIB_FILE${NC}"
    echo ""
fi

# Import HIV aggregated metadata (HIV_AGG)
HIV_AGG_FILE="$SCRIPT_DIR/data/HIV_AGG_2.1.0_DHIS2.41/HIV_AGG_COMPLETE_2.1.0_DHIS2.41/HIV_AGG_COMPLETE_2.1.0_DHIS2.41.json"
if [ -f "$HIV_AGG_FILE" ]; then
    import_metadata_file "$HIV_AGG_FILE" "$FACILITY_LEVEL_UID" || true
    echo ""
else
    echo -e "${YELLOW}Warning: HIV_AGG metadata file not found at $HIV_AGG_FILE${NC}"
    echo ""
fi

echo -e "${GREEN}=== Metadata Import Complete ===${NC}"
echo ""

# ===== CREATE ORGANIZATION HIERARCHY =====
echo -e "${GREEN}=== Creating Organization Hierarchy ===${NC}"

# Get or create root organization unit (needed as parent)
echo -e "${YELLOW}Getting or creating root organization unit...${NC}"
ROOT_ORG_UNIT="HfVjCurKxh2"  # Fixed ID for root org unit

if resource_exists "organisationUnits" "$ROOT_ORG_UNIT"; then
    echo -e "  Root organization unit ${GREEN}$ROOT_ORG_UNIT${NC} already exists"
else
    # Create root organization unit
    ROOT_ORG_UNIT_DATA='{
        "id": "'"$ROOT_ORG_UNIT"'",
        "name": "Test Root Organization",
        "shortName": "Test Root",
        "code": "TEST_ROOT",
        "openingDate": "2020-01-01"
    }'
    result=$(api_call "POST" "organisationUnits" "$ROOT_ORG_UNIT_DATA" 2>&1)
    if [ $? -eq 0 ]; then
        echo -e "  Created root organization unit ${GREEN}$ROOT_ORG_UNIT${NC} (Test Root Organization)"
    else
        echo -e "  ${RED}Failed to create root organization unit${NC}"
        echo "  Error: $(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "$result" | tail -1)"
        exit 1
    fi
fi
echo -e "${GREEN}Using parent organization unit: $ROOT_ORG_UNIT${NC}"
echo ""

# Create Organization Units
echo -e "${YELLOW}Creating organization units for test facilities...${NC}"

# Organization Unit 1: ImspTQPwCqd
if resource_exists "organisationUnits" "ImspTQPwCqd"; then
    echo -e "  Organization unit ${GREEN}ImspTQPwCqd${NC} already exists"
else
    ORG_UNIT_1='{
        "id": "ImspTQPwCqd",
        "name": "Test Health Facility 1",
        "shortName": "THF1",
        "code": "THF1",
        "openingDate": "2020-01-01",
        "parent": {
            "id": "'"$ROOT_ORG_UNIT"'"
        }
    }'
    result=$(api_call "POST" "organisationUnits" "$ORG_UNIT_1" 2>&1)
    if [ $? -eq 0 ]; then
        echo -e "  Created organization unit ${GREEN}ImspTQPwCqd${NC} (Test Health Facility 1)"
    else
        echo -e "  ${RED}Failed to create organization unit ImspTQPwCqd${NC}"
        echo "  Error: $(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "$result" | tail -1)"
        exit 1
    fi
fi

# Organization Unit 2: DiszpKrYNg8
if resource_exists "organisationUnits" "DiszpKrYNg8"; then
    echo -e "  Organization unit ${GREEN}DiszpKrYNg8${NC} already exists"
else
    ORG_UNIT_2='{
        "id": "DiszpKrYNg8",
        "name": "Test Health Facility 2",
        "shortName": "THF2",
        "code": "THF2",
        "openingDate": "2020-01-01",
        "parent": {
            "id": "'"$ROOT_ORG_UNIT"'"
        }
    }'
    result=$(api_call "POST" "organisationUnits" "$ORG_UNIT_2" 2>&1)
    if [ $? -eq 0 ]; then
        echo -e "  Created organization unit ${GREEN}DiszpKrYNg8${NC} (Test Health Facility 2)"
    else
        echo -e "  ${RED}Failed to create organization unit DiszpKrYNg8${NC}"
        echo "  Error: $(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "$result" | tail -1)"
        exit 1
    fi
fi

echo -e "${GREEN}=== Organization Hierarchy Created ===${NC}"
echo ""

# Function to configure admin user org unit access
configure_user_access() {
    echo -e "${YELLOW}Configuring admin user org unit access...${NC}"
    
    local user_id
    local all_org_units
    
    # Get current admin user
    user_id=$(api_call GET "users.json?filter=username:eq:$DHIS2_USERNAME&fields=id" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
    
    if [ -z "$user_id" ]; then
        echo -e "${RED}Could not find admin user${NC}"
        return 1
    fi
    
    echo -e "  Found user ID: $user_id"
    
    # Get full user object
    USER_FULL=$(api_call GET "users/$user_id.json?fields=:all" 2>/dev/null)
    
    if [ -z "$USER_FULL" ]; then
        echo -e "${RED}Could not retrieve user object${NC}"
        return 1
    fi
    
    # Get all org units
    all_org_units=$(api_call GET "organisationUnits.json?fields=id&paging=false" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
    
    # Build org unit array for user update
    local org_unit_refs="["
    local first=true
    while IFS= read -r ou_id; do
        if [ -n "$ou_id" ]; then
            if [ "$first" = false ]; then
                org_unit_refs+=","
            fi
            org_unit_refs+="{\"id\":\"$ou_id\"}"
            first=false
        fi
    done <<< "$all_org_units"
    org_unit_refs+="]"
    
    echo -e "  Updating user with org unit access..."
    
    # Use Python to update the user object with org units
    UPDATED_USER=$(python3 << PYTHON_EOF
import json
import sys

try:
    user_obj = json.loads('''$USER_FULL''')
    org_units = json.loads('''$org_unit_refs''')
    
    # Set organisation units for data capture, data viewing, and search
    user_obj['organisationUnits'] = org_units
    user_obj['dataViewOrganisationUnits'] = org_units
    user_obj['teiSearchOrganisationUnits'] = org_units
    
    print(json.dumps(user_obj))
except Exception as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)
PYTHON_EOF
)
    
    if [ $? -ne 0 ] || [ -z "$UPDATED_USER" ]; then
        echo -e "${RED}Failed to prepare user update${NC}"
        return 1
    fi
    
    # Update user with PUT
    if api_call PUT "users/$user_id" "$UPDATED_USER" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Admin user now has access to all organization units${NC}"
    else
        echo -e "${YELLOW}Warning: Could not update user org units${NC}"
        return 1
    fi
}

# Get default category combo (usually "bjDvmb4bfuf")
echo -e "${YELLOW}Getting default category combo...${NC}"
DEFAULT_CATEGORY_COMBO=$(api_call "GET" "categoryCombos?filter=name:eq:default&paging=false" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$DEFAULT_CATEGORY_COMBO" ]; then
    # Try to get any category combo
    DEFAULT_CATEGORY_COMBO=$(api_call "GET" "categoryCombos?paging=false&pageSize=1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -z "$DEFAULT_CATEGORY_COMBO" ]; then
        echo -e "${YELLOW}No category combo found, using default ID: bjDvmb4bfuf${NC}"
        DEFAULT_CATEGORY_COMBO="bjDvmb4bfuf"
    fi
fi
echo -e "${GREEN}Using category combo: $DEFAULT_CATEGORY_COMBO${NC}"

# Create Data Elements
echo -e "${YELLOW}Creating data elements for test data...${NC}"

# Data elements needed for test data (format: id|name|description)
DATA_ELEMENTS=(
    "Fu5yaroCXiZ|Positive tests (total)|Positive PCR tests"
    "gJaB7Ne6k7c|Negative tests (total)|Negative PCR tests"
    "OMYdA6qs2qI|Positive tests (male)|Positive PCR tests - Male"
    "Aytls9Fx4y5|Positive tests (female)|Positive PCR tests - Female"
    "Fm6cUmmiY3d|Total tests|Total PCR tests conducted"
)

for element in "${DATA_ELEMENTS[@]}"; do
    IFS='|' read -r de_id de_name de_description <<< "$element"
    
    if resource_exists "dataElements" "$de_id"; then
        echo -e "  Data element ${GREEN}$de_id${NC} ($de_name) already exists"
    else
        DATA_ELEMENT="{
            \"id\": \"$de_id\",
            \"name\": \"$de_name\",
            \"shortName\": \"$de_name\",
            \"code\": \"$de_id\",
            \"description\": \"$de_description\",
            \"domainType\": \"AGGREGATE\",
            \"valueType\": \"INTEGER\",
            \"aggregationType\": \"SUM\",
            \"categoryCombo\": {
                \"id\": \"$DEFAULT_CATEGORY_COMBO\"
            },
            \"zeroIsSignificant\": false
        }"
        
        result=$(api_call "POST" "dataElements" "$DATA_ELEMENT" 2>&1)
        if [ $? -eq 0 ]; then
            echo -e "  Created data element ${GREEN}$de_id${NC} ($de_name)"
        else
            echo -e "  ${RED}Failed to create data element $de_id${NC}"
            echo "  Error: $(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "$result" | tail -1)"
            exit 1
        fi
    fi
done

# Get default category option combo (usually "default" or "HllvX50cXC0")
echo -e "${YELLOW}Getting default category option combo...${NC}"
DEFAULT_COC=$(api_call "GET" "categoryOptionCombos?filter=name:eq:default&paging=false" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$DEFAULT_COC" ]; then
    # Try common default ID
    DEFAULT_COC=$(api_call "GET" "categoryOptionCombos/HllvX50cXC0" 2>/dev/null | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -z "$DEFAULT_COC" ]; then
        # Get first available category option combo
        DEFAULT_COC=$(api_call "GET" "categoryOptionCombos?paging=false&pageSize=1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ -z "$DEFAULT_COC" ]; then
            echo -e "${YELLOW}No category option combo found, using default ID: HllvX50cXC0${NC}"
            DEFAULT_COC="HllvX50cXC0"
        fi
    fi
fi
echo -e "${GREEN}Using category option combo: $DEFAULT_COC${NC}"

# Data elements array for data set creation
TEST_DATA_ELEMENTS=("Fu5yaroCXiZ" "gJaB7Ne6k7c" "OMYdA6qs2qI" "Aytls9Fx4y5" "Fm6cUmmiY3d")

# Create Data Set with data elements from GEN_LIB metadata
echo -e "${YELLOW}Creating data set...${NC}"

if resource_exists "dataSets" "BfMAe6Itzgt"; then
    echo -e "  Data set ${GREEN}BfMAe6Itzgt${NC} already exists"
    # Update it to include all test data elements
    echo -e "  ${YELLOW}Updating data set to include all test data elements...${NC}"
else
    # Build dataSetElements array for all test data elements
    DATA_SET_ELEMENTS="["
    for i in "${!TEST_DATA_ELEMENTS[@]}"; do
        de_id="${TEST_DATA_ELEMENTS[$i]}"
        if [ $i -gt 0 ]; then
            DATA_SET_ELEMENTS+=","
        fi
        DATA_SET_ELEMENTS+="{\"dataElement\":{\"id\":\"$de_id\"},\"categoryCombo\":{\"id\":\"$DEFAULT_CATEGORY_COMBO\"}}"
    done
    DATA_SET_ELEMENTS+="]"
    
    DATA_SET="{
        \"id\": \"BfMAe6Itzgt\",
        \"name\": \"Test Analytics Data Set\",
        \"shortName\": \"TADS\",
        \"code\": \"TADS\",
        \"periodType\": \"Monthly\",
        \"categoryCombo\": {
            \"id\": \"$DEFAULT_CATEGORY_COMBO\"
        },
        \"dataSetElements\": $DATA_SET_ELEMENTS,
        \"organisationUnits\": [
            {\"id\": \"ImspTQPwCqd\"},
            {\"id\": \"DiszpKrYNg8\"}
        ]
    }"
    
    result=$(api_call "POST" "dataSets" "$DATA_SET" 2>&1)
    if [ $? -eq 0 ]; then
        echo -e "  Created data set ${GREEN}BfMAe6Itzgt${NC} (Test Analytics Data Set)"
    else
        echo -e "  ${RED}Failed to create data set BfMAe6Itzgt${NC}"
        echo "  Error: $(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "$result" | tail -1)"
        exit 1
    fi
fi

# Assign data set to organization units
echo -e "${YELLOW}Assigning data set to organization units...${NC}"
# Update the data set to include organization units
UPDATE_DATA_SET='{
    "id": "BfMAe6Itzgt",
    "organisationUnits": [
        {"id": "ImspTQPwCqd"},
        {"id": "DiszpKrYNg8"}
    ]
}'
if api_call "PUT" "dataSets/BfMAe6Itzgt" "$UPDATE_DATA_SET" > /dev/null 2>&1; then
    echo -e "  ${GREEN}Data set assigned to organization units${NC}"
else
    echo -e "  ${YELLOW}Warning: Could not assign data set to organization units (may already be assigned)${NC}"
fi

# Assign data set to user (so user can access it)
echo -e "${YELLOW}Assigning data set to user for access...${NC}"
# Get current user info
USER_INFO=$(api_call "GET" "me" 2>/dev/null)
CURRENT_USER=$(echo "$USER_INFO" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
CURRENT_USERNAME=$(echo "$USER_INFO" | grep -o '"username":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$CURRENT_USER" ]; then
    echo -e "  Current user: ${GREEN}$CURRENT_USERNAME${NC} (ID: $CURRENT_USER)"
    
    # Get full user object with all fields
    USER_OBJECT=$(api_call "GET" "users/$CURRENT_USER.json?fields=:all" 2>/dev/null)
    if [ -n "$USER_OBJECT" ]; then
        # Check if data set is already assigned
        if echo "$USER_OBJECT" | grep -q "\"id\":\"BfMAe6Itzgt\""; then
            echo -e "  ${GREEN}Data set already assigned to user${NC}"
        else
            # Use Python to properly update the user JSON (preserving all fields)
            UPDATED_USER=$(python3 << PYTHON_EOF
import json
import sys

try:
    user_obj = json.loads('''$USER_OBJECT''')
    
    # Get existing data sets or initialize empty list
    data_sets = user_obj.get('dataSets', [])
    if data_sets is None:
        data_sets = []
    
    # Check if data set already in list
    data_set_ids = [ds.get('id') if isinstance(ds, dict) else ds for ds in data_sets]
    if 'BfMAe6Itzgt' not in data_set_ids:
        # Add data set
        data_sets.append({'id': 'BfMAe6Itzgt'})
        user_obj['dataSets'] = data_sets
    
    print(json.dumps(user_obj))
except Exception as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)
PYTHON_EOF
)
            
            if [ $? -eq 0 ] && [ -n "$UPDATED_USER" ]; then
                result=$(api_call "PUT" "users/$CURRENT_USER" "$UPDATED_USER" 2>&1)
                if [ $? -eq 0 ]; then
                    echo -e "  ${GREEN}Data set assigned to user${NC}"
                else
                    echo -e "  ${YELLOW}Warning: Could not assign data set to user${NC}"
                    echo -e "  ${YELLOW}You can assign it manually via DHIS2 UI: Settings > Users > $CURRENT_USERNAME > Data Sets${NC}"
                fi
            else
                echo -e "  ${YELLOW}Warning: Could not update user object${NC}"
                echo -e "  ${YELLOW}You can assign the data set manually via DHIS2 UI${NC}"
            fi
        fi
    else
        echo -e "  ${YELLOW}Could not retrieve user object${NC}"
        echo -e "  ${YELLOW}You can assign the data set manually via DHIS2 UI${NC}"
    fi
else
    echo -e "  ${YELLOW}Could not determine current user ID${NC}"
    echo -e "  ${YELLOW}You can assign the data set manually via DHIS2 UI${NC}"
fi

# Configure sharing settings for visibility
echo -e "${YELLOW}Configuring sharing settings...${NC}"

# Get current user's userGroups
echo -e "  Getting user groups..."
USER_GROUPS=$(api_call "GET" "me.json?fields=userGroups[id,name]" 2>/dev/null) || true
USER_GROUP_ID=$(echo "$USER_GROUPS" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$USER_GROUP_ID" ]; then
    echo -e "  ${YELLOW}No user group found. Using public access for visibility.${NC}"
    # Set public access
    SHARING_SETTINGS='{
        "publicAccess": "rw------",
        "externalAccess": false
    }'
else
    echo -e "  ${GREEN}Found user group: $USER_GROUP_ID${NC}"
    # Set public access and user group access
    SHARING_SETTINGS='{
        "publicAccess": "rw------",
        "externalAccess": false,
        "userGroupAccesses": [
            {
                "id": "'"$USER_GROUP_ID"'",
                "access": "rw------"
            }
        ]
    }'
fi

# Update sharing for organization units (non-fatal)
for org_unit_id in "ImspTQPwCqd" "DiszpKrYNg8"; do
    echo -e "  Setting sharing for organization unit: $org_unit_id"
    api_call "PUT" "sharing?type=organisationUnit&id=$org_unit_id" "$SHARING_SETTINGS" > /dev/null 2>&1 || true
    echo -e "    ${GREEN}✓ Sharing configured${NC}"
done

# Update sharing for data set (non-fatal)
echo -e "  Setting sharing for data set: BfMAe6Itzgt"
api_call "PUT" "sharing?type=dataSet&id=BfMAe6Itzgt" "$SHARING_SETTINGS" > /dev/null 2>&1 || true
echo -e "    ${GREEN}✓ Sharing configured${NC}"

# Update sharing for data elements (non-fatal)
for de_id in "${TEST_DATA_ELEMENTS[@]}"; do
    if resource_exists "dataElements" "$de_id"; then
        echo -e "  Setting sharing for data element: $de_id"
        api_call "PUT" "sharing?type=dataElement&id=$de_id" "$SHARING_SETTINGS" > /dev/null 2>&1 || true
        echo -e "    ${GREEN}✓ Sharing configured${NC}"
    fi
done

echo ""
echo -e "${GREEN}=== Configuration Complete ===${NC}"

# Configure admin user org unit access
configure_user_access

echo ""
echo "Created:"
echo "  - Data Elements: ${TEST_DATA_ELEMENTS[*]}"
echo "  - Organization Units: ImspTQPwCqd (Test Health Facility 1), DiszpKrYNg8 (Test Health Facility 2)"
echo "  - Data Set: BfMAe6Itzgt (Test Analytics Data Set)"
echo "  - Sharing: Configured for public read/write access"
echo ""
echo "Configuration Summary:"
echo "  - Organization units are assigned to the root org unit: $ROOT_ORG_UNIT"
echo "  - Data set includes ${#TEST_DATA_ELEMENTS[@]} data elements"
echo "  - Data set is assigned to user: $CURRENT_USERNAME"
echo "  - Sharing is configured for visibility"
echo "  - Default Category Combo: $DEFAULT_CATEGORY_COMBO"
echo "  - Default Category Option Combo: $DEFAULT_COC"
echo ""
echo "You can now:"
echo "  1. View the metadata in DHIS2: $DHIS2_URL"
echo "  2. The test data in PostgreSQL should now sync successfully"
echo "  3. Check data values: $DHIS2_URL/api/dataValueSets.json?dataSet=BfMAe6Itzgt&period=202401"
echo "  4. View in Data Entry: $DHIS2_URL/dhis-web-dataentry/index.action"
echo ""
echo -e "${YELLOW}Note: Use these values in your dhis2-mappings.yml:${NC}"
echo "  - DHIS2_ORG_UNIT_UID_FOR_A=ImspTQPwCqd"
echo "  - DHIS2_ORG_UNIT_UID_FOR_B=DiszpKrYNg8"
echo "  - categoryOptionCombo: $DEFAULT_COC (for all data values)"