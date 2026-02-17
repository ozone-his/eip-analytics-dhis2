-- Insert LIM PCR test data for integration testing
-- This data simulates PCR test results from a Laboratory Information Management (LIM) system

-- PCR Test Results Data
-- Test data spans 3 months (Jan, Feb, Mar 2024) across 2 facilities
-- Includes various test results (positive/negative) and patient demographics

INSERT INTO lims_test_results (test_date, facility_id, facility_name, test_type, patient_age, patient_gender, test_result, test_count, period) VALUES
-- Facility 1: ImspTQPwCqd - January 2024
('2024-01-05', 'FACILITY_A', 'Facility A', 'PCR', 25, 'M', 'positive', 1, '202401'),
('2024-01-05', 'FACILITY_A', 'Facility A', 'PCR', 30, 'F', 'negative', 1, '202401'),
('2024-01-10', 'FACILITY_A', 'Facility A', 'PCR', 35, 'M', 'positive', 1, '202401'),
('2024-01-10', 'FACILITY_A', 'Facility A', 'PCR', 28, 'F', 'negative', 1, '202401'),
('2024-01-15', 'FACILITY_A', 'Facility A', 'PCR', 42, 'M', 'negative', 1, '202401'),
('2024-01-15', 'FACILITY_A', 'Facility A', 'PCR', 29, 'F', 'positive', 1, '202401'),
('2024-01-20', 'FACILITY_A', 'Facility A', 'PCR', 33, 'M', 'negative', 1, '202401'),
('2024-01-20', 'FACILITY_A', 'Facility A', 'PCR', 26, 'F', 'negative', 1, '202401'),
('2024-01-25', 'FACILITY_A', 'Facility A', 'PCR', 38, 'M', 'positive', 1, '202401'),
('2024-01-25', 'FACILITY_A', 'Facility A', 'PCR', 31, 'F', 'negative', 1, '202401'),

-- Facility 1: ImspTQPwCqd - February 2024
('2024-02-03', 'FACILITY_A', 'Facility A', 'PCR', 27, 'M', 'positive', 1, '202402'),
('2024-02-03', 'FACILITY_A', 'Facility A', 'PCR', 32, 'F', 'negative', 1, '202402'),
('2024-02-08', 'FACILITY_A', 'Facility A', 'PCR', 40, 'M', 'negative', 1, '202402'),
('2024-02-08', 'FACILITY_A', 'Facility A', 'PCR', 24, 'F', 'positive', 1, '202402'),
('2024-02-12', 'FACILITY_A', 'Facility A', 'PCR', 36, 'M', 'positive', 1, '202402'),
('2024-02-12', 'FACILITY_A', 'Facility A', 'PCR', 28, 'F', 'negative', 1, '202402'),
('2024-02-18', 'FACILITY_A', 'Facility A', 'PCR', 29, 'M', 'negative', 1, '202402'),
('2024-02-18', 'FACILITY_A', 'Facility A', 'PCR', 34, 'F', 'negative', 1, '202402'),
('2024-02-22', 'FACILITY_A', 'Facility A', 'PCR', 41, 'M', 'positive', 1, '202402'),
('2024-02-22', 'FACILITY_A', 'Facility A', 'PCR', 27, 'F', 'negative', 1, '202402'),

-- Facility 1: ImspTQPwCqd - March 2024
('2024-03-05', 'FACILITY_A', 'Facility A', 'PCR', 31, 'M', 'negative', 1, '202403'),
('2024-03-05', 'FACILITY_A', 'Facility A', 'PCR', 25, 'F', 'positive', 1, '202403'),
('2024-03-10', 'FACILITY_A', 'Facility A', 'PCR', 39, 'M', 'positive', 1, '202403'),
('2024-03-10', 'FACILITY_A', 'Facility A', 'PCR', 30, 'F', 'negative', 1, '202403'),
('2024-03-15', 'FACILITY_A', 'Facility A', 'PCR', 26, 'M', 'negative', 1, '202403'),
('2024-03-15', 'FACILITY_A', 'Facility A', 'PCR', 33, 'F', 'negative', 1, '202403'),
('2024-03-20', 'FACILITY_A', 'Facility A', 'PCR', 37, 'M', 'positive', 1, '202403'),
('2024-03-20', 'FACILITY_A', 'Facility A', 'PCR', 28, 'F', 'negative', 1, '202403'),
('2024-03-25', 'FACILITY_A', 'Facility A', 'PCR', 32, 'M', 'negative', 1, '202403'),
('2024-03-25', 'FACILITY_A', 'Facility A', 'PCR', 29, 'F', 'positive', 1, '202403'),

