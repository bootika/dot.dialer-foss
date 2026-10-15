param(
    [Parameter(Mandatory)][string]$Destination,
    [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent)
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$targetRoot = [IO.Path]::GetFullPath($Destination)
if ($targetRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $targetRoot -eq $sourceRoot) {
    throw 'Use a separate, empty destination outside the source checkout.'
}
if ((Test-Path -LiteralPath $targetRoot) -and @(Get-ChildItem -LiteralPath $targetRoot -Force).Count) {
    throw 'Destination must be empty. Existing files are never overwritten or removed.'
}
$paths = @(& git -C $sourceRoot ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the reviewed Git file inventory.' }
$rootFiles = @('.gitignore', 'README.md', 'UPSTREAM.md', 'LICENSE', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat')
$tools = @('tools/branding/BrandAssetGenerator.java', 'tools/check-private-documentation.ps1', 'tools/export-public-source.ps1')
$selected = @($paths | Where-Object {
    $_ -in $rootFiles -or $_ -in $tools -or
    $_ -eq '.github/workflows/android.yml' -or
    $_ -match '^(app|core|config|gradle|LICENSES)/' -or
    $_ -match '^branding/[^/]+\.svg$' -or $_ -eq 'branding/dot-dialer-phone-mark-approved.png'
} | Where-Object {
    $_ -notmatch '(^|/)(build|evidence|docs|node_modules|\.git|\.secrets)/' -and
    $_ -notmatch '(^|/)(AGENTS\.md|local\.properties|\.env(?:\..*)?)$' -and
    $_ -notmatch '\.(jks|keystore|p12|pfx|pem|apk|aab)$' -and
    ($_ -notmatch '\.md$' -or $_ -in @('README.md', 'UPSTREAM.md') -or $_ -like 'LICENSES/*')
} | Sort-Object -Unique)
if (!$selected.Count) { throw 'No source files selected.' }
New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null
foreach ($relative in $selected) {
    $from = Join-Path $sourceRoot $relative
    $to = Join-Path $targetRoot $relative
    if (!(Test-Path -LiteralPath $from -PathType Leaf)) { throw "Missing source: $relative" }
    if ((Get-Item -LiteralPath $from).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Symlink not allowed: $relative" }
    New-Item -ItemType Directory -Path (Split-Path $to -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $from -Destination $to
    if ((Get-FileHash -LiteralPath $from).Hash -ne (Get-FileHash -LiteralPath $to).Hash) {
        throw "Copy verification failed: $relative"
    }
}
Write-Host "Exported and hash-verified $($selected.Count) reviewed source files."
