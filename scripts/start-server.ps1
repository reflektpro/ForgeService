# ForgeService - Windows PowerShell launcher
# Run from project root: .\scripts\start-server.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   ForgeService — Автосервис Премиум" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$serverDir = Join-Path $PSScriptRoot "..\server"
Set-Location $serverDir

# Check for venv
if (-not (Test-Path "venv")) {
    Write-Host "Creating virtual environment..." -ForegroundColor Yellow
    python -m venv venv
}

Write-Host "Activating virtual environment..." -ForegroundColor Green
& ".\venv\Scripts\Activate.ps1"

Write-Host "Installing / updating dependencies..." -ForegroundColor Green
pip install -r requirements.txt --quiet

Write-Host "Setting PYTHONPATH for correct imports (from app.xxx)..." -ForegroundColor Green
$env:PYTHONPATH = "."

Write-Host ""
Write-Host "Starting ForgeService server..." -ForegroundColor Green
Write-Host "API docs will be available at: http://localhost:8000/docs" -ForegroundColor Magenta
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host ""

uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload