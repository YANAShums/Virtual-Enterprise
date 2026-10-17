$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & mvn -B package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    $artifactDir = Join-Path $PSScriptRoot 'acceptance-runtime'
    New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
    foreach ($module in @('enterprise-app', 'workload-service')) {
        $jar = Get-ChildItem -LiteralPath "$module/target" -Filter '*.jar' | Where-Object { $_.Name -notlike '*original*' } | Select-Object -First 1
        if (-not $jar) { throw "Missing jar for $module" }
        Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $artifactDir "$module.jar") -Force
    }
} finally { Pop-Location }
