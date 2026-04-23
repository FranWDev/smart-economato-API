Write-Host "Deteniendo contenedores y liberando vol$([char]0xFA)menes..." -ForegroundColor Yellow
docker compose -p smart-economato-api down -v --remove-orphans 2>$null

$projectPrefix = "turing-backend"
$vols = @(
    "${projectPrefix}_postgres-data",
    "${projectPrefix}_postgres-replica-data",
    "${projectPrefix}_redis-data",
    "${projectPrefix}_kafka-data",
    "${projectPrefix}_prometheus-data",
    "${projectPrefix}_grafana-data",
    "${projectPrefix}_predictor-outbox-data",
    "${projectPrefix}_uploads-data"
)

Write-Host "Borrando volumenes..." -ForegroundColor Yellow
foreach ($v in $vols) {
    docker volume rm $v -f 2>$null
}

Write-Host "Borrando archivos de configuracion..." -ForegroundColor Yellow
Remove-Item ".env" -ErrorAction SilentlyContinue
Remove-Item "nginx/certs/*" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "smart-economato.log" -ErrorAction SilentlyContinue

$desktop = [Environment]::GetFolderPath('Desktop')
Remove-Item (Join-Path $desktop "Smart Economato.lnk") -ErrorAction SilentlyContinue

Write-Host "Eliminando tarea programada..." -ForegroundColor Yellow
Unregister-ScheduledTask -TaskName "SmartEconomatoBackend" -Confirm:$false -ErrorAction SilentlyContinue

Write-Host "---------------------------------------------------------------"
Write-Host " SISTEMA RESETEADO COMPLETAMENTE" -ForegroundColor Green
Write-Host " Ahora puedes ejecutar .\install.ps1 como si fuera la primera vez."
Write-Host "---------------------------------------------------------------"
