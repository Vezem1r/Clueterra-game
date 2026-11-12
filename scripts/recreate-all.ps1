$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

Write-Host "=== Recreating all schemas ==="

& "$RootDir\backend\clueterra-questions\scripts\recreate-db.ps1"
& "$RootDir\backend\clueterra-budget\scripts\recreate-db.ps1"
& "$RootDir\backend\clueterra-api\scripts\recreate-db.ps1"

Write-Host "=== All schemas recreated ==="
