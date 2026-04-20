<#
.SYNOPSIS
Smart Economato - Panel de Control & Instalador para Windows Server
#>

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Detección de OS para compatibilidad con PS 5.1
if ($PSVersionTable.PSVersion.Major -lt 6) {
    $script:IsWindowsOS = $true   # PS 5.1 solo corre en Windows
} else {
    $script:IsWindowsOS = $IsWindows
}

# Elevación automática en Windows
if ($script:IsWindowsOS) {
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
    Write-Host "       Panel de Control y Mantenimiento de Producción v3.1     " -ForegroundColor White
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
    $job = Start-Job -ScriptBlock $action -ArgumentList ($using:PWD + $ArgsList)
    
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
    $output = & docker $Arguments.Split(' ') 2>&1
    if ($LASTEXITCODE -ne 0) {
        if (-not $Silent) { Write-ErrorMsg "Docker falló (exit code $LASTEXITCODE): $output" }
        return $false
    }
    return $true
}

function Sanitize-EnvValue {
    param([string]$value)
    # Eliminar caracteres que rompen el formato .env
    $value = $value -replace '[=\r\n"''#\\]', ''
    return $value.Trim()
}

function Set-EnvValue($envPath, $key, $value) {
    $escapedKey = [regex]::Escape($key)
    $content = Get-Content $envPath -ErrorAction SilentlyContinue
    if ($content | Select-String "^${escapedKey}=") {
        $content = $content | ForEach-Object {
            if ($_ -match "^${escapedKey}=") { "${key}=${value}" } else { $_ }
        }
        $content | Set-Content $envPath
    } else {
        Add-Content -Path $envPath -Value "${key}=${value}"
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
        Add-Content -Path $envPath -Value "${key}=${val}"
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
                if ($_ -match "^${escapedKey}=") { "${key}=${val}" } else { $_ }
            }
            $content | Set-Content $envPath
        }
    }
}

function Pause-Execution {
    Write-Host ""
    Write-Typewriter ">> Presiona ENTER para volver al menú principal..." 5 "Cyan"
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
        return "IP_DEL_SERVIDOR"
    }
}

function Ensure-Certificates {
    $certDir = Join-Path $PWD "nginx\certs"
    if (-not (Test-Path $certDir)) { New-Item -Path $certDir -ItemType Directory | Out-Null }
    
    $crtPath = Join-Path $certDir "local.crt"
    $keyPath = Join-Path $certDir "local.key"
    
    if (-not (Test-Path $crtPath) -or -not (Test-Path $keyPath)) {
        Show-Spinner "Generando certificados de seguridad (SSL)..." {
            param($cwd)
            if ($PSVersionTable.PSVersion.Major -lt 6) { $script:IsWindowsOS = $true } else { $script:IsWindowsOS = $IsWindows }
            if ($script:IsWindowsOS) {
                $cert = New-SelfSignedCertificate -DnsName "localhost", "smart-economato" -CertStoreLocation "Cert:\LocalMachine\My" -NotAfter (Get-Date).AddYears(10) -FriendlyName "Smart Economato SSL"
                $crtBase64 = "-----BEGIN CERTIFICATE-----`n" + [Convert]::ToBase64String($cert.RawData, "InsertLineBreaks") + "`n-----END CERTIFICATE-----"
                Set-Content -Path (Join-Path $cwd "nginx\certs\local.crt") -Value $crtBase64
                $rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($cert)
                $keyBase64 = "-----BEGIN PRIVATE KEY-----`n" + [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey(), "InsertLineBreaks") + "`n-----END PRIVATE KEY-----"
                Set-Content -Path (Join-Path $cwd "nginx\certs\local.key") -Value $keyBase64
                return $true
            }
            return $false
        }
    }
}

