$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$jdk21Home = "C:\Program Files\Java\jdk-21"

if (-not (Test-Path $jdk21Home)) {
    throw "JDK 21 not found at $jdk21Home"
}

$env:JAVA_HOME = $jdk21Home
$machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = "$env:JAVA_HOME\bin;$machinePath;$userPath"

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
java -version

$targetDir = Join-Path $repoRoot "target"
if (Test-Path $targetDir) {
    Write-Host "Removing stale build output: $targetDir"
    Remove-Item -Recurse -Force $targetDir
}

Set-Location $repoRoot
mvn spring-boot:run
