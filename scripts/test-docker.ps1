# Smoke test nhanh cho docker compose stack (mysql, redis, app).
# Kiem tra container dang chay + healthy + tra loi dung.
#
# Cach dung:
#   .\scripts\test-docker.ps1
#   .\scripts\test-docker.ps1 -AppPort 8080

param(
    [int]$AppPort = 8081
)

$ErrorActionPreference = "Continue"
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    $dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
    if (Test-Path $dockerBin) { $env:Path += ";$dockerBin" }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
Push-Location $repoRoot

$results = @()

function Test-Step($name, [scriptblock]$block) {
    try {
        $out = & $block
        $script:results += [pscustomobject]@{ Test = $name; Status = "OK"; Detail = ($out | Out-String).Trim() }
    } catch {
        $script:results += [pscustomobject]@{ Test = $name; Status = "FAIL"; Detail = $_.Exception.Message }
    }
}

Test-Step "1. Container status" {
    $ps = docker compose ps --format json | ConvertFrom-Json
    if (-not $ps) { throw "Khong co container nao dang chay. Chay: docker compose up -d" }
    $ps | ForEach-Object { "$($_.Service): $($_.State) ($($_.Status))" }
}

Test-Step "2. MySQL ping" {
    $r = docker compose exec -T mysql mysqladmin ping -h localhost -uroot -proot 2>&1
    if ($LASTEXITCODE -ne 0) { throw $r }
    $r
}

Test-Step "3. Redis ping" {
    $r = docker compose exec -T redis redis-cli ping 2>&1
    if ($r -notmatch "PONG") { throw $r }
    $r
}

Test-Step "4. MySQL - dem so room/poi" {
    $r = "SELECT (SELECT COUNT(*) FROM room) AS rooms, (SELECT COUNT(*) FROM poi) AS pois;" |
        docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 roomfinder 2>&1
    if ($LASTEXITCODE -ne 0) { throw $r }
    $r
}

Test-Step "5. App health endpoint (host)" {
    $resp = Invoke-RestMethod -Uri "http://localhost:$AppPort/api/v1/chat/health" -Method Get -TimeoutSec 5
    if ($resp.status -ne "UP") { throw "status != UP: $($resp | ConvertTo-Json -Compress)" }
    "status=$($resp.status) (port $AppPort)"
}

Test-Step "6. App -> chat end-to-end" {
    $body = '{"session_id":"docker-smoke-test","message":"Tim phong duoi 3 trieu"}'
    $resp = Invoke-RestMethod -Uri "http://localhost:$AppPort/api/v1/chat" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 15
    if (-not $resp.reply) { throw "Khong co truong 'reply' trong response" }
    "intent=$($resp.intent) so_phong=$($resp.rooms.Count) path=$($resp.meta.path)"
}

Pop-Location

Write-Host ""
$results | ForEach-Object {
    $color = if ($_.Status -eq "OK") { "Green" } else { "Red" }
    Write-Host "[$($_.Status)] $($_.Test)" -ForegroundColor $color
    if ($_.Detail) {
        $indented = ($_.Detail -split "`n" | ForEach-Object { "    $_" }) -join "`n"
        Write-Host $indented -ForegroundColor DarkGray
    }
}

$failed = $results | Where-Object { $_.Status -eq "FAIL" }
if ($failed) {
    Write-Host "`n$($failed.Count) test THAT BAI." -ForegroundColor Red
    exit 1
} else {
    Write-Host "`nTat ca $($results.Count) test PASS." -ForegroundColor Green
}