function Create-DesktopShortcut {
    if ($script:IsWindowsOS) {
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
    if ($script:IsWindowsOS) {
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

function Configure-System {
    Write-Header
    Write-Info "Ejecutando Inicialización y Despliegue de Auto-Configuración..."
    
    # 1. Dependencias
    if (-not (Invoke-Docker "compose version" -Silent)) {
        Write-ErrorMsg "Docker no detectado. Instálalo para poder correr Smart Economato."
        return
    }
    Write-Success "Docker Compose Engine Detectado."

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
        $adminName = Read-Host "Nombre (Ej: Jefe_de_Cocina o pulsa Enter)"
        if ([string]::IsNullOrWhiteSpace($adminName)) { $adminName = "Admin" }
        $adminName = Sanitize-EnvValue $adminName
        
        $adminUser = Read-Host "Nombre de usuario"
        if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = "admin" }
        $adminUser = Sanitize-EnvValue $adminUser
        
        do {
            $adminPass = Read-Host -AsSecureString "Contraseña (obligatoria, mínimo 8 caracteres)"
            $adminPassStr = [System.Net.NetworkCredential]::new("", $adminPass).Password
            if ($adminPassStr.Length -lt 8) {
                Write-Warn "La contraseña debe tener al menos 8 caracteres."
            }
        } while ($adminPassStr.Length -lt 8)
        $adminPassStr = Sanitize-EnvValue $adminPassStr
        
        Add-Content -Path $envPath -Value "SEED_ADMIN_NAME=$adminName"
        Add-Content -Path $envPath -Value "SEED_ADMIN_USER=$adminUser"
        Add-Content -Path $envPath -Value "SEED_ADMIN_PASSWORD=$adminPassStr"

        # Sincronizar Grafana con el Jefe de Cocina (Capa de abstracción)
        Add-Content -Path $envPath -Value "GRAFANA_USER=$adminUser"
        Add-Content -Path $envPath -Value "GRAFANA_PASSWORD=$adminPassStr"
    }

    Set-EnvSecret $envPath "POSTGRES_DB" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_USER" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_PASSWORD" 32 $true
    Set-EnvSecret $envPath "JWT_SECRET" 128 $true
    Set-EnvSecret $envPath "LEDGER_HMAC_SECRET" 128 $true
    if (-not (Select-String -Path $envPath -Pattern "^JWT_EXPIRATION=" -Quiet)) { Add-Content -Path $envPath -Value "JWT_EXPIRATION=86400000" }

    Write-Success "Las llaves de seguridad y contraseñas se han configurado correctamente."

    # 3. Volúmenes
    foreach ($vol in $script:ManagedVolumes) {
        if (-not (docker volume ls -q | Select-String "^$vol$")) {
            Invoke-Docker "volume create $vol" | Out-Null
        }
    }
    Write-Success "Volúmenes persistentes mapeados."

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
    
    $httpPort = if ($p80 -eq 80) { 80 } else { 3000 }
    $httpsPort = if ($p443 -eq 443) { 443 } else { 3443 }
    
    # Verificar que los puertos fallback también estén libres
    if ($p80 -ne 80) {
        $pFallback = Get-FreePort 3000
        if ($pFallback -eq 0) {
            Write-ErrorMsg "Los puertos 80 y 3000 están ocupados. Libera uno de ellos."
            return
        }
    }
    if ($p443 -ne 443) {
        $pFallback = Get-FreePort 3443
        if ($pFallback -eq 0) {
            Write-ErrorMsg "Los puertos 443 y 3443 están ocupados. Libera uno de ellos."
            return
        }
    }
    
    # Persistir en .env para que docker-compose los lea
    $envPath = Join-Path $PWD ".env"
    Set-EnvValue $envPath "PROXY_HTTP_PORT" $httpPort
    Set-EnvValue $envPath "PROXY_HTTPS_PORT" $httpsPort
    Set-EnvValue $envPath "NGINX_CONF_PATH" "./nginx/reverse-proxy.template"
    
    # También setear en el proceso actual
    $env:PROXY_HTTP_PORT = $httpPort
    $env:PROXY_HTTPS_PORT = $httpsPort
    $env:NGINX_CONF_PATH = "./nginx/reverse-proxy.template"

    $success = Show-Spinner "Desplegando contenedores Docker..." {
        param($cwd)
        Set-Location $cwd
        $args = "compose up -d --build"
        & docker $args.Split(' ') 2>&1
        return ($LASTEXITCODE -eq 0)
    }

    if ($success) {
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
    
    # Revisar contenedores detenidos y levantarlos automágicamente.
    $exited = docker compose ps --filter "status=exited" --format "{{.Names}}" 2>$null
    if ([string]::IsNullOrWhiteSpace($exited)) {
        Write-Success "Todo parece estar en orden. El sistema está funcionando bien."
    } else {
        Write-Warn "Se ha detectado que algo se detuvo:"
        Write-Host $exited -ForegroundColor Yellow
        Write-Info "Intentando arreglarlo automáticamente..."
        Invoke-Docker "compose up -d" | Out-Null
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

    # Usar la URL detectada por el sistema
    $protocol = "https"
    $hostName = "localhost"
    $port = if ($env:PROXY_HTTPS_PORT) { $env:PROXY_HTTPS_PORT } else { 443 }
    $baseUrl = if ($port -eq 443) { "${protocol}://${hostName}" } else { "${protocol}://${hostName}:$port" }

    Write-Info "Conectando a $baseUrl..."
    Write-Info "Iniciando sesión..."
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
        # PS 5.1: deshabilitar validación SSL temporalmente
        if (-not ([System.Management.Automation.PSTypeName]'TrustAllCertsPolicy').Type) {
            Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
"@
        }
        [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
    }

    try {
        $loginRes = Invoke-RestMethod @invokeParams
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
        $repairParams = @{
            Uri = "$baseUrl/api/admin/blockchain/rebuild-all"
            Method = 'Post'
            Headers = $headers
            ErrorAction = 'Stop'
        }
        if ($PSVersionTable.PSVersion.Major -ge 7) { $repairParams['SkipCertificateCheck'] = $true }

        $rebuildRes = Invoke-RestMethod @repairParams
        Write-Success "El libro de movimientos ha sido reparado con éxito."
    } catch {
        Write-ErrorMsg "Fallo al intentar reparar el libro de movimientos."
        Write-Warn $_.Exception.Message
        return
    }

    Write-Success "La operación de mantenimiento ha finalizado correctamente."
}

function Action-Backup {
    Write-Info "Creando copia de seguridad de volúmenes Docker..."

    $backupRoot = Get-VolumeBackupRoot
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupDir = Join-Path $backupRoot "backup_$timestamp"
    New-Item -Path $backupDir -ItemType Directory -Force | Out-Null

    if (Export-ManagedVolumes $backupDir) {
        Write-Success "Backup de volúmenes completado: $backupDir"
    } else {
        Write-ErrorMsg "Falló la copia de seguridad de volúmenes."
    }
}

function Action-RestoreBackup {
    Write-Info "Restaurando backup de volúmenes Docker..."

    $backupRoot = Get-VolumeBackupRoot
    $backupDirs = Get-ChildItem -Path $backupRoot -Directory | Sort-Object Name -Descending
    if (-not $backupDirs -or $backupDirs.Count -eq 0) {
        Write-ErrorMsg "No hay backups de volúmenes disponibles."
        return
    }

    Write-Host "`n--- Backups de volúmenes disponibles ---" -ForegroundColor Cyan
    for ($i = 0; $i -lt $backupDirs.Count; $i++) {
        Write-Host (" [" + ($i + 1) + "] " + $backupDirs[$i].Name) -ForegroundColor White
    }

    $selection = Read-Host "Selecciona el número del backup a restaurar"
    if (-not ($selection -match '^\d+$')) {
        Write-ErrorMsg "Selección inválida."
        return
    }

    $index = [int]$selection - 1
    if ($index -lt 0 -or $index -ge $backupDirs.Count) {
        Write-ErrorMsg "Selección fuera de rango."
        return
    }

    $selectedBackup = $backupDirs[$index]
    Write-Warn "Se guardará una copia del estado actual y luego se reemplazarán los volúmenes por el backup elegido."
    $confirm = Read-Host "Escribe SI para continuar"
    if ($confirm -ne "SI") {
        Write-Info "Operación cancelada por el usuario."
        return
    }

    Write-Info "Deteniendo servicios para restauración consistente..."
    if (-not (Invoke-Docker "compose down" -Silent)) {
        Write-Warn "No se pudo detener completamente Docker Compose. Se intentará continuar."
    }

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $safetyBackup = Join-Path $backupRoot "pre_restore_$timestamp"
    New-Item -Path $safetyBackup -ItemType Directory -Force | Out-Null

    Write-Info "Guardando estado actual de volúmenes en: $safetyBackup"
    if (-not (Export-ManagedVolumes $safetyBackup)) {
        Write-ErrorMsg "No se pudo crear la copia de seguridad previa. Restauración cancelada para proteger datos."
        return
    }

    if (Import-ManagedVolumes $selectedBackup.FullName) {
        Write-Success "Backup restaurado correctamente desde: $($selectedBackup.Name)"
    } else {
        Write-ErrorMsg "Falló la restauración de volúmenes. El backup previo quedó guardado en: $safetyBackup"
    }

    Write-Info "Arrancando servicios nuevamente..."
    if (Invoke-Docker "compose up -d") {
        Write-Success "Servicios levantados correctamente."
    } else {
        Write-Warn "No se pudieron iniciar todos los servicios automáticamente."
    }
}

function Action-Credits {
    Write-Host "`n--- Créditos ---" -ForegroundColor Cyan
    Write-Host "Francisco Airam - Backend e Infraestructura" -ForegroundColor White
    Write-Host "Javier Remedios - Frontend e Integracion de IA" -ForegroundColor White
    Write-Host "Lorena Fumero - Maquetacion y UI/UX" -ForegroundColor White
    Write-Host "Javier Pascual - Apoyo emocional" -ForegroundColor White
}

# =============================================================================
# BUCLE DEL MENÚ
# =============================================================================

$envCheckPath = Join-Path $PWD ".env"

function Should-Run-Config {
    param([string]$envPath)
    if (-not (Test-Path $envPath)) { return $true }
    
    # Comprobar si faltan claves críticas para que no sea solo "que exista el archivo"
    $criticalKeys = @("SEED_ADMIN_NAME", "JWT_SECRET", "POSTGRES_PASSWORD", "LEDGER_HMAC_SECRET")
    $content = Get-Content $envPath
    foreach ($key in $criticalKeys) {
        $escapedKey = [regex]::Escape($key)
        if (-not ($content | Select-String "^${escapedKey}=")) { return $true }
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
    Write-Typewriter "  Acceso rápido al Panel de Control Smart Economato" 5 "Gray"
    Write-Host " ---------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " [ 1 ] 🟢 Iniciar Smart Economato" -ForegroundColor Green
    Write-Host " [ 2 ] 🔴 Apagar sistema" -ForegroundColor Red
    Write-Host " [ 3 ] 🔄 Reiniciar" -ForegroundColor Yellow
    Write-Host " [ 4 ] 🏥 Solucionar problemas" -ForegroundColor Cyan
    Write-Host " [ 5 ] 📋 Ver historial de actividad" -ForegroundColor Magenta
    Write-Host " [ 6 ] 💾 Crear copia de seguridad (Backup)" -ForegroundColor Blue
    Write-Host " [ 7 ] 📥 Cargar copia de seguridad" -ForegroundColor Blue
    Write-Host " [ 8 ] ⚖️  Reparar libro de stock" -ForegroundColor DarkYellow
    Write-Host " [ 9 ] 👥 Ver creditos" -ForegroundColor White
    Write-Host " [ 0 ] 🚪 Salir" -ForegroundColor DarkGray
    Write-Host " ---------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host " Hecho con ❤️  por el Grupo Turing del IES Domingo Pérez Minik" -ForegroundColor White
    Write-Host " ---------------------------------------------------------------" -ForegroundColor Cyan
    
    $choice = Read-Host " >> Selecciona una acción"
    
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
        '0' { Write-Host "Saliendo del Panel de Control... Ciao!"; exit }
        default { Write-ErrorMsg "Opción inválida." }
    }
}
