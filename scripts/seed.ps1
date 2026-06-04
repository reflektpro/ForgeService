# Run this after first start or to reset demo data
$serverDir = Join-Path $PSScriptRoot "..\server"
Set-Location $serverDir

& ".\venv\Scripts\Activate.ps1"
$env:PYTHONPATH = "."
python seed.py

Write-Host ""
Write-Host "Demo data loaded. You can now start the server." -ForegroundColor Green