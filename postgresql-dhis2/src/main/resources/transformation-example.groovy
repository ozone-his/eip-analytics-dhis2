// Example Groovy DSL transformation script
// This file demonstrates how to transform PostgreSQL data to DHIS2 format
//
// Available variables:
// - data: Map containing the PostgreSQL row data
// - exchange: Camel Exchange object
// - headers: Exchange headers
// - properties: Exchange properties
//
// Example transformation:

// Simple transformation - maps columns directly
return [
    dataSet: data.dataSet ?: "default",
    period: data.period,
    orgUnit: data.orgUnit,
    completeDate: java.time.LocalDate.now().toString(),
    dataValues: [[
        dataElement: data.dataElement,
        categoryOptionCombo: data.categoryOptionCombo ?: "default",
        value: String.valueOf(data.value)
    ]]
]

// Advanced transformation example (commented out):
/*
// Aggregate multiple data elements from one row
def dataValues = []

// Add first data element if present
if (data.dataElement1 && data.value1 != null) {
    dataValues.add([
        dataElement: data.dataElement1,
        categoryOptionCombo: data.categoryOptionCombo1 ?: "default",
        value: String.valueOf(data.value1)
    ])
}

// Add second data element if present
if (data.dataElement2 && data.value2 != null) {
    dataValues.add([
        dataElement: data.dataElement2,
        categoryOptionCombo: data.categoryOptionCombo2 ?: "default",
        value: String.valueOf(data.value2)
    ])
}

// Calculate derived values
if (data.value1 != null && data.value2 != null) {
    def calculatedValue = data.value1 + data.value2
    dataValues.add([
        dataElement: "CALCULATED_ELEMENT_ID",
        categoryOptionCombo: "default",
        value: String.valueOf(calculatedValue)
    ])
}

return [
    dataSet: data.dataSet,
    period: data.period,
    orgUnit: data.orgUnit,
    completeDate: java.time.LocalDate.now().toString(),
    dataValues: dataValues
]
*/
