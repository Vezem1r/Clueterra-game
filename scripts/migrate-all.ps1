$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

Write-Host "=== Running all migrations ==="

try {
    & "$RootDir\backend\clueterra-questions\scripts\migrate.ps1"
    & "$RootDir\backend\clueterra-budget\scripts\migrate.ps1"
    & "$RootDir\backend\clueterra-api\scripts\migrate.ps1"

    Write-Host "=== All migrations complete ==="
} finally {
    Set-Location $ScriptDir
}
