param(
    [string]$SourceJar = "K:\mymod\build\libs\bettercode-1.0.9.jar",
    [string]$DestJar = "$env:APPDATA\.minecraft\mods\bettercode-1.0.9.jar",
    [int]$PollMs = 1500,
    [string]$LogFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-DeployLog([string]$msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Write-Host $line
    if ($LogFile -and $LogFile.Trim().Length -gt 0) {
        try {
            $dir = Split-Path -Parent $LogFile
            if ($dir -and -not (Test-Path -LiteralPath $dir)) {
                New-Item -ItemType Directory -Path $dir -Force | Out-Null
            }
            Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
        }
        catch { }
    }
}

if (-not (Test-Path -LiteralPath $SourceJar)) {
    Write-DeployLog "Source jar not found: $SourceJar"
    exit 1
}

function Test-MinecraftRunning {
    # Fast check: visible client window.
    $withWindow = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object {
        (($_.MainWindowTitle -as [string]) -match "Minecraft")
    }
    if ($withWindow) {
        return $true
    }

    # Fallback check: detect actual game JVM only (not launcher/updater JVMs).
    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop
        foreach ($p in $procs) {
            $cmd = ($p.CommandLine -as [string])
            if (-not $cmd) {
                continue
            }
            $isLauncherLike = ($cmd -match "org\\.tlauncher\\.tlauncher\\.rmo\\.TLauncher") -or
                              ($cmd -match "LabyMod\\\\Updater\\.jar")
            if ($isLauncherLike) {
                continue
            }
            $isGameJvm = ($cmd -match "net\\.minecraft\\.client\\.main\\.Main") -or
                         ($cmd -match "net\\.minecraft\\.launchwrapper\\.Launch") -or
                         ($cmd -match "(^|\\s)--gameDir(\\s|=)") -or
                         ($cmd -match "(^|\\s)--assetsDir(\\s|=)")
            if ($isGameJvm) {
                return $true
            }
        }
    }
    catch {
        # Ignore and rely on window-based detection only.
    }

    return $false
}

while ($true) {
    if (-not (Test-MinecraftRunning)) {
        break
    }
    Write-DeployLog "Waiting Minecraft close..."
    Start-Sleep -Milliseconds $PollMs
}

try {
    $destDir = Split-Path -Parent $DestJar
    if ($destDir -and -not (Test-Path -LiteralPath $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
}
catch {
    Write-DeployLog "Failed to ensure destination directory: $DestJar"
    exit 1
}

while ($true) {
    try {
        Copy-Item -LiteralPath $SourceJar -Destination $DestJar -Force -ErrorAction Stop
        Write-DeployLog "Copied: $SourceJar -> $DestJar"
        exit 0
    }
    catch {
        Write-DeployLog "Copy failed (will retry): $($_.Exception.Message)"
        Start-Sleep -Milliseconds $PollMs
    }
}
