SELECT cl_budget_schema.start_sync_run(
               :sourceCode,
               NOW() - CAST(
                       :staleTimeout AS INTERVAL
                       )
       ) AS run_id
