$ErrorActionPreference = "Stop"

$DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { "clueterra" }
$DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "clueterra" }

Write-Host "Dropping and recreating BudgetGuessr schemas..."

$env:PGPASSWORD = $env:DB_PASSWORD
$sql = @"
DROP SCHEMA IF EXISTS cl_budget_schema CASCADE;
DROP SCHEMA IF EXISTS cl_budget_staging CASCADE;
DROP SCHEMA IF EXISTS cl_budget_sch_schema CASCADE;
CREATE SCHEMA cl_budget_schema;
CREATE SCHEMA cl_budget_staging;
CREATE SCHEMA cl_budget_sch_schema;
"@

$sql | psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME

Write-Host "BudgetGuessr schemas recreated. Running migrations..."

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$ScriptDir\migrate.ps1"

Write-Host "Granting BudgetGuessr database role..."
if (-not $env:BUDGET_DB_PASSWORD) {
    throw "BUDGET_DB_PASSWORD must be set"
}
$RoleScript = Join-Path $ScriptDir "..\..\..\scripts\db\budget-role.sql"
psql -X -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f $RoleScript
if ($LASTEXITCODE -ne 0) {
    throw "Budget role grant failed with exit code $LASTEXITCODE"
}
