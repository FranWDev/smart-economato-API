<#
.SYNOPSIS
Smart Economato - Panel de Control & Instalador para Windows Server
#>

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Elevación automática en Windows
if ($IsWindows) {
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "Solicitando permisos de Administrador..." -ForegroundColor Yellow
        Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
        exit
    }
}

# =============================================================================
# FUNCIONES DE UI Y UTILIDADES
# =============================================================================

function Write-Header {
    Clear-Host
    Write-Host "===============================================================" -ForegroundColor Cyan
    Write-Host "  ╔═╗╔╦╗╔═╗╦═╗╔╦╗  ╔═╗╔═╗╔═╗╔╗╔╔═╗╔╦╗╔═╗╔╦╗╔═╗" -ForegroundColor Cyan
    Write-Host "  ╚═╗║║║╠═╣╠╦╝ ║   ║╣ ║  ║ ║║║║║ ║║║║╠═╣ ║ ║ ║" -ForegroundColor Cyan
    Write-Host "  ╚═╝╩ ╩╩ ╩╩╚═ ╩   ╚═╝╚═╝╚═╝╝╚╝╚═╝╩ ╩╩ ╩ ╩ ╚═╝" -ForegroundColor Cyan
    Write-Host "===============================================================" -ForegroundColor Cyan
    Write-Host "       Panel de Control y Mantenimiento de Producción v2.0     " -ForegroundColor White
    Write-Host ""
}

function Write-Info { param([string]$msg); Write-Host "[ INFO ] " -NoNewline -ForegroundColor Cyan; Write-Host $msg }
function Write-Success { param([string]$msg); Write-Host "[  OK  ] " -NoNewline -ForegroundColor Green; Write-Host $msg }
function Write-Warn { param([string]$msg); Write-Host "[ WARN ] " -NoNewline -ForegroundColor Yellow; Write-Host $msg }
function Write-ErrorMsg { param([string]$msg); Write-Host "[ERROR ] " -NoNewline -ForegroundColor Red; Write-Host $msg }

function Generate-RandomString($length, $isPassword=$false) {
    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    if ($isPassword) { $chars += "@#%*()_+:?" }
    
    $bytes = New-Object Byte[] $length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    
    $result = ""
    foreach ($byte in $bytes) {
        $result += $chars[$byte % $chars.Length]
    }
    return $result
}

function Set-EnvSecret($envPath, $key, $length, $isComplex) {
    if (-not (Test-Path $envPath)) { New-Item -ItemType File -Path $envPath -Force | Out-Null }
    
    $exists = Select-String -Path $envPath -Pattern "^$key=" -Quiet
    if (-not $exists) {
        $val = Generate-RandomString $length $isComplex
        Add-Content -Path $envPath -Value "$key=$val"
    } else {
        $existingVal = (Select-String -Path $envPath -Pattern "^$key=(.*)").Matches.Groups[1].Value
        if ([string]::IsNullOrWhiteSpace($existingVal)) {
            $val = Generate-RandomString $length $isComplex
            (Get-Content $envPath) -replace "^$key=", "$key=$val" | Set-Content $envPath
        }
    }
}

function Pause-Execution {
    Write-Host "`nPresiona ENTER para continuar..." -ForegroundColor Cyan
    Read-Host
}

function Get-FreePort {
    param([int]$port)
    try {
        $properties = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties()
        $listeners = $properties.GetActiveTcpListeners()
        foreach ($listener in $listeners) {
            if ($listener.Port -eq $port) { return 0 }
        }
        return $port
    } catch {
        return 0
    }
}

# =============================================================================
# FUNCIONES PRINCIPALES DEL SISTEMA
# =============================================================================

function Ensure-LocalDns {
    $hostsPath = if ($IsWindows) { "C:\Windows\System32\drivers\etc\hosts" } else { "/etc/hosts" }
    $entry = "127.0.0.1 smart-economato"
    
    try {
        if (-not (Select-String -Path $hostsPath -Pattern "\bsmart-economato\b" -Quiet)) {
            Add-Content -Path $hostsPath -Value "`n$entry" -ErrorAction Stop
            Write-Success "Resolvedor DNS local (smart-economato) configurado."
        }
    } catch {
        Write-Warn "No se pudo añadir 'smart-economato' al archivo hosts (faltan permisos de Administrador/root)."
        Write-Warn "Añade manualmente '127.0.0.1 smart-economato' en $hostsPath si deseas usar ese dominio."
    }
}

