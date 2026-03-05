-- Generate sample HIV patient testing data
-- This script creates test data for HIV patient events with focus on testing indicators

-- Generate HIV patient testing events
INSERT INTO hiv_patient_events (
    event_date,
    org_unit,
    patient_age,
    GEN_FAMILY_NAME,
    GEN_GIVEN_NAME,
    GEN_SEX_W_UNK,
    HIV_HFP_TESTING_AVAILABLE,
    HIV_HFP_VIRAL_LOAD_TESTING_AVAILABLE,
    HIV_TEST_M,
    HIV_TEST_POS_M,
    HIV_HTS_CLIENTS_TESTED_HIV_Y,
    HIV_HTS_CLIENT_HIV_POSITIVE_Y,
    HIV_NEW_CASES_M,
    HIV_NEW_HIV_CASES_STARTED_ART_M,
    HIV_ART_M,
    HIV_VIRSUPP_M,
    HIV_STI_CLIENTS_SYPHILIS_TESTED_Y,
    HIV_STI_CLIENTS_SYPHILIS_TESTED_POSITIVE_Y
) VALUES
-- January 2024
--                                                                                  BOOL  BOOL  INT   INT              INT                       INT                 INT                          INT        INT      INT                           INT                              INT
--                                                                         HFP_TEST  HFP_VL TEST_M TEST_POS_M  HTS_CLIENTS_TESTED_HIV_Y  HTS_CLIENT_HIV_POS_Y  NEW_CASES_M  NEW_HIV_CASES_STARTED_ART_M  ART_M  VIRSUPP_M  STI_SYPH_TESTED_Y  STI_SYPH_TESTED_POS_Y
('2024-01-05', 'ImspTQPwCqd', 28, 'Okello',   'Amina',   'Female', TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 1, 0),
('2024-01-10', 'ImspTQPwCqd', 35, 'Mwangi',   'Grace',   'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 1, 1),
('2024-01-15', 'ImspTQPwCqd', 42, 'Njeri',    'Faith',   'Female', TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 0, 0),
('2024-01-20', 'ImspTQPwCqd', 26, 'Achieng',  'Mary',    'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 0),
('2024-01-08', 'ImspTQPwCqd', 31, 'Omondi',   'James',   'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 0, 0),
('2024-01-12', 'ImspTQPwCqd', 39, 'Kamau',    'Peter',   'Male',   TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 1, 0),
('2024-01-18', 'ImspTQPwCqd', 44, 'Wanjiru',  'John',    'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 0, 0),
('2024-01-25', 'ImspTQPwCqd', 29, 'Otieno',   'Mark',    'Male',   TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 0, 0),

-- February 2024
('2024-02-03', 'DiszpKrYNg8', 33, 'Wambui',   'Sarah',   'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 0),
('2024-02-08', 'DiszpKrYNg8', 27, 'Nyambura', 'Jane',    'Female', TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 0, 0, 0),
('2024-02-14', 'DiszpKrYNg8', 38, 'Akinyi',   'Lucy',    'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 1),
('2024-02-20', 'DiszpKrYNg8', 30, 'Chebet',   'Rose',    'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 0, 0),
('2024-02-05', 'DiszpKrYNg8', 36, 'Kipchoge', 'David',   'Male',   TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 0, 0),
('2024-02-10', 'DiszpKrYNg8', 41, 'Mutua',    'Samuel',  'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 0),
('2024-02-16', 'DiszpKrYNg8', 25, 'Odhiambo', 'Tom',     'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 0, 0),
('2024-02-22', 'DiszpKrYNg8', 34, 'Kariuki',  'Paul',    'Male',   TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 1, 0),

-- March 2024
('2024-03-04', 'ImspTQPwCqd', 32, 'Wanjiku',  'Anne',    'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 1),
('2024-03-09', 'ImspTQPwCqd', 45, 'Juma',     'Michael', 'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 0, 0),
('2024-03-14', 'ImspTQPwCqd', 23, 'Awuor',    'Betty',   'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 1, 0),
('2024-03-19', 'ImspTQPwCqd', 37, 'Maina',    'Robert',  'Male',   TRUE, TRUE,  1, 0,  1, 0, 0, 0, 1, 1, 0, 0),
('2024-03-06', 'DiszpKrYNg8', 29, 'Njoki',    'Esther',  'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 1, 0),
('2024-03-12', 'DiszpKrYNg8', 40, 'Koech',    'Daniel',  'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 0, 0, 0),
('2024-03-17', 'DiszpKrYNg8', 31, 'Adhiambo', 'Violet',  'Female', TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 0, 0),
('2024-03-24', 'DiszpKrYNg8', 47, 'Kimani',   'George',  'Male',   TRUE, TRUE,  1, 1,  1, 1, 1, 1, 1, 1, 0, 0);

-- Display testing summary by period and facility
SELECT 
    TO_CHAR(event_date, 'YYYY-MM') as period,
    org_unit,
    GEN_SEX_W_UNK as gender,
    COUNT(*) as patient_count,
    SUM(HIV_TEST_M) as tests_performed,
    SUM(HIV_TEST_POS_M) as positive_tests,
    SUM(HIV_NEW_CASES_M) as new_cases,
    SUM(HIV_NEW_HIV_CASES_STARTED_ART_M) as cases_started_art,
    SUM(HIV_ART_M) as on_art,
    SUM(HIV_VIRSUPP_M) as virally_suppressed,
    SUM(HIV_STI_CLIENTS_SYPHILIS_TESTED_Y) as syphilis_tests,
    SUM(HIV_STI_CLIENTS_SYPHILIS_TESTED_POSITIVE_Y) as syphilis_positive
FROM hiv_patient_events
GROUP BY TO_CHAR(event_date, 'YYYY-MM'), org_unit, GEN_SEX_W_UNK
ORDER BY period, org_unit, gender;
