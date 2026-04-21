<#
.SYNOPSIS
Smart Economato - Panel de Control & Instalador para Windows Server
#>

$ErrorActionPreference = "Stop" # Comportamiento mas estricto para produccion
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Forzar colores de terminal (Fondo negro, letras blancas) para estetica profesional
try {
    $Host.UI.RawUI.BackgroundColor = "Black"
    $Host.UI.RawUI.ForegroundColor = "White"
    Clear-Host
} catch {}

# Deteccion de OS para compatibilidad con PS 5.1
if ($PSVersionTable.PSVersion.Major -lt 6) {
    $script:IsWindowsOS = $true
} else {
    $script:IsWindowsOS = $IsWindows
}

# Asegurar que el directorio de trabajo es el del script (importante tras elevacion)
Set-Location $PSScriptRoot

# Elevacion automatica en Windows
if ($script:IsWindowsOS) {
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "---------------------------------------------------------------" -ForegroundColor Yellow
        Write-Host " [AVISO] Se requieren permisos de Administrador." -ForegroundColor Yellow
        Write-Host " Se abrira una nueva ventana para continuar." -ForegroundColor Yellow
        Write-Host " (Para evitar esto, ejecuta tu terminal como Administrador)" -ForegroundColor Gray
        Write-Host "---------------------------------------------------------------" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
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
    Write-Host "  SMART ECONOMATO - PANEL DE CONTROL" -ForegroundColor Cyan
    Write-Host "===============================================================" -ForegroundColor Cyan
    Write-Host "       Panel de Control y Mantenimiento de Produccion v3.1     " -ForegroundColor White
    Write-Host ""
}

function Write-Typewriter {
    param([string]$msg, [int]$speed = 10, [string]$color = "White")
    foreach ($char in $msg.ToCharArray()) {
        Write-Host $char -NoNewline -ForegroundColor $color
        Start-Sleep -Milliseconds $speed
    }
    Write-Host ""
}

