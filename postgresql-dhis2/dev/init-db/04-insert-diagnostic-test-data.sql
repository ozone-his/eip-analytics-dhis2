-- Insert test patient
INSERT INTO patient_flat (id, gender, org_unit) VALUES 
  ('0f35c45b-4419-4e89-a63b-3118e0720bb0', 'M', 'ImspTQPwCqd'),
  ('1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p', 'F', 'DiszpKrYNg8'),
  ('2b3c4d5e-6f7g-8h9i-0j1k-2l3m4n5o6p7q', 'M', 'DiszpKrYNg8');

-- Insert test diagnostic reports for March 2026
-- Report 1: Hemoglobin test (code 718-7) at Facility A
INSERT INTO diagnostic_report_flat (id, patient_id, encounter_id, org_unit, status, code_code, code_sys, code_display, result_obs_id)
VALUES 
  ('08f2c350-2250-4212-b85a-3e3d07a3ed5a', '0f35c45b-4419-4e89-a63b-3118e0720bb0', NULL, 'ImspTQPwCqd', 'final', '718-7', 'http://loinc.org', 'Hemoglobin', 'cec1bb72-d0b5-4a06-8d60-b1ab1a4d22de'),
  ('18f2c350-2250-4212-b85a-3e3d07a3ed5b', '1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p', NULL, 'DiszpKrYNg8', 'final', '718-7', 'http://loinc.org', 'Hemoglobin', 'dfd1bb72-d0b5-4a06-8d60-b1ab1a4d22df'),
  ('28f2c350-2250-4212-b85a-3e3d07a3ed5c', '2b3c4d5e-6f7g-8h9i-0j1k-2l3m4n5o6p7q', NULL, 'DiszpKrYNg8', 'final', '718-7', 'http://loinc.org', 'Hemoglobin', 'efe1bb72-d0b5-4a06-8d60-b1ab1a4d22eg');

-- Insert test observations (Hemoglobin values)
-- For sync testing: values > 18.0 count as "positive" in the aggregation
INSERT INTO observation_flat (id, patient_id, status, obs_date, val_quantity, val_quantity_unit, code_code, code_sys, code_display)
VALUES
  -- Facility A (ImspTQPwCqd) tests in February 2026 - 4 tests
  ('cec1bb72-d0b5-4a06-8d60-b1ab1a4d22de', '0f35c45b-4419-4e89-a63b-3118e0720bb0', 'final', '2026-02-01 08:00:00', 19.5, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  ('d0d2cc73-e1c6-4b07-9e61-b2bc1b5e33ef', '0f35c45b-4419-4e89-a63b-3118e0720bb0', 'final', '2026-02-02 09:30:00', 17.2, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  ('e1e3dd74-f2d7-4c08-af72-c3cd2c6f44f0', '0f35c45b-4419-4e89-a63b-3118e0720bb0', 'final', '2026-02-03 10:15:00', 20.1, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  ('f2f4ee85-g3e8-4d09-bg83-d4de3d7g55g1', '0f35c45b-4419-4e89-a63b-3118e0720bb0', 'final', '2026-02-05 11:45:00', 16.8, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  
  -- Facility B (DiszpKrYNg8) tests in February 2026 - 3 tests
  ('dfd1bb72-d0b5-4a06-8d60-b1ab1a4d22df', '1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p', 'final', '2026-02-01 14:20:00', 21.3, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  ('eae2cc83-e6d7-5d17-ae73-c2cd3e7g33dg', '1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p', 'final', '2026-02-04 15:00:00', 18.9, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin'),
  ('fbf3dd94-f7e8-5e18-bf84-d3de3f8h44eh', '2b3c4d5e-6f7g-8h9i-0j1k-2l3m4n5o6p7q', 'final', '2026-02-05 16:30:00', 15.4, 'g/dl', '718-7', 'http://loinc.org', 'Hemoglobin');

-- Expected aggregation results after sync:
-- Period 202602:
--   Facility A (ImspTQPwCqd): 4 tests, 2 positive (>18.0: 19.5, 20.1)
--   Facility B (DiszpKrYNg8): 3 tests, 2 positive (>18.0: 21.3, 18.9)
