SELECT
  'BfMAe6Itzgt' AS data_set,
  TO_CHAR(test_date, 'YYYYMM') AS period,
  SUM(CASE WHEN test_result = 'positive' THEN test_count ELSE 0 END) AS positive_tests,
  SUM(CASE WHEN test_result = 'negative' THEN test_count ELSE 0 END) AS negative_tests,
  SUM(CASE WHEN test_result = 'positive' AND patient_gender = 'M' THEN test_count ELSE 0 END) AS positive_male,
  SUM(CASE WHEN test_result = 'positive' AND patient_gender = 'F' THEN test_count ELSE 0 END) AS positive_female,
  SUM(test_count) AS total_tests
FROM lims_test_results
WHERE test_date > :#lastSyncTimestamp AND facility_id = 'FACILITY_B'
GROUP BY TO_CHAR(test_date, 'YYYYMM')
ORDER BY period