function Show-Spinner {
    param([string]$msg, [scriptblock]$action, [array]$ArgsList = @())
    $spinner = @('|', '/', '-', '\')
    $job = Start-Job -ScriptBlock $action -ArgumentList @($PWD, $ArgsList)
    
    Write-Host "[ .... ] $msg " -NoNewline -ForegroundColor Cyan
    $i = 0
    while ($job.State -eq 'Running') {
        Write-Host ("`b" * 1) -NoNewline
        Write-Host $spinner[$i % 4] -NoNewline -ForegroundColor Yellow
        $i++
        Start-Sleep -Milliseconds 150
    }
    
    $result = Receive-Job -Job $job -Wait
    $success = $job.ChildJobs[0].Error.Count -eq 0 -and $null -ne $result -and $result -ne $false
    
    # Limpiar spinner
    Write-Host ("`b" * 10) -NoNewline
    if ($success) {
        Write-Host "[  OK  ] " -NoNewline -ForegroundColor Green
    } else {
        Write-Host "[ERROR ] " -NoNewline -ForegroundColor Red
    }
    Write-Host "$msg"
    
    Remove-Job $job
    return $result
}

$script:LogFile = Join-Path $PWD "smart-economato.log"

function Write-Log {
    param([string]$level, [string]$msg)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $script:LogFile -Value "[$timestamp] [$level] $msg" -ErrorAction SilentlyContinue
}

function Write-Info { param([string]$msg); Write-Host "[ INFO ] " -NoNewline -ForegroundColor Cyan; Write-Host $msg; Write-Log "INFO" $msg }
function Write-Success { param([string]$msg); Write-Host "[  OK  ] " -NoNewline -ForegroundColor Green; Write-Host $msg; Write-Log "SUCCESS" $msg }
function Write-Warn { param([string]$msg); Write-Host "[ WARN ] " -NoNewline -ForegroundColor Yellow; Write-Host $msg; Write-Log "WARN" $msg }
function Write-ErrorMsg { param([string]$msg); Write-Host "[ERROR ] " -NoNewline -ForegroundColor Red; Write-Host $msg; Write-Log "ERROR" $msg }

function Invoke-Docker {
    param([string]$Arguments, [switch]$Silent)
    try {
        $process = Start-Process docker -ArgumentList $Arguments -NoNewWindow -Wait -PassThru -ErrorAction SilentlyContinue -RedirectStandardOutput "stdout.tmp" -RedirectStandardError "stderr.tmp"
        $success = ($process.ExitCode -eq 0)
        if (-not $success -and -not $Silent) {
            $err = Get-Content "stderr.tmp" -Raw -ErrorAction SilentlyContinue
            Write-ErrorMsg "Docker fallo: $err"
        }
        return $success
    } catch {
        return $false
    } finally {
        Remove-Item "stdout.tmp", "stderr.tmp" -ErrorAction SilentlyContinue
    }
}

function Sanitize-EnvValue {
    param([string]$value)
    # Eliminar caracteres que rompen el formato .env
    $value = $value -replace '[=\r\n\x22''#\\]', ''
    return $value.Trim()
}

function Set-EnvValue($envPath, $key, $value) {
    $escapedKey = [regex]::Escape($key)
    $content = Get-Content $envPath -ErrorAction SilentlyContinue
    if ($content | Select-String "^${escapedKey}=") {
        $content = $content | ForEach-Object {
            if ($_ -match "^${escapedKey}=") { "${key}=`"${value}`"" } else { $_ }
        }
        $content | Set-Content $envPath
    } else {
        Add-Content -Path $envPath -Value "${key}=`"${value}`""
    }
}

function Generate-RandomString($length, $isPassword=$false) {
    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    if ($isPassword) { $chars += "@#%*()_+:?" }
    
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $maxValid = [Math]::Floor(256 / $chars.Length) * $chars.Length  # Rejection threshold
    $result = [System.Text.StringBuilder]::new($length)
    $buf = New-Object Byte[] 1
    
    while ($result.Length -lt $length) {
        $rng.GetBytes($buf)
        if ($buf[0] -lt $maxValid) {
            [void]$result.Append($chars[$buf[0] % $chars.Length])
        }
    }
    $rng.Dispose()
    return $result.ToString()
}

function Set-EnvSecret($envPath, $key, $length, $isComplex, $forceLetterStart=$false) {
    if (-not (Test-Path $envPath)) { New-Item -ItemType File -Path $envPath -Force | Out-Null }
    
    $escapedKey = [regex]::Escape($key)
    $exists = Select-String -Path $envPath -Pattern "^${escapedKey}=" -Quiet
    if (-not $exists) {
        $val = Generate-RandomString $length $isComplex
        if ($forceLetterStart) {
            $letters = "abcdefghijklmnopqrstuvwxyz"
            $val = $letters[(Get-Random -Maximum $letters.Length)] + $val.Substring(1)
        }
        Add-Content -Path $envPath -Value "${key}=`"${val}`""
    } else {
        $existingVal = (Select-String -Path $envPath -Pattern "^${escapedKey}=(.*)").Matches.Groups[1].Value
        if ([string]::IsNullOrWhiteSpace($existingVal)) {
            $val = Generate-RandomString $length $isComplex
            if ($forceLetterStart) {
                $letters = "abcdefghijklmnopqrstuvwxyz"
                $val = $letters[(Get-Random -Maximum $letters.Length)] + $val.Substring(1)
            }
            $content = Get-Content $envPath
            $content = $content | ForEach-Object {
                if ($_ -match "^${escapedKey}=") { "${key}=`"${val}`"" } else { $_ }
            }
            $content | Set-Content $envPath
        }
    }
}

function Pause-Execution {
    Write-Host ""
    Write-Typewriter '>> Presiona ENTER para volver al menu principal...' 5 'Cyan'
    Read-Host
}

function Get-FreePort {
    param([int]$port)
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
        $listener.Start()
        $listener.Stop()
        return $port
    } catch {
        return 0 # Puerto ocupado o protegido por SO
    }
}

# =============================================================================
# FUNCIONES PRINCIPALES DEL SISTEMA
# =============================================================================

function Ensure-WslMemoryLimit {
    if ($script:IsWindowsOS) {
        $wslConfigPath = Join-Path $env:USERPROFILE ".wslconfig"
        if (-not (Test-Path $wslConfigPath)) {
            Write-Info "Optimizando uso de memoria RAM para Docker (WSL2)..."
            $configContent = "[wsl2]`nmemory=8GB"
            try {
                Set-Content -Path $wslConfigPath -Value $configContent -ErrorAction Stop
                Write-Success "Se ha limitado el uso de RAM de Docker a 8GB para mejorar el rendimiento."
                Write-Warn "Para que el cambio de RAM sea inmediato, reinicia Docker Desktop."
            } catch {
                Write-Warn "No se pudo crear automaticamente el archivo .wslconfig en su carpeta de usuario."
            }
        }
    }
}