-- Facility 2: DiszpKrYNg8 - January 2024
('2024-01-07', 'FACILITY_B', 'Facility B', 'PCR', 28, 'M', 'positive', 1, '202401'),
('2024-01-07', 'FACILITY_B', 'Facility B', 'PCR', 35, 'F', 'negative', 1, '202401'),
('2024-01-12', 'FACILITY_B', 'Facility B', 'PCR', 31, 'M', 'negative', 1, '202401'),
('2024-01-12', 'FACILITY_B', 'Facility B', 'PCR', 27, 'F', 'positive', 1, '202401'),
('2024-01-17', 'FACILITY_B', 'Facility B', 'PCR', 44, 'M', 'positive', 1, '202401'),
('2024-01-17', 'FACILITY_B', 'Facility B', 'PCR', 26, 'F', 'negative', 1, '202401'),
('2024-01-22', 'FACILITY_B', 'Facility B', 'PCR', 30, 'M', 'negative', 1, '202401'),
('2024-01-22', 'FACILITY_B', 'Facility B', 'PCR', 32, 'F', 'negative', 1, '202401'),
('2024-01-27', 'FACILITY_B', 'Facility B', 'PCR', 36, 'M', 'positive', 1, '202401'),
('2024-01-27', 'FACILITY_B', 'Facility B', 'PCR', 29, 'F', 'negative', 1, '202401'),

-- Facility 2: DiszpKrYNg8 - February 2024
('2024-02-05', 'FACILITY_B', 'Facility B', 'PCR', 29, 'M', 'negative', 1, '202402'),
('2024-02-05', 'FACILITY_B', 'Facility B', 'PCR', 33, 'F', 'positive', 1, '202402'),
('2024-02-10', 'FACILITY_B', 'Facility B', 'PCR', 38, 'M', 'positive', 1, '202402'),
('2024-02-10', 'FACILITY_B', 'Facility B', 'PCR', 25, 'F', 'negative', 1, '202402'),
('2024-02-14', 'FACILITY_B', 'Facility B', 'PCR', 34, 'M', 'negative', 1, '202402'),
('2024-02-14', 'FACILITY_B', 'Facility B', 'PCR', 31, 'F', 'positive', 1, '202402'),
('2024-02-20', 'FACILITY_B', 'Facility B', 'PCR', 27, 'M', 'positive', 1, '202402'),
('2024-02-20', 'FACILITY_B', 'Facility B', 'PCR', 35, 'F', 'negative', 1, '202402'),
('2024-02-24', 'FACILITY_B', 'Facility B', 'PCR', 40, 'M', 'negative', 1, '202402'),
('2024-02-24', 'FACILITY_B', 'Facility B', 'PCR', 28, 'F', 'negative', 1, '202402'),

-- Facility 2: DiszpKrYNg8 - March 2024
('2024-03-07', 'FACILITY_B', 'Facility B', 'PCR', 32, 'M', 'positive', 1, '202403'),
('2024-03-07', 'FACILITY_B', 'Facility B', 'PCR', 26, 'F', 'negative', 1, '202403'),
('2024-03-12', 'FACILITY_B', 'Facility B', 'PCR', 37, 'M', 'negative', 1, '202403'),
('2024-03-12', 'FACILITY_B', 'Facility B', 'PCR', 29, 'F', 'positive', 1, '202403'),
('2024-03-17', 'FACILITY_B', 'Facility B', 'PCR', 28, 'M', 'negative', 1, '202403'),
('2024-03-17', 'FACILITY_B', 'Facility B', 'PCR', 34, 'F', 'negative', 1, '202403'),
('2024-03-22', 'FACILITY_B', 'Facility B', 'PCR', 39, 'M', 'positive', 1, '202403'),
('2024-03-22', 'FACILITY_B', 'Facility B', 'PCR', 27, 'F', 'negative', 1, '202403'),
('2024-03-27', 'FACILITY_B', 'Facility B', 'PCR', 31, 'M', 'negative', 1, '202403'),
('2024-03-27', 'FACILITY_B', 'Facility B', 'PCR', 30, 'F', 'positive', 1, '202403');

-- HIV patient-level event data is generated in 03-import-hiv-json.sql

-- Display inserted data summary
SELECT 
    period,
    facility_id,
    facility_name,
    test_result,
    patient_gender,
    COUNT(*) as test_count,
    SUM(test_count) as total_tests
FROM lims_test_results
GROUP BY period, facility_id, facility_name, test_result, patient_gender
ORDER BY period, facility_id, test_result, patient_gender;

-- Summary by period and facility
SELECT 
    period,
    facility_id,
    facility_name,
    COUNT(*) as total_records,
    SUM(CASE WHEN test_result = 'positive' THEN test_count ELSE 0 END) as positive_tests,
    SUM(CASE WHEN test_result = 'negative' THEN test_count ELSE 0 END) as negative_tests,
    SUM(test_count) as total_tests
FROM lims_test_results
GROUP BY period, facility_id, facility_name
ORDER BY period, facility_id;
