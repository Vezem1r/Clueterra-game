$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $RootDir ".env"
$ComposeFile = Join-Path $RootDir "docker\docker-compose.yml"
$ComposeDir = Split-Path -Parent $ComposeFile

if (Test-Path -LiteralPath $EnvFile) {
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and !$line.StartsWith("#") -and $line -match "^([A-Za-z_][A-Za-z0-9_]*)=(.*)$") {
            $name = $Matches[1]
            $value = $Matches[2].Trim('"').Trim("'")
            if (![Environment]::GetEnvironmentVariable($name, "Process")) {
                [Environment]::SetEnvironmentVariable($name, $value, "Process")
            }
        }
    }
}

$required = @(
    "MINIO_ACCESS_KEY",
    "MINIO_SECRET_KEY",
    "MINIO_API_SECRET_KEY",
    "MINIO_QUESTIONS_SECRET_KEY",
    "MINIO_BUDGET_SECRET_KEY"
)

foreach ($name in $required) {
    if (![Environment]::GetEnvironmentVariable($name, "Process")) {
        throw "Missing required MinIO service credential: $name"
    }
}

if ($env:MINIO_ACCESS_KEY -in @("clueterra-api", "clueterra-questions", "clueterra-budget")) {
    throw "MinIO root access key must not reuse an application identity."
}

$secretKeys = @(
    $env:MINIO_API_SECRET_KEY,
    $env:MINIO_QUESTIONS_SECRET_KEY,
    $env:MINIO_BUDGET_SECRET_KEY
)
foreach ($secret in $secretKeys) {
    if ($secret.Length -lt 20 `
            -or $secret.StartsWith("replace-with-") `
            -or $secret -eq $env:MINIO_SECRET_KEY) {
        throw "Each MinIO service secret must be a unique random value of at least 20 characters."
    }
}
if (($secretKeys | Sort-Object -Unique).Count -ne $secretKeys.Count) {
    throw "Each application must use a distinct MinIO secret key."
}

docker compose --project-directory $ComposeDir -f $ComposeFile up -d minio
if ($LASTEXITCODE -ne 0) {
    throw "Unable to start MinIO."
}

$ready = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker compose --project-directory $ComposeDir -f $ComposeFile exec -T minio mc ready local *> $null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (!$ready) {
    throw "MinIO did not become ready in time."
}

docker compose --project-directory $ComposeDir -f $ComposeFile exec -T `
    -e "MINIO_API_SECRET_KEY=$env:MINIO_API_SECRET_KEY" `
    -e "MINIO_QUESTIONS_SECRET_KEY=$env:MINIO_QUESTIONS_SECRET_KEY" `
    -e "MINIO_BUDGET_SECRET_KEY=$env:MINIO_BUDGET_SECRET_KEY" `
    minio sh /opt/clueterra/minio/provision.sh

if ($LASTEXITCODE -ne 0) {
    throw "MinIO provisioning failed."
}