function Ensure-LocalDns {
    $hostsPath = if ($script:IsWindowsOS) { "C:\Windows\System32\drivers\etc\hosts" } else { "/etc/hosts" }
    $entry = "127.0.0.1 smart-economato"
    
    try {
        $content = Get-Content $hostsPath -Raw -ErrorAction SilentlyContinue
        if ($content -notmatch '127\.0\.0\.1\s+smart-economato') {
            Add-Content -Path $hostsPath -Value "`n$entry" -ErrorAction Stop
            Write-Success "Resolvedor DNS local (smart-economato) configurado."
        }
    } catch {
        Write-Warn "No se pudo anadir 'smart-economato' al archivo hosts (faltan permisos de Administrador/root)."
        Write-Warn "Anade manualmente '127.0.0.1 smart-economato' en $hostsPath si deseas usar ese dominio."
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
        return "IP_DEL_SERVIDOR"
    }
}

function Ensure-Certificates {
    $certDir = Join-Path $PWD "nginx\certs"
    if (-not (Test-Path $certDir)) { New-Item -Path $certDir -ItemType Directory | Out-Null }
    
    $crtPath = Join-Path $certDir "local.crt"
    $keyPath = Join-Path $certDir "local.key"
    
    if (-not (Test-Path $crtPath) -or -not (Test-Path $keyPath)) {
        Write-Info "Generando certificados de seguridad (SSL) via Docker..."
        $certCmd = "run --rm -v `"${PWD}/nginx/certs:/certs`" alpine sh -c `"apk add --no-cache openssl && openssl req -x509 -nodes -days 3650 -newkey rsa:2048 -keyout /certs/local.key -out /certs/local.crt -subj '/CN=localhost'`""
        if (Invoke-Docker $certCmd) {
            Write-Success "Certificados SSL generados correctamente."
        } else {
            Write-ErrorMsg "Fallo la generacion de certificados SSL."
        }
    }
}

function Create-DesktopShortcut {
    if ($script:IsWindowsOS) {
        $desktopPath = [Environment]::GetFolderPath('Desktop')
        $shortcutPath = Join-Path $desktopPath "Smart Economato.lnk"
        $iconPath = "c:\Users\PC\Desktop\turing\smart-economato-API\front\src\assets\img\logo-candelaria-new-sin-fondo.png"
        
        try {
            $WshShell = New-Object -ComObject WScript.Shell
            $Shortcut = $WshShell.CreateShortcut($shortcutPath)
            $Shortcut.TargetPath = "powershell.exe"
            $Shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Normal -File `"$PWD\install.ps1`""
            $Shortcut.WorkingDirectory = $PWD.Path
            $Shortcut.Description = "Panel de Control de Smart Economato"
            if (Test-Path $iconPath) {
                $Shortcut.IconLocation = $iconPath
            }
            $Shortcut.Save()
            Write-Success "Acceso directo 'Smart Economato' creado en el Escritorio."
        } catch {
            Write-Warn "No se pudo crear el acceso directo en el escritorio: $($_.Exception.Message)"
        }
    }
}

function Ensure-DockerService {
    if ($script:IsWindowsOS) {
        Write-Info "Verificando estado de Docker..."
        try {
            $dockerSvc = Get-Service -Name "docker" -ErrorAction SilentlyContinue
            if ($null -eq $dockerSvc) {
                # Probablemente Docker Desktop sin el servicio registrado como 'docker' (comun en instalaciones recientes)
                # Verificamos si el proceso esta corriendo
                if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
                    Write-Warn "Docker Desktop no parece estar ejecutandose. Intentando iniciarlo..."
                    $desktopPath = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"
                    if (Test-Path $desktopPath) {
                        Start-Process $desktopPath
                        Write-Info "Esperando a que Docker se inicie (esto puede tardar 1-2 minutos)..."
                        $timeout = 60
                        while (-not (Invoke-Docker "version" -Silent) -and $timeout -gt 0) {
                            Start-Sleep -Seconds 2
                            $timeout -= 2
                        }
                    } else {
                        Write-ErrorMsg "No se encontro el ejecutable de Docker Desktop. Por favor, inicialo manualmente."
                    }
                }
            } else {
                if ($dockerSvc.StartType -ne 'Automatic') {
                    Set-Service -Name "docker" -StartupType Automatic
                    Write-Success "Servicio Docker configurado para Auto-Arranque."
                }
                if ($dockerSvc.Status -ne 'Running') {
                    Start-Service -Name "docker"
                    Write-Success "Servicio Docker iniciado."
                }
            }
        } catch {
            Write-Warn "Hubo un problema al intentar gestionar el servicio Docker: $($_.Exception.Message)"
        }
        
        # Validacion final
        if (-not (Invoke-Docker "version" -Silent)) {
            Write-ErrorMsg "Docker no esta respondiendo. Asegurate de que Docker Desktop esta abierto y configurado correctamente."
            return $false
        }
        return $true
    }
    return $true
}

