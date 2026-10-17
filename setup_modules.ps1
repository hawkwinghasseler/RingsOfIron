$ErrorActionPreference = "Stop"

$modules = @(
    "core-engine",
    "physics-exterior",
    "interior-grid",
    "ai-crew",
    "macro-sim",
    "combat-sys",
    "ui-hud",
    "audio-spatial",
    "modding-data"
)

$deps = @{
    "core-engine" = @()
    "modding-data" = @("core-engine")
    "physics-exterior" = @("core-engine")
    "interior-grid" = @("core-engine")
    "macro-sim" = @("core-engine", "physics-exterior")
    "ai-crew" = @("core-engine", "interior-grid")
    "ui-hud" = @("core-engine", "physics-exterior", "interior-grid")
    "audio-spatial" = @("core-engine", "physics-exterior", "interior-grid")
    "combat-sys" = @("core-engine", "physics-exterior", "interior-grid")
}

Write-Host "Creating directories and build.gradle files..."
foreach ($mod in $modules) {
    $pkgName = $mod.Replace('-', '')
    $dir = "D:\Code\Java\RingsOfIron\$mod\src\main\java\com\ringsofiron\$pkgName"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    
    $buildFile = "D:\Code\Java\RingsOfIron\$mod\build.gradle"
    $content = "[compileJava, compileTestJava]*.options*.encoding = 'UTF-8'`r`n"
    $content += "eclipse.project.name = appName + '-$mod'`r`n`r`n"
    $content += "dependencies {`r`n"
    
    $content += "  api `"com.badlogicgames.gdx:gdx:`$gdxVersion`"`r`n"
    
    if ($mod -eq "physics-exterior" -or $mod -eq "combat-sys") {
        $content += "  api `"com.badlogicgames.gdx:gdx-box2d:`$gdxVersion`"`r`n"
    }
    if ($mod -eq "ai-crew") {
        $content += "  api `"com.badlogicgames.gdx:gdx-ai:`$aiVersion`"`r`n"
    }
    if ($mod -eq "modding-data") {
        $content += "  api `"org.apache.groovy:groovy-all:`$groovyVersion`"`r`n"
    }
    
    foreach ($dep in $deps[$mod]) {
        $content += "  api project(':$dep')`r`n"
    }
    
    $content += "}`r`n"
    Set-Content -Path $buildFile -Value $content
}

Write-Host "Updating settings.gradle..."
$settingsPath = "D:\Code\Java\RingsOfIron\settings.gradle"
$settingsContent = Get-Content -Path $settingsPath -Raw
$includeLine = "include 'lwjgl3', 'core', '" + ($modules -join "', '") + "'"
$settingsContent = $settingsContent -replace "include 'lwjgl3', 'core'", $includeLine
Set-Content -Path $settingsPath -Value $settingsContent

Write-Host "Updating core/build.gradle..."
$coreBuildPath = "D:\Code\Java\RingsOfIron\core\build.gradle"
$coreBuildContent = Get-Content -Path $coreBuildPath -Raw
$coreDeps = ""
foreach ($mod in $modules) {
    $coreDeps += "  api project(':$mod')`r`n"
}
# Using regex to insert dependencies right after dependencies {
$coreBuildContent = [regex]::Replace($coreBuildContent, "(?s)dependencies \{", "dependencies {`r`n$coreDeps", 1)
Set-Content -Path $coreBuildPath -Value $coreBuildContent

Write-Host "Project structure overhaul complete."
