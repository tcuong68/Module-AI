# Bơm dữ liệu test bổ sung (scripts/test-data-seed.sql) vào MySQL đang chạy
# trong docker compose (service "mysql"). Không đụng tới seed gốc data.sql.
#
# Yêu cầu: container mysql đã "up" (docker compose up -d mysql).
#
# Cách dùng:
#   .\scripts\seed-test-data.ps1
#   .\scripts\seed-test-data.ps1 -Clean   # chỉ xoá dữ liệu [TEST], không seed lại

param(
    [switch]$Clean,
    [string]$Service = "mysql",
    [string]$Database = "roomfinder",
    [string]$User = "root",
    [string]$Password = "root"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    $dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
    if (Test-Path $dockerBin) { $env:Path += ";$dockerBin" }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

Push-Location $repoRoot
try {
    $running = docker compose ps --status running --services 2>$null
    if (-not ($running -contains $Service)) {
        Write-Host "Container '$Service' chua chay. Dang start..." -ForegroundColor Yellow
        docker compose up -d $Service
        Start-Sleep -Seconds 3
    }

    if ($Clean) {
        $sql = "DELETE FROM room WHERE title LIKE '[TEST]%'; DELETE FROM poi WHERE name LIKE '[TEST]%'; SELECT ROW_COUNT() AS deleted;"
        $sql | docker compose exec -T $Service mysql "-u$User" "-p$Password" $Database
        if ($LASTEXITCODE -ne 0) { throw "mysql exec that bai (exit $LASTEXITCODE)" }
        Write-Host "Da xoa du lieu [TEST]." -ForegroundColor Green
    }
    else {
        $sqlFile = Join-Path $scriptDir "test-data-seed.sql"
        Get-Content -Raw $sqlFile | docker compose exec -T $Service mysql "-u$User" "-p$Password" $Database
        if ($LASTEXITCODE -ne 0) { throw "mysql exec that bai (exit $LASTEXITCODE)" }
        Write-Host "Da them du lieu test vao bang room/poi." -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