$script:ManagedVolumes = @(
    "turing-backend_postgres-data",
    "turing-backend_postgres-replica-data",
    "turing-backend_redis-data",
    "turing-backend_kafka-data",
    "turing-backend_prometheus-data",
    "turing-backend_grafana-data",
    "turing-backend_predictor-outbox-data",
    "turing-backend_uploads-data"
)

function Get-VolumeBackupRoot {
    $backupRoot = Join-Path $PWD "backups\volumes"
    if (-not (Test-Path $backupRoot)) {
        New-Item -Path $backupRoot -ItemType Directory -Force | Out-Null
    }
    return $backupRoot
}

function Export-ManagedVolumes {
    param([string]$targetDir)

    if (-not (Test-Path $targetDir)) {
        New-Item -Path $targetDir -ItemType Directory -Force | Out-Null
    }

    foreach ($vol in $script:ManagedVolumes) {
        $archiveName = "$vol.tar.gz"
        Write-Info "Copiando volumen: $vol"
        & docker run --rm -v "${vol}:/volume" -v "${targetDir}:/backup" alpine sh -c "tar -C /volume -czf /backup/$archiveName ." 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-ErrorMsg "No se pudo exportar el volumen $vol."
            return $false
        }
    }

    Set-Content -Path (Join-Path $targetDir "manifest.txt") -Value ($script:ManagedVolumes -join [Environment]::NewLine)
    return $true
}

function Import-ManagedVolumes {
    param([string]$sourceDir)

    foreach ($vol in $script:ManagedVolumes) {
        $archiveName = "$vol.tar.gz"
        $archivePath = Join-Path $sourceDir $archiveName
        if (-not (Test-Path $archivePath)) {
            Write-ErrorMsg "Falta el archivo de respaldo del volumen: $archiveName"
            return $false
        }

        Write-Info "Restaurando volumen: $vol"
        & docker run --rm -v "${vol}:/volume" -v "${sourceDir}:/backup" alpine sh -c "rm -rf /volume/* /volume/.[!.]* /volume/..?* 2>/dev/null; tar -xzf /backup/$archiveName -C /volume" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-ErrorMsg "No se pudo restaurar el volumen $vol."
            return $false
        }
    }
    return $true
}

function Ensure-ManagedVolumes {
    foreach ($vol in $script:ManagedVolumes) {
        if (-not (docker volume ls -q | Select-String "^$vol$")) {
            Write-Info "Re-mapeando volumen de datos: $vol"
            Invoke-Docker "volume create $vol" -Silent | Out-Null
        }
    }
}

