Write-Host "Deteniendo contenedores y limpiando red..." -ForegroundColor Yellow
docker compose down -v 2>$null

$vols = @(
    "turing-backend_postgres-data",
    "turing-backend_postgres-replica-data",
    "turing-backend_redis-data",
    "turing-backend_kafka-data",
    "turing-backend_prometheus-data",
    "turing-backend_grafana-data",
    "turing-backend_predictor-outbox-data",
    "turing-backend_uploads-data"
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
