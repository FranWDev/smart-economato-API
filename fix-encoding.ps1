# Script para corregir la codificación de archivos a UTF-8 con BOM
# Esto soluciona los problemas de visualización de tildes y caracteres especiales en PowerShell 5.1

$filesToFix = @("install.ps1")

foreach ($file in $filesToFix) {
    if (Test-Path $file) {
        Write-Host "Corrigiendo codificación para: $file" -ForegroundColor Cyan
        
        # Leemos el contenido asumiendo que actualmente está en UTF-8 (sin BOM)
        $content = Get-Content -Path $file -Raw -Encoding UTF8
        
        # Al escribir usando [System.Text.Encoding]::UTF8 en .NET, automáticamente incluye la marca BOM
        [System.IO.File]::WriteAllText((Resolve-Path $file).Path, $content, [System.Text.Encoding]::UTF8)
        
        Write-Host "  -> Listo. $file guardado con UTF-8 BOM." -ForegroundColor Green
    } else {
        Write-Host "Archivo no encontrado: $file" -ForegroundColor Red
    }
}
