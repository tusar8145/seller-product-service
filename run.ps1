# run.ps1 — build (optional) and run Seller Product Service on Windows
$ErrorActionPreference = "Stop"

# Always work relative to this script's folder
Set-Location -Path $PSScriptRoot

if (-not (Test-Path ".env.dev")) {
    Write-Host ".env.dev not found!" -ForegroundColor Red
    exit 1
}

Write-Host "Loading environment from .env.dev" -ForegroundColor Cyan

# Read .env.dev line by line and inject into this process
Get-Content ".env.dev" | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $kv = $line -split "=", 2
    if ($kv.Length -ne 2) { return }

    $key = $kv[0].Trim()
    $val = $kv[1].Trim().Trim([char]34)   # strip optional double quotes

    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
}

$skipBuild = $args -contains "--skip-build"

if (-not $skipBuild) {
    Write-Host "Building project (skip tests)..." -ForegroundColor Cyan
    if (Test-Path ".\mvnw.cmd") {
        .\mvnw.cmd -q -DskipTests clean package
    } else {
        mvn -q -DskipTests clean package
    }
}

$jar = Get-ChildItem ".\target\*.jar" |
       Where-Object { $_.Name -notmatch "original" } |
       Select-Object -First 1

if (-not $jar) {
    Write-Host "No jar found in .\target" -ForegroundColor Red
    exit 1
}

$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8081" }

Write-Host ("Starting: " + $jar.FullName + " on port " + $port) -ForegroundColor Green

java -jar $jar.FullName --spring.profiles.active=dev --server.port=$port