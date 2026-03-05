SELECT
  'GqKaojXhPdg' AS data_set,
  TO_CHAR(CAST(obs.obs_date AS TIMESTAMP), 'YYYYMM') AS period,
  COUNT(*) AS tests_performed,
  COUNT(CASE WHEN obs.val_quantity > 18.0 THEN 1 END) AS tests_positive
FROM diagnostic_report_flat dr
JOIN observation_flat obs ON dr.result_obs_id = obs.id
JOIN patient_flat p ON dr.patient_id = p.id
WHERE obs.obs_date > :#lastSyncTimestamp
  AND dr.code_code = '718-7'
  AND p.org_unit = 'ImspTQPwCqd'
GROUP BY TO_CHAR(CAST(obs.obs_date AS TIMESTAMP), 'YYYYMM')
ORDER BY period
