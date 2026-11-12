$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$BackendDir = Split-Path -Parent $ProjectDir

Write-Host "Running Flyway migrate for clueterra-questions..."

$DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { "clueterra" }
$DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "clueterra" }
$DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "clueterra_secret" }

Push-Location $ProjectDir
try {
  & "$BackendDir\mvnw.cmd" -f "$ProjectDir\pom.xml" flyway:migrate `
    "-Dflyway.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" `
    "-Dflyway.user=${DB_USER}" `
    "-Dflyway.password=${DB_PASSWORD}" `
    "-Dflyway.schemas=cl_schema,cl_sch_schema,cl_staging" `
    "-Dflyway.createSchemas=true" `
    "-Dflyway.locations=filesystem:src/main/resources/db/migration" `
    "-Dflyway.baselineOnMigrate=true"
} finally {
  Pop-Location
}

Write-Host "Questions migration complete."
