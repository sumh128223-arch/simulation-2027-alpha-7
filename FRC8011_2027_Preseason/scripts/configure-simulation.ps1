param(
    [string]$WpilibHome,
    [switch]$ConfigureAdvantageScope,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($WpilibHome)) {
    if ([string]::IsNullOrWhiteSpace($env:PUBLIC)) {
        throw 'Pass -WpilibHome with your WPILib 2027_alpha7 installation directory.'
    }
    $WpilibHome = Join-Path $env:PUBLIC 'wpilib\2027_alpha7'
}
$jdkPath = Join-Path $WpilibHome 'jdk'
$javaExe = Join-Path $jdkPath 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw 'Java was not found. Install WPILib 2027_alpha7 or pass -WpilibHome.'
}
$javaVersion = (& $javaExe --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(?:openjdk|java) 25(?:[.\s]|$)') {
    throw 'The project requires Java 25. Check the JDK in -WpilibHome.'
}
$assetsPath = Join-Path $projectRoot 'ascope\assets'
if (-not (Test-Path -LiteralPath (Join-Path $assetsPath 'Robot_8011\config.json'))) {
    throw 'Robot assets are missing. Extract the full source package.'
}

$prefsPath = $null
if ($ConfigureAdvantageScope) {
    if ([string]::IsNullOrWhiteSpace($env:APPDATA)) { throw 'APPDATA is unavailable.' }
    $prefsPath = Join-Path $env:APPDATA 'AdvantageScope\prefs.json'
    if (-not (Test-Path -LiteralPath $prefsPath)) {
        throw 'Open AdvantageScope once, then exit it before running this option.'
    }
    if (Get-Process | Where-Object { $_.ProcessName -match 'AdvantageScope' }) {
        throw 'Exit all AdvantageScope windows before configuring its asset directory.'
    }
    $prefs = Get-Content -LiteralPath $prefsPath -Raw | ConvertFrom-Json
}

Write-Output 'Java 25 found.'
Write-Output ('Robot asset directory: ' + $assetsPath)
if ($CheckOnly) {
    Write-Output 'Check only: no files changed.'
    return
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$properties = '# Local machine configuration. Do not commit this file.' + "`n" +
    'org.gradle.java.home=' + $jdkPath.Replace('\', '/') + "`n"
[IO.File]::WriteAllText((Join-Path $projectRoot 'gradle.properties'), $properties, $utf8)
if ($ConfigureAdvantageScope) {
    $backupPath = $prefsPath + '.before-8011-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.bak'
    Copy-Item -LiteralPath $prefsPath -Destination $backupPath
    $prefs | Add-Member -NotePropertyName userAssetsFolder -NotePropertyValue $assetsPath -Force
    [IO.File]::WriteAllText($prefsPath, ($prefs | ConvertTo-Json -Depth 20), $utf8)
    Write-Output 'AdvantageScope preferences backed up and asset directory configured.'
}
Write-Output 'Ready: open this project in WPILib VS Code and run Simulate Robot Code.'