function Configure-System {
    Write-Header
    Write-Info "Ejecutando Inicializacion y Despliegue de Auto-Configuracion..."
    
    # 0. Optimizacion de RAM
    Ensure-WslMemoryLimit

    # 1. Dependencias (Solo Docker)
    if (-not (Ensure-DockerService)) {
        return
    }
    Write-Success "Motor Docker (Docker Compose) Listo."

    [void](Ensure-DockerService)
    Create-DesktopShortcut

    # 2. Archivo .env
    $envPath = Join-Path $PWD ".env"
    if (-not (Test-Path $envPath)) {
        Write-Info "Creando entorno seguro (.env)..."
        New-Item -ItemType File -Path $envPath | Out-Null
    }

    if (-not (Select-String -Path $envPath -Pattern "^SEED_ADMIN_NAME=" -Quiet)) {
        Write-Host "`n--- Configuracion Inicial ---" -ForegroundColor Cyan
        Write-Host " Por favor, rellena los siguientes datos para crear el usuario administrador." -ForegroundColor Gray
        do {
            $adminName = Read-Host " Nombre Completo (Obligatorio, ej: Jefe de Cocina)"
            if ([string]::IsNullOrWhiteSpace($adminName)) { Write-Warn "El nombre no puede estar vacio." }
        } while ([string]::IsNullOrWhiteSpace($adminName))
        $adminName = Sanitize-EnvValue $adminName
        
        do {
            $adminUser = Read-Host "Nombre de usuario (Obligatorio, ej: jefe_cocina)"
            if ([string]::IsNullOrWhiteSpace($adminUser)) { Write-Warn "El nombre de usuario no puede estar vacio." }
        } while ([string]::IsNullOrWhiteSpace($adminUser))
        $adminUser = Sanitize-EnvValue $adminUser
        
        do {
            $adminPass = Read-Host -AsSecureString "Contrasena (obligatoria, minimo 8 caracteres)"
            $adminPassStr = [System.Net.NetworkCredential]::new("", $adminPass).Password
            if ($adminPassStr.Length -lt 8) {
                Write-Warn "La contrasena debe tener al menos 8 caracteres."
            }
        } while ($adminPassStr.Length -lt 8)
        $adminPassStr = Sanitize-EnvValue $adminPassStr
        
        Add-Content -Path $envPath -Value "SEED_ADMIN_NAME=`"$adminName`""
        Add-Content -Path $envPath -Value "SEED_ADMIN_USER=`"$adminUser`""
        Add-Content -Path $envPath -Value "SEED_ADMIN_PASSWORD=`"$adminPassStr`""

        # Sincronizar Grafana con el Jefe de Cocina (Capa de abstraccion)
        Add-Content -Path $envPath -Value "GRAFANA_USER=`"$adminUser`""
        Add-Content -Path $envPath -Value "GRAFANA_PASSWORD=`"$adminPassStr`""
    }

    Set-EnvSecret $envPath "POSTGRES_DB" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_USER" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_PASSWORD" 32 $true
    Set-EnvSecret $envPath "JWT_SECRET" 128 $true
    Set-EnvSecret $envPath "LEDGER_HMAC_SECRET" 128 $true
    if (-not (Select-String -Path $envPath -Pattern "^JWT_EXPIRATION=" -Quiet)) { Add-Content -Path $envPath -Value "JWT_EXPIRATION=`"86400000`"" }

    # Configuracion de IA (AI NEST)
    Set-EnvSecret $envPath "AI_NEST_SERVICE_KEY" 64 $true
    if (-not (Select-String -Path $envPath -Pattern "^AI_NEST_BASE_URL=" -Quiet)) { Add-Content -Path $envPath -Value "AI_NEST_BASE_URL=`"http://localhost:3000`"" }
    if (-not (Select-String -Path $envPath -Pattern "^AI_NEST_ALLOWED_ORIGIN=" -Quiet)) { Add-Content -Path $envPath -Value "AI_NEST_ALLOWED_ORIGIN=`"http://localhost:3000`"" }

    Write-Success "Las llaves de seguridad y contrasenas se han configurado correctamente."

    # 3. Volumenes
    Ensure-ManagedVolumes
    Write-Success "Volumenes persistentes mapeados."

    # 4. Tarea en Background (Arrancar contenedores junto a Windows)
    if ($script:IsWindowsOS) {
        try {
            # Se requiere Administrador
            $projectRoot = (Resolve-Path $PSScriptRoot).Path
            $action = New-ScheduledTaskAction -Execute "docker" -Argument "compose -f `"$projectRoot/docker-compose.yml`" up -d" -WorkingDirectory "$projectRoot"
            $trigger = New-ScheduledTaskTrigger -AtBoot
            $trigger2 = New-ScheduledTaskTrigger -AtLogOn
            $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
            
            Register-ScheduledTask -TaskName "SmartEconomatoBackend" -Action $action -Trigger @($trigger, $trigger2) -Principal $principal -Description "Arranca los servicios docker de Economato." -Force | Out-Null
            Write-Success "Instalada Tarea de Arranque con Windows Task Scheduler."
        } catch {
            Write-Warn "No se pudo inyectar el Task Scheduler. Asegurate de ejecutar este panel como Administrador en Produccion."
        }
    }

    Write-Success "La configuracion inicial se ha completado."
}


# =============================================================================
# COMANDOS DEL PANEL DE CONTROL
# =============================================================================

function Action-Start {
    Write-Info "Iniciando Smart Economato. Por favor, espera un momento..."
    
    # Asegurar que los volumenes existen antes de arrancar (Docker Compose los marca como externos)
    Ensure-ManagedVolumes

    # Deteccion de puertos interactiva
    $envPath = Join-Path $PWD ".env"
    $p80 = Get-FreePort 80
    $p443 = Get-FreePort 443
    
    if ($p80 -eq 80) { 
        $httpPort = 80 
    } else {
        Write-Warn "El puerto 80 esta ocupado."
        $httpPort = Read-Host "Introduce un puerto alternativo para HTTP (ej: 3000)"
        if ([string]::IsNullOrWhiteSpace($httpPort)) { $httpPort = 3000 }
    }

    if ($p443 -eq 443) { 
        $httpsPort = 443 
    } else {
        Write-Warn "El puerto 443 esta ocupado."
        $httpsPort = Read-Host "Introduce un puerto alternativo para HTTPS (ej: 3443)"
        if ([string]::IsNullOrWhiteSpace($httpsPort)) { $httpsPort = 3443 }
    }
    
    # Persistir en .env para que docker-compose los lea
    Set-EnvValue $envPath "PROXY_HTTP_PORT" $httpPort
    Set-EnvValue $envPath "PROXY_HTTPS_PORT" $httpsPort
    Set-EnvValue $envPath "NGINX_CONF_PATH" "./nginx/reverse-proxy.template"

    # Sincronizar origen de IA con la URL real para evitar errores de CORS
    $protocol = "https"
    $realOrigin = if ($httpsPort -eq 443) { "${protocol}://localhost" } else { "${protocol}://localhost:${httpsPort}" }
    Set-EnvValue $envPath "AI_NEST_ALLOWED_ORIGIN" $realOrigin
    
    # Tambien setear en el proceso actual
    $env:PROXY_HTTP_PORT = $httpPort
    $env:PROXY_HTTPS_PORT = $httpsPort
    $env:NGINX_CONF_PATH = "./nginx/reverse-proxy.template"
    $env:AI_NEST_ALLOWED_ORIGIN = $realOrigin

    Write-Info "Desplegando contenedores Docker. Esto puede tardar varios minutos..."
    Write-Host "---------------------------------------------------------------" -ForegroundColor Gray
    & docker compose up -d --build --wait
    $success = ($LASTEXITCODE -eq 0)
    Write-Host "---------------------------------------------------------------" -ForegroundColor Gray

    if ($success) {
        Write-Success "Sistema encendido!"
        
        $localIp = Get-LocalIP
        
        $localUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://localhost" } else { "${protocol}://localhost:$($env:PROXY_HTTPS_PORT)" }
        $domainUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://smart-economato" } else { "${protocol}://smart-economato:$($env:PROXY_HTTPS_PORT)" }
        $networkUrl = if ($env:PROXY_HTTPS_PORT -eq 443) { "${protocol}://${localIp}" } else { "${protocol}://${localIp}:$($env:PROXY_HTTPS_PORT)" }
        
        Ensure-LocalDns

        Write-Host "`n===============================================================" -ForegroundColor Cyan
        Write-Host " ENLACE DE SMART ECONOMATO (Servidor Local) :" -ForegroundColor Green
        Write-Host "    $localUrl" -ForegroundColor White
        Write-Host "    $domainUrl (Requiere permisos en hosts)" -ForegroundColor White
        Write-Host ""
        Write-Host " ENLACE EN RED (Para otros dispositivos en tu red / WiFi) :" -ForegroundColor Yellow
        Write-Host "    $networkUrl" -ForegroundColor White
        Write-Host "===============================================================`n" -ForegroundColor Cyan

        # Abrir navegador automaticamente
        Write-Info "Abriendo Smart Economato en el navegador..."
        Start-Process $localUrl

    } else {
        Write-ErrorMsg "No se pudo iniciar el sistema. Revisa el registro de actividad."
    }
}

function Action-Stop {
    Write-Info "Apagando el sistema..."
    if (Invoke-Docker "compose down") {
        Write-Success "Sistema apagado correctamente."
    } else {
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
    
    # Revisar contenedores detenidos y levantarlos automaticamente.
    $exited = docker compose ps --filter "status=exited" --format "{{.Names}}" 2>$null
    if ([string]::IsNullOrWhiteSpace($exited)) {
        Write-Success "Todos los servicios están en ejecucion."
    } else {
        Write-Warn "Se ha detectado que algunos servicios se detuvieron:"
        Write-Host $exited -ForegroundColor Yellow
        Write-Info "Intentando recuperación automatica..."
        Invoke-Docker "compose up -d" | Out-Null
    }

    Write-Info "Escaneando registros en busca de errores recientes..."
    $errorLogs = docker compose logs --tail=50 2>&1 | Select-String "ERROR", "Critical", "Fatal" -Context 0,1
    if ($errorLogs) {
        Write-Warn "Se encontraron posibles errores en los registros de los contenedores:"
        $errorLogs | ForEach-Object { Write-Host " > $($_.Line)" -ForegroundColor Red }
    } else {
        Write-Success "No se detectaron errores criticos en los logs recientes."
    }

    Write-Host "`n--- ESTADO ACTUAL DEL SISTEMA ---" -ForegroundColor Cyan
    docker compose ps
}


function Action-Logs {
    Write-Info "Abriendo el registro de actividad..."
    Write-Warn "Para salir de aqui y volver al menu, pulsa CTRL+C."
    try {
        docker compose logs --tail=100 -f
    } catch {}
}

function Action-RepairBlockchain {
    Write-Host "`n--- Reparar el Libro de Movimientos ---" -ForegroundColor DarkYellow
    Write-Host "Necesitamos confirmar que eres el responsable de cocina." -ForegroundColor White
    
    $username = Read-Host "Usuario"
    $password = Read-Host -AsSecureString "Contrasena"
    $passwordStr = [System.Net.NetworkCredential]::new("", $password).Password

    # Usar la URL detectada por el sistema
    $protocol = "https"
    $hostName = "localhost"
    $port = if ($env:PROXY_HTTPS_PORT) { $env:PROXY_HTTPS_PORT } else { 443 }
    $baseUrl = if ($port -eq 443) { "${protocol}://${hostName}" } else { "${protocol}://${hostName}:$port" }

    Write-Info "Conectando a $baseUrl..."
    Write-Info "Iniciando sesion..."
    $body = @{
        name = $username
        password = $passwordStr
    } | ConvertTo-Json

    $invokeParams = @{
        Uri = "$baseUrl/api/auth/login"
        Method = 'Post'
        Body = $body
        ContentType = 'application/json'
        ErrorAction = 'Stop'
    }

    # PS 7+: usar -SkipCertificateCheck
    if ($PSVersionTable.PSVersion.Major -ge 7) {
        $invokeParams['SkipCertificateCheck'] = $true
    } else {
        # PS 5.1: deshabilitar validación SSL temporalmente de forma sencilla
        [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
    }

    try {
        $loginRes = Invoke-RestMethod @invokeParams
        $token = $loginRes.token
        if ([string]::IsNullOrEmpty($token)) {
            Write-ErrorMsg "No se pudo entrar al sistema."
            return
        }
        Write-Success "Acceso concedido!"
    } catch {
        Write-ErrorMsg "El nombre de usuario o la contrasena no son correctos."
        return
    }

    $headers = @{
        "Authorization" = "Bearer $token"
    }

    Write-Info "Reparando el Libro de Movimientos y Stock. Por favor, espera..."
    try {
        $repairParams = @{
            Uri = "$baseUrl/api/admin/blockchain/rebuild-all"
            Method = 'Post'
            Headers = $headers
            ErrorAction = 'Stop'
        }
        if ($PSVersionTable.PSVersion.Major -ge 7) { $repairParams['SkipCertificateCheck'] = $true }

        $rebuildRes = Invoke-RestMethod @repairParams
        Write-Success "El libro de movimientos ha sido reparado con exito."
    } catch {
        Write-ErrorMsg "Fallo al intentar reparar el libro de movimientos."
        Write-Warn $_.Exception.Message
        return
    }

    Write-Success "La operacion de mantenimiento ha finalizado correctamente."
}

function Action-Backup {
    Write-Info "Creando copia de seguridad de volumenes Docker..."

    $backupRoot = Get-VolumeBackupRoot
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupDir = Join-Path $backupRoot "backup_$timestamp"
    New-Item -Path $backupDir -ItemType Directory -Force | Out-Null

    if (Export-ManagedVolumes $backupDir) {
        Write-Success "Backup de volumenes completado: $backupDir"
    } else {
        Write-ErrorMsg "Fallo la copia de seguridad de volumenes."
    }
}

function Action-RestoreBackup {
    Write-Info "Restaurando backup de volumenes Docker..."

    $backupRoot = Get-VolumeBackupRoot
    $backupDirs = Get-ChildItem -Path $backupRoot -Directory | Sort-Object Name -Descending
    if (-not $backupDirs -or $backupDirs.Count -eq 0) {
        Write-ErrorMsg "No hay backups de volumenes disponibles."
        return
    }

    Write-Host "`n--- Backups de volumenes disponibles ---" -ForegroundColor Cyan
    for ($i = 0; $i -lt $backupDirs.Count; $i++) {
        Write-Host (" [" + ($i + 1) + "] " + $backupDirs[$i].Name) -ForegroundColor White
    }

    $selection = Read-Host "Selecciona el numero del backup a restaurar"
    if (-not ($selection -match '^\d+$')) {
        Write-ErrorMsg "Selección invalida."
        return
    }

    $index = [int]$selection - 1
    if ($index -lt 0 -or $index -ge $backupDirs.Count) {
        Write-ErrorMsg "Selección fuera de rango."
        return
    }

    $selectedBackup = $backupDirs[$index]
    Write-Warn "Se guardara una copia del estado actual y luego se reemplazaran los volumenes por el backup elegido."
    $confirm = Read-Host "Escribe SI para continuar"
    if ($confirm -ne "SI") {
        Write-Info "Operación cancelada por el usuario."
        return
    }

    Write-Info "Deteniendo servicios para restauracion consistente..."
    if (-not (Invoke-Docker "compose down" -Silent)) {
        Write-Warn "No se pudo detener completamente Docker Compose. Se intentara continuar."
    }

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $safetyBackup = Join-Path $backupRoot "pre_restore_$timestamp"
    New-Item -Path $safetyBackup -ItemType Directory -Force | Out-Null

    Write-Info "Guardando estado actual de volumenes en: $safetyBackup"
    if (-not (Export-ManagedVolumes $safetyBackup)) {
        Write-ErrorMsg "No se pudo crear la copia de seguridad previa. Restauración cancelada para proteger datos."
        return
    }

    if (Import-ManagedVolumes $selectedBackup.FullName) {
        Write-Success "Backup restaurado correctamente desde: $($selectedBackup.Name)"
    } else {
        Write-ErrorMsg "Fallo la restauracion de volumenes. El backup previo quedo guardado en: $safetyBackup"
    }

    Write-Info "Arrancando servicios nuevamente..."
    if (Invoke-Docker "compose up -d") {
        Write-Success "Servicios levantados correctamente."
    } else {
        Write-Warn "No se pudieron iniciar todos los servicios automaticamente."
    }
}

function Action-Credits {
    Write-Host "`n--- Creditos ---" -ForegroundColor Cyan
    Write-Host "Francisco Airam - Backend e Infraestructura" -ForegroundColor White
    Write-Host "Javier Remedios - Frontend e Integracion de IA" -ForegroundColor White
    Write-Host "Lorena Fumero - Maquetacion y UI/UX" -ForegroundColor White
    Write-Host "Javier Pascual - Apoyo emocional" -ForegroundColor White
}

# =============================================================================
# BUCLE DEL MENU
# =============================================================================

$envCheckPath = Join-Path $PWD ".env"

function Should-Run-Config {
    param([string]$envPath)
    if (-not (Test-Path $envPath)) { return $true }
    
    # Comprobar si faltan claves criticas para que no sea solo "que exista el archivo"
    $criticalKeys = @('SEED_ADMIN_NAME', 'JWT_SECRET', 'POSTGRES_PASSWORD', 'LEDGER_HMAC_SECRET')
    $content = Get-Content $envPath
    foreach ($key in $criticalKeys) {
        $escapedKey = [regex]::Escape($key)
        if (-not ($content | Select-String -Pattern ('^' + $escapedKey + '='))) { return $true }
    }
    return $false
}

if (Should-Run-Config $envCheckPath) {
    Ensure-Certificates
    Configure-System
    Pause-Execution
}

while ($true) {
    Write-Header
    Write-Typewriter '  Acceso rapido al Panel de Control Smart Economato' 5 'Gray'
    Write-Host ' ---------------------------------------------------------------' -ForegroundColor DarkGray
    Write-Host ' [ 1 ] ( ) Iniciar Smart Economato' -ForegroundColor Green
    Write-Host ' [ 2 ] (x) Apagar sistema' -ForegroundColor Red
    Write-Host ' [ 3 ] (*) Reiniciar sistema' -ForegroundColor Yellow
    Write-Host ' [ 4 ] (+) Auto-reparar sistema' -ForegroundColor Cyan
    Write-Host ' [ 5 ] (-) Ver actividad (Logs)' -ForegroundColor Magenta
    Write-Host ' [ 6 ] (S) Crear copia de seguridad' -ForegroundColor Blue
    Write-Host ' [ 7 ] (L) Cargar copia de seguridad' -ForegroundColor Blue
    Write-Host ' [ 8 ] (#) Sincronizar Stock' -ForegroundColor DarkYellow
    Write-Host ' [ 9 ] (?) Ver creditos' -ForegroundColor White
    Write-Host ' [ 0 ] [E] Salir' -ForegroundColor DarkGray
    Write-Host ' ---------------------------------------------------------------' -ForegroundColor Cyan
    Write-Host ' Hecho por el Grupo Turing del IES Domingo Perez Minik' -ForegroundColor White
    Write-Host ' ---------------------------------------------------------------' -ForegroundColor Cyan
    
    $choice = Read-Host ' >> Selecciona una accion'
    
    switch ($choice) {
        '1' { Action-Start; Pause-Execution }
        '2' { Action-Stop; Pause-Execution }
        '3' { Action-Restart; Pause-Execution }
        '4' { Action-Health; Pause-Execution }
        '5' { Action-Logs }
        '6' { Action-Backup; Pause-Execution }
        '7' { Action-RestoreBackup; Pause-Execution }
        '8' { Action-RepairBlockchain; Pause-Execution }
        '9' { Action-Credits; Pause-Execution }
        '0' { Write-Host 'Saliendo del Panel de Control... Ciao!'; exit }
        default { Write-ErrorMsg 'Opcion invalida.' }
    }
}
