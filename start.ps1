Set-Location $PSScriptRoot
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Создан файл .env из .env.example"
}
docker compose up --build
