# PowerShell script to run tests on Windows

param(
    [string]$TestType = "all",
    [switch]$Coverage = $true,
    [switch]$Verbose = $true
)

Write-Host "Running backend tests..." -ForegroundColor Green

# Ensure we're in the correct directory
$BackendDir = Split-Path $MyInvocation.MyCommand.Path -Parent
Set-Location $BackendDir

# Install test dependencies if needed
Write-Host "Installing test dependencies..." -ForegroundColor Yellow
uv sync --extra test

# Build test command
$TestCommand = "uv run pytest"

# Add coverage if requested
if ($Coverage) {
    $TestCommand += " --cov=app --cov-report=html --cov-report=term"
}

# Add verbose if requested
if ($Verbose) {
    $TestCommand += " -v"
}

# Add test type filter
switch ($TestType) {
    "unit" { $TestCommand += " -m unit" }
    "integration" { $TestCommand += " -m integration" }
    "user" { $TestCommand += " tests/api/v1/endpoints/test_user.py" }
    "all" { 
        # Run all tests - no additional filter needed
    }
    default { 
        Write-Host "Invalid test type. Use: all, unit, integration, user" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Executing: $TestCommand" -ForegroundColor Cyan

# Run the tests
Invoke-Expression $TestCommand

$ExitCode = $LASTEXITCODE

if ($ExitCode -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
} else {
    Write-Host "Some tests failed!" -ForegroundColor Red
}

exit $ExitCode
