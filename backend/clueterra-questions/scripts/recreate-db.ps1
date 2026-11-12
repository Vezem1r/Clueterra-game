$ErrorActionPreference = "Stop"

$DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { "clueterra" }
$DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "clueterra" }

Write-Host "Dropping and recreating questions schemas..."

$env:PGPASSWORD = $env:DB_PASSWORD
$sql = @"
DROP SCHEMA IF EXISTS cl_schema CASCADE;
DROP SCHEMA IF EXISTS cl_sch_schema CASCADE;
DROP SCHEMA IF EXISTS cl_staging CASCADE;
CREATE SCHEMA cl_schema;
CREATE SCHEMA cl_sch_schema;
CREATE SCHEMA cl_staging;
"@

$sql | psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME

Write-Host "Questions schemas recreated. Running migrations..."

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$ScriptDir\migrate.ps1"
