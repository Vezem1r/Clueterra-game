\set ON_ERROR_STOP on
\getenv budget_password BUDGET_DB_PASSWORD

BEGIN;

SELECT 'CREATE ROLE clueterra_budget'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'clueterra_budget')
\gexec

ALTER ROLE clueterra_budget WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS
    PASSWORD :'budget_password';

SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO clueterra_budget', current_database())
\gexec

GRANT USAGE ON SCHEMA cl_budget_schema, cl_budget_staging, cl_media_schema TO clueterra_budget;
GRANT USAGE, CREATE ON SCHEMA cl_budget_sch_schema TO clueterra_budget;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cl_budget_schema, cl_budget_staging TO clueterra_budget;
ALTER DEFAULT PRIVILEGES FOR ROLE clueterra IN SCHEMA cl_budget_schema, cl_budget_staging
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO clueterra_budget;

GRANT SELECT ON cl_media_schema.image_delivery TO clueterra_budget;
GRANT SELECT, INSERT, UPDATE ON
    cl_media_schema.image_resource,
    cl_media_schema.image_rendition,
    cl_media_schema.image_rights_revision
TO clueterra_budget;

SELECT format('ALTER %s %I.%I OWNER TO clueterra_budget',
              CASE c.relkind WHEN 'v' THEN 'VIEW' ELSE 'TABLE' END, n.nspname, c.relname)
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'cl_budget_sch_schema'
  AND c.relkind IN ('r', 'v')
  AND c.relowner <> 'clueterra_budget'::regrole
\gexec

COMMIT;
