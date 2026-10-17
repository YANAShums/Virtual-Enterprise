param([switch]$SkipBuild,[switch]$SkipUnitTests,[switch]$SkipRestore)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
New-Item -ItemType Directory -Force output/ml-acceptance | Out-Null
$python=if(Test-Path .venv-ml/Scripts/python.exe){(Resolve-Path .venv-ml/Scripts/python.exe).Path}else{'python'}
function Invoke-Checked([string]$Name,[scriptblock]$Action) {
    Write-Host "CHECK: $Name"
    & $Action
    if($LASTEXITCODE -ne 0){throw "Failed: $Name (exit $LASTEXITCODE)"}
}
if(-not $SkipUnitTests) {
    Invoke-Checked 'Java tests' {mvn -B test}
    $env:PYTHONPATH='attrition-ml-service'
    Invoke-Checked 'ML tests' {& $python -m pytest attrition-ml-service/tests -q}
}
if(-not $SkipBuild) {
    Invoke-Checked 'Acceptance JARs' {& ./tools/prepare-acceptance.ps1}
    Invoke-Checked 'Isolated stack' {docker compose --env-file .env.example -p flowstaff-ml-check -f docker-compose.yml -f tools/compose.ml-acceptance.yml up -d --build --wait mysql workload-service enterprise-app attrition-ml-service}
}
Invoke-Checked 'ML integration' {& $python tools/verify-ml-integration.py}
Invoke-Checked 'Admin models' {& $python tools/verify-improvement-stage1.py}
Invoke-Checked 'Feature completeness' {& $python tools/verify-improvement-stage2.py}
Invoke-Checked 'Employee events and ML outage' {& $python tools/verify-improvement-stage3.py}
Invoke-Checked 'Quality report' {& $python tools/verify-improvement-stage4.py}
Invoke-Checked 'Lifecycle and rollback' {& $python tools/verify-improvement-stage5.py}
Invoke-Checked 'Tasks and workload outage' {& $python tools/verify-improvement-stage6.py}
$env:STAGE2_TEST_URL='http://localhost:18080'
Invoke-Checked 'Administrative CRUD' {& $python tools/verify-stage2.py}
Invoke-Checked 'Saved forecast during ML outage' {& $python tools/verify-ml-fallback.py}
if(-not $SkipRestore){Invoke-Checked 'Backup restore drill' {& $python tools/verify-restore-acceptance.py}}
Write-Host 'PASS: MVP acceptance completed'
