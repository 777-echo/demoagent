# Minimal AI Agent - Web UI launcher
# Opens http://localhost:8080 in your browser

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

if (-not $env:OPENAI_API_KEY) {
    Write-Host "[ERROR] OPENAI_API_KEY not set."
    exit 1
}

# Auto-detect jackson jars
$libDir = "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core"
$jars = @(
    (Get-ChildItem "$libDir\jackson-databind\*\*.jar" | Select-Object -Last 1).FullName,
    (Get-ChildItem "$libDir\jackson-core\*\*.jar" | Select-Object -Last 1).FullName,
    (Get-ChildItem "$libDir\jackson-annotations\*\*.jar" | Select-Object -Last 1).FullName
)
$cp = "target/classes;" + ($jars -join ";")

Write-Host "Starting Web UI at http://localhost:8080 ..."
Start-Process "http://localhost:8080"
java --add-modules jdk.httpserver -cp $cp com.example.WebServer
