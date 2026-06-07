# Minimal AI Agent launcher
# Usage: .\run.ps1
#        .\run.ps1 --once "what is AI Agent"

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

if (-not $env:OPENAI_API_KEY) {
    Write-Host "[ERROR] OPENAI_API_KEY not set. Please set it first:"
    Write-Host '  $env:OPENAI_API_KEY="sk-your-key"'
    exit 1
}

# Build if needed
if (-not (Test-Path "target/classes")) {
    Write-Host "Building..."
    mvn compile -q
}

# Auto-detect jackson jars
$libDir = "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core"
$jars = @(
    (Get-ChildItem "$libDir\jackson-databind\*\*.jar" | Select-Object -Last 1).FullName,
    (Get-ChildItem "$libDir\jackson-core\*\*.jar" | Select-Object -Last 1).FullName,
    (Get-ChildItem "$libDir\jackson-annotations\*\*.jar" | Select-Object -Last 1).FullName
)
$cp = "target/classes;" + ($jars -join ";")

java -cp $cp com.example.Main @args