function Get-LocalIP {
    try {
        $ip = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) | 
              Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.ToString() -notmatch '^127\.' } | 
              Select-Object -ExpandProperty IPAddressToString -First 1
        if ($ip) { return $ip }
        return "TU_IP_LOCAL"
    } catch {
        return "TU_IP_LOCAL"
    }
}

function Create-DesktopShortcut {
    if ($IsWindows) {
        $desktopPath = [Environment]::GetFolderPath('Desktop')
        $shortcutPath = Join-Path $desktopPath "Smart Economato.lnk"
        
        if (-not (Test-Path $shortcutPath)) {
            try {
                $WshShell = New-Object -ComObject WScript.Shell
                $Shortcut = $WshShell.CreateShortcut($shortcutPath)
                $Shortcut.TargetPath = "powershell.exe"
                $Shortcut.Arguments = "-ExecutionPolicy Bypass -WindowStyle Normal -File `"$PWD\install.ps1`""
                $Shortcut.WorkingDirectory = $PWD
                $Shortcut.Description = "Panel de Control de Smart Economato"
                $Shortcut.Save()
                Write-Success "Acceso directo creado en el Escritorio."
            } catch {
                Write-Warn "No se pudo crear el acceso directo en el escritorio."
            }
        }
    }
}

function Ensure-DockerService {
    if ($IsWindows) {
        try {
            $dockerSvc = Get-Service -Name "docker" -ErrorAction Stop
            if ($dockerSvc.StartType -ne 'Automatic') {
                Set-Service -Name "docker" -StartupType Automatic -ErrorAction Stop
                Write-Success "Servicio principal de Docker configurado para Auto-Arranque con Windows."
            }
            if ($dockerSvc.Status -ne 'Running') {
                Start-Service -Name "docker" -ErrorAction Stop
                Write-Success "Servicio principal de Docker iniciado."
            }
        } catch {
            Write-Warn "El servicio 'docker' nativo no se detectó o no tienes permisos."
            Write-Warn "Si usas Docker Desktop, marca la opción 'Start Docker Desktop when you log in' en sus ajustes, o ejecuta este panel como Administrador."
        }
    }
}

function Configure-System {
    Write-Header
    Write-Info "Ejecutando Inicialización y Despliegue de Auto-Configuración..."
    
    # 1. Dependencias
    try {
        $null = docker compose version 2>$null
        Write-Success "Docker Compose Engine Detectado."
    } catch {
        Write-ErrorMsg "Docker no detectado. Instálalo para poder correr Smart Economato."
        return
    }

    Ensure-DockerService
    Create-DesktopShortcut

    # 2. Archivo .env
    $envPath = Join-Path $PWD ".env"
    if (-not (Test-Path $envPath)) {
        Write-Info "Creando entorno seguro (.env)..."
        New-Item -ItemType File -Path $envPath | Out-Null
    }

    if (-not (Select-String -Path $envPath -Pattern "^SEED_ADMIN_NAME=" -Quiet)) {
        Write-Host "`n--- Configuración Inicial ---" -ForegroundColor Cyan
        $adminName = Read-Host "Nombre (Ej: Jefe de Cocina o pulsa Enter)"
        if ([string]::IsNullOrWhiteSpace($adminName)) { $adminName = "Admin" }
        
        $adminUser = Read-Host "Nombre de usuario"
        if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = "admin" }
        
        $adminPass = Read-Host -AsSecureString "Contraseña"
        $adminPassStr = [System.Net.NetworkCredential]::new("", $adminPass).Password
        if ([string]::IsNullOrWhiteSpace($adminPassStr)) { $adminPassStr = "admin1234" }
        
        Add-Content -Path $envPath -Value "SEED_ADMIN_NAME=$adminName"
        Add-Content -Path $envPath -Value "SEED_ADMIN_USER=$adminUser"
        Add-Content -Path $envPath -Value "SEED_ADMIN_PASSWORD=$adminPassStr"

        # Sincronizar Grafana con el Jefe de Cocina (Capa de abstracción)
        Add-Content -Path $envPath -Value "GRAFANA_USER=$adminUser"
        Add-Content -Path $envPath -Value "GRAFANA_PASSWORD=$adminPassStr"
    }

    Set-EnvSecret $envPath "POSTGRES_DB" 12 $false
    Set-EnvSecret $envPath "POSTGRES_USER" 12 $false
    Set-EnvSecret $envPath "POSTGRES_PASSWORD" 32 $true
    Set-EnvSecret $envPath "JWT_SECRET" 128 $true
    Set-EnvSecret $envPath "LEDGER_HMAC_SECRET" 128 $true
    if (-not (Select-String -Path $envPath -Pattern "^JWT_EXPIRATION=" -Quiet)) { Add-Content -Path $envPath -Value "JWT_EXPIRATION=86400000" }

    Write-Success "Las llaves de seguridad y contraseñas se han configurado correctamente."

    # 3. Volúmenes
    $volumes = @("turing-backend_postgres-data", "turing-backend_postgres-replica-data", "turing-backend_redis-data", "turing-backend_kafka-data", "turing-backend_prometheus-data", "turing-backend_grafana-data", "turing-backend_predictor-outbox-data", "turing-backend_uploads-data")
    foreach ($vol in $volumes) {
        if (-not (docker volume ls -q | Select-String "^$vol$")) {
            $null = docker volume create $vol
        }
    }
    Write-Success "Volúmenes persistentes mapeados."

    # 4. Tarea en Background (Arrancar contenedores junto a Windows)
    if ($IsWindows) {
        try {
            # Se requiere Administrador
            $action = New-ScheduledTaskAction -Execute "docker" -Argument "compose -f `"$PWD/docker-compose.yml`" up -d" -WorkingDirectory "$PWD"
            $trigger = New-ScheduledTaskTrigger -AtBoot
            $trigger2 = New-ScheduledTaskTrigger -AtLogOn
            $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
            
            Register-ScheduledTask -TaskName "SmartEconomatoBackend" -Action $action -Trigger @($trigger, $trigger2) -Principal $principal -Description "Arranca los servicios docker de Economato." -Force | Out-Null
            Write-Success "Instalada Tarea de Arranque con Windows Task Scheduler."
        } catch {
            Write-Warn "No se pudo inyectar el Task Scheduler. Asegúrate de ejecutar este panel como Administrador en Producción."
        }
    }

    Write-Success "La configuración inicial se ha completado."
}


# =============================================================================
# COMANDOS DEL PANEL DE CONTROL
# =============================================================================

function Action-Start {
    Write-Info "Iniciando Smart Economato. Por favor, espera un momento..."
    
    # Detección de puertos automática
    $p80 = Get-FreePort 80
    $p443 = Get-FreePort 443
    
    $env:PROXY_HTTP_PORT = if ($p80 -eq 80) { 80 } else { 8080 }
    $env:PROXY_HTTPS_PORT = if ($p443 -eq 443) { 443 } else { 8443 }

    try {
        $null = docker compose up -d --build 2>&1
        Write-Success "¡Sistema encendido!"
        
        $protocol = "https"
        $localIp = Get-LocalIP
        
        $localUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://localhost" } else { "${protocol}://localhost:$($env:PROXY_HTTPS_PORT)" }
        $domainUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://smart-economato" } else { "${protocol}://smart-economato:$($env:PROXY_HTTPS_PORT)" }
        $networkUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://${localIp}" } else { "${protocol}://${localIp}:$($env:PROXY_HTTPS_PORT)" }
        
        Ensure-LocalDns

        Write-Host "`n===============================================================" -ForegroundColor Cyan
        Write-Host " 🌍 ENLACE DE SMART ECONOMATO (Servidor Local) :" -ForegroundColor Green
        Write-Host "    $localUrl" -ForegroundColor White
        Write-Host "    $domainUrl (Requiere permisos en hosts)" -ForegroundColor White
        Write-Host ""
        Write-Host " 📱 ENLACE EN RED (Para otros dispositivos en tu red / WiFi) :" -ForegroundColor Yellow
        Write-Host "    $networkUrl" -ForegroundColor White
        Write-Host "===============================================================`n" -ForegroundColor Cyan

    } catch {
        Write-ErrorMsg "No se pudo iniciar el sistema. Revisa el registro de actividad."
    }
}

function Action-Stop {
    Write-Info "Apagando el sistema..."
    try {
        $null = docker compose down 2>&1
        Write-Success "Sistema apagado correctamente."
    } catch {
        Write-ErrorMsg "Error al apagar el sistema."
    }
}

function Action-Restart {
    Write-Info "Reiniciando Sistema..."
    Action-Stop
    Action-Start
}

function Action-Health {
    Write-Info "Revisando que todo funcione bien..."
    
    # Revisar contenedores detenidos y levantarlos automágicamente.
    $exited = docker ps -a --filter "status=exited" --format "{{.Names}}"
    if ([string]::IsNullOrWhiteSpace($exited)) {
        Write-Success "Todo parece estar en orden. El sistema está funcionando bien."
    } else {
        Write-Warn "Se ha detectado que algo se detuvo:"
        Write-Host $exited -ForegroundColor Yellow
        Write-Info "Intentando arreglarlo automáticamente..."
        docker compose up -d 
        Write-Success "Se han vuelto a encender los servicios que fallaban."
    }

    Write-Host "`n--- ESTADO ACTUAL DEL SISTEMA ---" -ForegroundColor Cyan
    docker compose ps
}

function Action-Logs {
    Write-Info "Abriendo el registro de actividad..."
    Write-Warn "Para salir de aquí y volver al menú, pulsa CTRL+C."
    try {
        docker compose logs --tail=100 -f
    } catch {}
}

function Action-RepairBlockchain {
    Write-Host "`n--- Reparar el Libro de Movimientos ---" -ForegroundColor DarkYellow
    Write-Host "Necesitamos confirmar que eres el responsable de cocina." -ForegroundColor White
    
    $username = Read-Host "Usuario"
    $password = Read-Host -AsSecureString "Contraseña"
    $passwordStr = [System.Net.NetworkCredential]::new("", $password).Password

    $baseUrl = "http://localhost:3000"

    Write-Info "Iniciando sesión..."
    $body = @{
        name = $username
        password = $passwordStr
    } | ConvertTo-Json

    try {
        $loginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json" -ErrorAction Stop
        $token = $loginRes.token
        if ([string]::IsNullOrEmpty($token)) {
            Write-ErrorMsg "No se pudo entrar al sistema."
            return
        }
        Write-Success "¡Acceso concedido!"
    } catch {
        Write-ErrorMsg "El nombre de usuario o la contraseña no son correctos."
        return
    }

    $headers = @{
        "Authorization" = "Bearer $token"
    }

    Write-Info "Reparando el Libro de Movimientos y Stock. Por favor, espera..."
    try {
        $rebuildRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/blockchain/rebuild-all" -Method Post -Headers $headers -ErrorAction Stop
        Write-Success "El libro de movimientos ha sido reparado con éxito."
    } catch {
        Write-ErrorMsg "Fallo al intentar reparar el libro de movimientos."
        Write-Warn $_.Exception.Message
        return
    }

    Write-Success "La operación de mantenimiento ha finalizado correctamente."
}

# =============================================================================
# BUCLE DEL MENÚ
# =============================================================================

function Should-Run-Config {
    if (-not (Test-Path $envCheckPath)) { return $true }
    
    # Comprobar si faltan claves críticas para que no sea solo "que exista el archivo"
    $criticalKeys = @("SEED_ADMIN_NAME", "JWT_SECRET", "POSTGRES_PASSWORD", "LEDGER_HMAC_SECRET")
    $content = Get-Content $envCheckPath
    foreach ($key in $criticalKeys) {
        if (-not ($content | Select-String "^$key=")) { return $true }
    }
    return $false
}

$envCheckPath = Join-Path $PWD ".env"
if (Should-Run-Config) {
    Configure-System
    Pause-Execution
}

while ($true) {
    Write-Header
    Write-Host " [ 1 ] 🟢 Iniciar Smart Economato" -ForegroundColor Green
    Write-Host " [ 2 ] 🔴 Apagar sistema" -ForegroundColor Red
    Write-Host " [ 3 ] 🔄 Reiniciar" -ForegroundColor Yellow
    Write-Host " [ 4 ] 🏥 Solucionar problemas" -ForegroundColor Cyan
    Write-Host " [ 5 ] 📋 Ver historial de actividad" -ForegroundColor Magenta
    Write-Host " [ 7 ] ⚖️  Reparar libro de stock" -ForegroundColor DarkYellow
    Write-Host " [ 9 ] 🚪 Salir" -ForegroundColor DarkGray
    Write-Host "---------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host " Hecho con ❤️  por el Grupo Turing del IES Domingo Pérez Minik:" -ForegroundColor White
    Write-Host " Francisco Airam | Javier Remedios | Lorena Fumero | Javier Pascual" -ForegroundColor Gray
    Write-Host "---------------------------------------------------------------" -ForegroundColor Cyan
    
    $choice = Read-Host "Elige una opción (1-9)"
    
    switch ($choice) {
        '1' { Action-Start; Pause-Execution }
        '2' { Action-Stop; Pause-Execution }
        '3' { Action-Restart; Pause-Execution }
        '4' { Action-Health; Pause-Execution }
        '5' { Action-Logs }
        '7' { Action-RepairBlockchain; Pause-Execution }
        '9' { Write-Host "Saliendo del Panel de Control... Ciao!"; exit }
        default { Write-ErrorMsg "Opción inválida." }
    }
}
