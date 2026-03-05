SELECT
  'GqKaojXhPdg' AS data_set,
  TO_CHAR(event_date, 'YYYYMM') AS period,
  SUM(HIV_TEST_M) AS tests_performed,
  SUM(HIV_TEST_POS_M) AS tests_positive
FROM hiv_patient_events
WHERE event_date > :#lastSyncTimestamp AND org_unit = 'DiszpKrYNg8'
GROUP BY TO_CHAR(event_date, 'YYYYMM')
ORDER BY period
