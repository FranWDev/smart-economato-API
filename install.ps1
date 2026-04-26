<#
.SYNOPSIS
Smart Economato - Panel de Control & Instalador para Windows Server
#>

$ErrorActionPreference = "Continue" # Evitar que avisos menores cierren el script automáticamente 
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Atrapador de errores global para evitar que la ventana se cierre sin aviso
trap {
    Write-Host "`n===============================================================" -ForegroundColor Red
    Write-Host " [ ERROR FATAL DEL SISTEMA ] " -ForegroundColor Red
    Write-Host " Mensaje: $($_.Exception.Message)" -ForegroundColor White
    Write-Host " Línea: $($_.InvocationInfo.ScriptLineNumber)" -ForegroundColor Gray
    Write-Host "===============================================================`n" -ForegroundColor Red
    Read-Host "Presiona ENTER para cerrar..."
    exit 1
}

# Forzar colores de terminal (Fondo negro, letras blancas) para estética profesional
try {
    $Host.UI.RawUI.BackgroundColor = "Black"
    $Host.UI.RawUI.ForegroundColor = "White"
    
    # Ajustar tamaño de ventana para que quepa todo el banner y menú
    $width = 120
    $height = 45
    if ($Host.UI.RawUI.WindowSize.Width -lt $width -or $Host.UI.RawUI.WindowSize.Height -lt $height) {
        $buffer = $Host.UI.RawUI.BufferSize
        $buffer.Width = [Math]::Max($width, $buffer.Width)
        $buffer.Height = [Math]::Max(3000, $buffer.Height) # Buffer grande para scroll
        $Host.UI.RawUI.BufferSize = $buffer
        
        $window = $Host.UI.RawUI.WindowSize
        $window.Width = $width
        $window.Height = $height
        $Host.UI.RawUI.WindowSize = $window
    }
    
    Clear-Host
} catch {}

# Detección de OS para compatibilidad con PS 5.1
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
        Write-Host " [AVISO] Se requieren permisos de administrador." -ForegroundColor Yellow
        Write-Host " Se abrirá una nueva ventana para continuar." -ForegroundColor Yellow
        Write-Host " (Para evitar esto, ejecuta tu terminal como administrador)" -ForegroundColor Gray
        Write-Host "---------------------------------------------------------------" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
        try {
            Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command `"& { try { . '$PSCommandPath' } catch { Write-Host $_; pause } }`"" -Verb RunAs
        } catch {
            Write-Error "No se pudo iniciar el proceso elevado: $($_.Exception.Message)"
            pause
        }
        exit
    }
}

# =============================================================================
# FUNCIONES DE UI Y UTILIDADES
# =============================================================================

function Write-Centered {
    param([string]$text, [string]$color = "White", [int]$width = 105)
    $lines = $text -split "`r?`n"
    foreach ($line in $lines) {
        $cleanLine = $line.Trim()
        if ($cleanLine.Length -eq 0) { Write-Host ""; continue }
        $padding = [Math]::Max(0, [Math]::Floor(($width - $line.Length) / 2))
        Write-Host (" " * $padding + $line) -ForegroundColor $color
    }
}
$script:BannerAnimated = $false
function Write-Header {
    param([switch]$Fast)
    
    # Forzar fondo negro y limpiar pantalla
    try {
        $Host.UI.RawUI.BackgroundColor = "Black"
        $Host.UI.RawUI.ForegroundColor = "White"
    } catch {}
    $esc = [char]27
    $bgBlack = "$esc[40m" # ANSI estándar para fondo negro
    $reset = "$esc[0m$bgBlack" # Reset + Forzar fondo negro
    Write-Host "$esc[48;2;0;0;0m" -NoNewline # TrueColor negro
    Clear-Host
    
    $screenWidth = 105
    $white = "$esc[38;2;255;255;255m"
    $customRed = "$esc[38;2;184;75;68m"
    $cyan = "$esc[36m"
    $starColor = "$esc[38;2;150;150;150m"

    $shouldAnimate = (-not $Fast) -and (-not $script:BannerAnimated)
    $delay = if ($shouldAnimate) { 15 } else { 0 }

    # Generar estrellas solo la primera vez para que queden "clavadas"
    if (-not $script:StaticTaglineMargins) {
        $stars = ""
        for ($i=0; $i -lt $screenWidth; $i++) {
            if ((Get-Random -Maximum 100) -lt 8) {
                $stars += ("*", ".", "+", "·")[(Get-Random -Maximum 4)]
            } else { $stars += " " }
        }
        $script:StaticTopStars = $stars
        
        $script:StaticSmartMargins = @()
        for ($i=0; $i -lt 8; $i++) {
            $s1 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $s2 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $script:StaticSmartMargins += @{ L=$s1; R=$s2 }
        }
        
        $script:StaticEconMargins = @()
        for ($i=0; $i -lt 7; $i++) {
            $s1 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $s2 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $script:StaticEconMargins += @{ L=$s1; R=$s2 }
        }
        
        $script:StaticTaglineMargins = @()
        for ($i=0; $i -lt 5; $i++) {
            $s1 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $s2 = if ((Get-Random -Maximum 20) -lt 1) { ("*", "·")[(Get-Random -Maximum 2)] } else { " " }
            $script:StaticTaglineMargins += @{ L=$s1; R=$s2 }
        }
    }

    Write-Host "$starColor$($script:StaticTopStars)$reset"

    Write-Host "$cyan >>=========================================================================================================<<$reset"
    if ($shouldAnimate) { 
        Start-Sleep -Milliseconds 50 
        $script:BannerAnimated = $true
    }

    $smart = @(
        "                       ________  _____ ______   ________  ________  _________                            ",
        "                      |\   ____\|\   _ \  _   \|\   __  \|\   __  \|\___   ___\                          ",
        "                      \ \  \___|\ \  \\\__\ \  \ \  \|\  \ \  \|\  \ \ \|\  \|                           ",
        "                       \ \_____  \ \  \\|__| \  \ \   __  \ \   _  _\   \ \  \                           ",
        "                        \|____|\  \ \  \    \ \  \ \  \ \  \ \  \\  \|   \ \  \                          ",
        "                          ____\_\  \ \__\    \ \__\ \__\ \__\ \__\\ _\    \ \__\                         ",
        "                         |\_________\|__|     \|__|\|__|\|__|\|__|\|__|    \|__|                         ",
        "                         \|_________|                                                                    "
    )
    for ($i=0; $i -lt $smart.Count; $i++) {
        $l = $smart[$i]
        $s1 = $script:StaticSmartMargins[$i].L
        $s2 = $script:StaticSmartMargins[$i].R
        Write-Host "$starColor$s1$reset$cyan||$white$l$cyan||$starColor$s2$reset"
        if ($delay -gt 0) { Start-Sleep -Milliseconds $delay }
    }

    # Lineas vacias intermedias
    for ($i=0; $i -lt 2; $i++) {
        $s1 = $script:StaticTaglineMargins[$i].L
        $s2 = $script:StaticTaglineMargins[$i].R
        Write-Host "$starColor$s1$reset$cyan||$white$(" " * 105)$cyan||$starColor$s2$reset"
    }
    
    $economato = @(
        "   _______   ________  ________  ________   ________  _____ ______   ________  _________  ________       ",
        "  |\  ___ \ |\   ____\|\   __  \|\   ___  \|\   __  \|\   _ \  _   \|\   __  \|\___   ___\\   __  \      ",
        "  \ \   __/|\ \  \___|\ \  \|\  \ \  \\ \  \ \  \|\  \ \  \\\__\ \  \ \  \|\  \|___ \  \_\ \  \|\  \     ",
        "   \ \  \_|/_\ \  \    \ \  \\\  \ \  \\ \  \ \  \\\  \ \  \\|__| \  \ \   __  \   \ \  \ \ \  \\\  \    ",
        "    \ \  \_|\ \ \  \____\ \  \\\  \ \  \\ \  \ \  \\\  \ \  \    \ \  \ \  \ \  \   \ \  \ \ \  \\\  \   ",
        "     \ \_______\ \_______\ \_______\ \__\\ \__\ \_______\ \__\    \ \__\ \__\ \__\   \ \__\ \ \_______\  ",
        "      \|_______|\|_______|\|_______|\|__| \|__|\|_______|\|__|     \|__|\|__|\|__|    \|__|  \|_______|  "
    )
    for ($i=0; $i -lt $economato.Count; $i++) {
        $l = $economato[$i]
        $s1 = $script:StaticEconMargins[$i].L
        $s2 = $script:StaticEconMargins[$i].R
        Write-Host "$starColor$s1$reset$cyan||$customRed$l$cyan||$starColor$s2$reset"
        if ($delay -gt 0) { Start-Sleep -Milliseconds $delay }
    }

    # Tagline y cierre dentro del banner
    $s1_1 = $script:StaticTaglineMargins[2].L; $s2_1 = $script:StaticTaglineMargins[2].R
    $s1_2 = $script:StaticTaglineMargins[3].L; $s2_2 = $script:StaticTaglineMargins[3].R
    $s1_3 = $script:StaticTaglineMargins[4].L; $s2_3 = $script:StaticTaglineMargins[4].R

    Write-Host "$starColor$s1_1$reset$cyan||$white$(" " * 105)$cyan||$starColor$s2_1$reset"
    $taglineText = "             Proyecto Smart Economato - Panel de Control y Mantenimiento de Producci$([char]0xF3)n v1.0              "
    Write-Host "$starColor$s1_2$reset$cyan||$customRed$taglineText$cyan||$starColor$s2_2$reset"
    Write-Host "$starColor$s1_3$reset$cyan||$white$(" " * 105)$cyan||$starColor$s2_3$reset"

    Write-Host "$cyan >>=========================================================================================================<<$reset"
    Write-Host ""
}

function Write-Typewriter {
    param([string]$msg, [int]$speed = 10, [string]$color = "White", [switch]$Centered)
    if ($Centered) {
        $width = 105
        $padding = [Math]::Max(0, [Math]::Floor(($width - $msg.Length) / 2))
        Write-Host (" " * $padding) -NoNewline
    }
    foreach ($char in $msg.ToCharArray()) {
        Write-Host $char -NoNewline -ForegroundColor $color
        Start-Sleep -Milliseconds (Get-Random -Minimum ($speed/2) -Maximum ($speed*2))
    }
    Write-Host ""
}

function Show-Spinner {
    param([string]$msg, [scriptblock]$action, [array]$ArgsList = @())
    $chars = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
    $job = Start-Job -ScriptBlock $action -ArgumentList (@($PWD) + $ArgsList)
    
    $prefix = "[  ....  ] "
    Write-Host $prefix -NoNewline -ForegroundColor Cyan
    Write-Host $msg -NoNewline -ForegroundColor White
    
    $i = 0
    while ($job.State -eq 'Running') {
        # Devolver el cursor al inicio de la línea (utilizando retorno de carro \r)
        Write-Host "`r" -NoNewline
        Write-Host "[   " -NoNewline -ForegroundColor Cyan
        Write-Host $chars[$i % $chars.Length] -NoNewline -ForegroundColor Yellow
        Write-Host "    ] " -NoNewline -ForegroundColor Cyan
        Write-Host $msg -NoNewline -ForegroundColor White
        $i++
        Start-Sleep -Milliseconds 80
    }
    
    $result = Receive-Job -Job $job -Wait
    $success = ($job.ChildJobs[0].Error.Count -eq 0)
    
    Write-Host "`r" -NoNewline
    if ($success) {
        Write-Host "[  DONE  ] " -NoNewline -ForegroundColor Green
    } else {
        Write-Host "[ ERROR  ] " -NoNewline -ForegroundColor Red
    }
    Write-Host "$msg                   " -ForegroundColor White
    
    Remove-Job $job
    return $result
}

$script:LogFile = Join-Path $PWD "smart-economato.log"

function Write-Log {
    param([string]$level, [string]$msg)
    if (Test-Path $script:LogFile) {
        $fileInfo = Get-Item $script:LogFile
        if ($fileInfo.Length -gt 10MB) {
            Rename-Item -Path $script:LogFile -NewName "$($script:LogFile).old" -Force
        }
    }
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
            Write-ErrorMsg "Docker falló: $err"
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
    $prompt = ">> Presiona ENTER para volver al men$([char]0xFA) principal..."
    Write-Typewriter $prompt 5 'Cyan' -Centered
    Read-Host | Out-Null
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

function Get-IsRunning {
    try {
        # Verificar si hay contenedores del proyecto en ejecución
        $running = docker compose -p smart-economato-api ps --filter "status=running" --quiet 2>$null
        if ($null -eq $running) { return $false }
        if ($running -is [array]) { return $running.Count -gt 0 }
        return ([string]::IsNullOrWhiteSpace($running) -eq $false)
    } catch {
        return $false
    }
}

# =============================================================================
# FUNCIONES PRINCIPALES DEL SISTEMA
# =============================================================================

function Ensure-HardwareRequirements {
    Write-Info "Verificando requisitos de hardware..."
    # Memoria RAM
    if ($script:IsWindowsOS) {
        try {
            $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
            $freeRamGB = [math]::Round($os.FreePhysicalMemory / 1024 / 1024, 2)
            $totalRamGB = [math]::Round($os.TotalVisibleMemorySize / 1024 / 1024, 2)
            
            if ($freeRamGB -lt 4) {
                Write-Warn "Poca memoria RAM libre ($freeRamGB GB). Docker y los contenedores podrían fallar."
            } elseif ($freeRamGB -lt 8) {
                Write-Info "RAM libre: $freeRamGB GB / $totalRamGB GB."
            } else {
                Write-Success "RAM libre: $freeRamGB GB."
            }
        } catch {
            Write-Warn "No se pudo verificar la memoria RAM."
        }
    }

    # Espacio en disco
    try {
        $drivePath = (Split-Path -Path $PWD -Qualifier)
        if (-not $drivePath) { $drivePath = "C:" }
        $drive = Get-PSDrive -Name $drivePath.Trim(':') -ErrorAction Stop
        $freeSpaceGB = [math]::Round($drive.Free / 1GB, 2)
        
        if ($freeSpaceGB -lt 10) {
            Write-Warn "Poco espacio en disco ($freeSpaceGB GB disponibles). Asegúrate de tener suficiente para imágenes de Docker y DB."
        } else {
            Write-Success "Espacio en disco: $freeSpaceGB GB disponibles."
        }
    } catch {
        Write-Warn "No se pudo verificar el espacio libre en disco."
    }
}

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
                Write-Warn "No se pudo crear automáticamente el archivo .wslconfig en su carpeta de usuario."
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
        Write-Warn "No se pudo añadir 'smart-economato' al archivo hosts (faltan permisos de administrador/root)."
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
        Write-Info "Generando certificados de seguridad (SSL) vía Docker..."
        $certCmd = "run --rm -v `"${PWD}/nginx/certs:/certs`" alpine sh -c `"apk add --no-cache openssl && openssl req -x509 -nodes -days 3650 -newkey rsa:2048 -keyout /certs/local.key -out /certs/local.crt -subj '/CN=localhost'`""
        if (Invoke-Docker $certCmd) {
            Write-Success "Certificados SSL generados correctamente."
        } else {
            Write-ErrorMsg "Falló la generación de certificados SSL."
        }
    }
}

function Fix-ShLineEndings {
    param([string]$filePath)
    if (Test-Path $filePath) {
        Write-Info "Asegurando formato Linux para script: $(Split-Path $filePath -Leaf)"
        $content = [System.IO.File]::ReadAllText($filePath)
        $content = $content.Replace("`r`n", "`n")
        # Escribir sin BOM y con LF
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($filePath, $content, $utf8NoBom)
    }
}

function Create-DesktopShortcut {
    try {
        $desktop = [Environment]::GetFolderPath('Desktop')
        $shortcutPath = Join-Path $desktop "Smart Economato.lnk"
        
        # 1. Intentar buscar el favicon del proyecto
        $iconPath = Join-Path $PWD "frontend\public\favicon.ico"
        
        $ws = New-Object -ComObject WScript.Shell
        $sc = $ws.CreateShortcut($shortcutPath)
        $sc.TargetPath = "https://smart-economato"
        $sc.Description = "Acceso r$([char]0xE1)pido a Smart Economato"
        
        # 2. Asignar icono (Favicon local o Globo de red del sistema)
        if (Test-Path $iconPath) {
            $sc.IconLocation = $iconPath
        } else {
            # Icono 13 de shell32.dll es el globo terr$([char]0xE1)queo de red
            $sc.IconLocation = "shell32.dll, 13"
        }
        
        $sc.Save()
        Write-Success "Acceso directo 'Smart Economato' creado en el escritorio con su logo."
    } catch {
        Write-Warn "No se pudo crear el acceso directo en el escritorio."
    }
}

function Ensure-DockerService {
    if ($script:IsWindowsOS) {
        Write-Info "Verificando estado de Docker..."
        try {
            $dockerSvc = Get-Service -Name "docker" -ErrorAction SilentlyContinue
            if ($null -eq $dockerSvc) {
                # Docker Desktop sin el servicio registrado como 'docker'
                if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
                    Write-Warn "Docker Desktop no parece estar ejecutándose. Intentando iniciarlo..."
                    $desktopPath = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"
                    if (Test-Path $desktopPath) {
                        Start-Process $desktopPath
                        Write-Info "Esperando a que Docker se inicie..."
                        $timeout = 60
                        while (-not (Invoke-Docker "version" -Silent) -and $timeout -gt 0) {
                            Start-Sleep -Seconds 2
                            $timeout -= 2
                        }
                    } else {
                        Write-ErrorMsg "No se encontró el ejecutable de Docker Desktop."
                    }
                }
            } else {
                if ($dockerSvc.Status -ne 'Running') {
                    Start-Service -Name "docker"
                    Write-Success "Servicio Docker iniciado."
                }
            }
        } catch {
            Write-Warn "Problema al gestionar Docker: $($_.Exception.Message)"
        }
        
        if (-not (Invoke-Docker "version" -Silent)) {
            Write-ErrorMsg "Docker no está respondiendo correctamente."
            return $false
        }
        return $true
    }
    return $true
}

$script:ProjectName = "turing-backend"

$script:ManagedVolumes = @(
    "${script:ProjectName}_postgres-data",
    "${script:ProjectName}_postgres-replica-data",
    "${script:ProjectName}_redis-data",
    "${script:ProjectName}_kafka-data",
    "${script:ProjectName}_prometheus-data",
    "${script:ProjectName}_grafana-data",
    "${script:ProjectName}_predictor-outbox-data",
    "${script:ProjectName}_uploads-data"
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
        $success = Show-Spinner "Sincronizando volumen: $vol" {
            param($p, $v, $a, $targetPath)
            $t = $targetPath -replace '\\', '/'
            
            $dockerArgs = @("run", "--rm", "-v", "${v}:/source", "-v", "${t}:/backup", "alpine", "tar", "czf", "/backup/${a}", "-C", "/source", ".")
            $process = Start-Process docker -ArgumentList $dockerArgs -NoNewWindow -Wait -PassThru -ErrorAction SilentlyContinue
            return ($process.ExitCode -eq 0)
        } -ArgsList @($vol, $archiveName, $targetDir)
        
        if (-not $success) {
            Write-ErrorMsg "No se pudo exportar el volumen $vol."
            return $false
        }
    }

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $manifestContent = "Backup Created: $timestamp" + [Environment]::NewLine + [Environment]::NewLine + "Volumes:" + [Environment]::NewLine + ($script:ManagedVolumes -join [Environment]::NewLine)
    Set-Content -Path (Join-Path $targetDir "manifest.txt") -Value $manifestContent
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

        $success = Show-Spinner "Restaurando volumen: $vol" {
            param($p, $v, $sourcePath, $a)
            $s = ($sourcePath -replace '\\', '/')
            
            # Comando directo sin arrays intermedios para evitar problemas de escape
            $cmd = "run --rm -v ${v}:/dest -v ${s}:/backup alpine sh -c `"rm -rf /dest/* 2>/dev/null; tar xzf /backup/${a} -C /dest`""
            $process = Start-Process docker -ArgumentList $cmd -NoNewWindow -Wait -PassThru -ErrorAction SilentlyContinue
            return ($process.ExitCode -eq 0)
        } -ArgsList @($vol, $sourceDir, $archiveName)

        if (-not $success) {
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
    Write-Header -Fast
    Write-Info "Ejecutando inicialización y despliegue de auto-configuración..."
    
    Ensure-HardwareRequirements

    # 0. Optimización de RAM
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
        Write-Host "`n--- Configuración inicial ---" -ForegroundColor Cyan
        Write-Host " Por favor, rellena los siguientes datos para crear el usuario administrador." -ForegroundColor Gray
        do {
            $adminName = Read-Host " Nombre completo"
            if ([string]::IsNullOrWhiteSpace($adminName)) { Write-Warn "El nombre no puede estar vacío." }
        } while ([string]::IsNullOrWhiteSpace($adminName))
        $adminName = Sanitize-EnvValue $adminName
        
        do {
            $adminUser = Read-Host " Nombre de usuario"
            if ([string]::IsNullOrWhiteSpace($adminUser)) { Write-Warn "El nombre de usuario no puede estar vacio." }
        } while ([string]::IsNullOrWhiteSpace($adminUser))
        $adminUser = Sanitize-EnvValue $adminUser
        
        do {
            $adminPass = Read-Host -AsSecureString " Contrase$([char]0xF1)a"
            $adminPassStr = [System.Net.NetworkCredential]::new("", $adminPass).Password
            if ($adminPassStr.Length -lt 8) {
                Write-Warn "La contraseña debe tener al menos 8 caracteres."
            }
        } while ($adminPassStr.Length -lt 8)
        $adminPassStr = Sanitize-EnvValue $adminPassStr
        
        Add-Content -Path $envPath -Value "SEED_ADMIN_NAME=`"$adminName`""
        Add-Content -Path $envPath -Value "SEED_ADMIN_USER=`"$adminUser`""
        Add-Content -Path $envPath -Value "SEED_ADMIN_PASSWORD=`"$adminPassStr`""

        # Sincronizar Grafana con el Jefe de Cocina (Capa de abstracción)
        Add-Content -Path $envPath -Value "GRAFANA_USER=`"$adminUser`""
        Add-Content -Path $envPath -Value "GRAFANA_PASSWORD=`"$adminPassStr`""
    }

    Set-EnvSecret $envPath "POSTGRES_DB" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_USER" 12 $false $true
    Set-EnvSecret $envPath "POSTGRES_PASSWORD" 32 $true
    Set-EnvSecret $envPath "JWT_SECRET" 128 $true
    Set-EnvSecret $envPath "LEDGER_HMAC_SECRET" 128 $true
    if (-not (Select-String -Path $envPath -Pattern "^JWT_EXPIRATION=" -Quiet)) { Add-Content -Path $envPath -Value "JWT_EXPIRATION=`"86400000`"" }

    # Configuración de IA (AI NEST)
    Set-EnvSecret $envPath "AI_NEST_SERVICE_KEY" 64 $true
    if (-not (Select-String -Path $envPath -Pattern "^AI_NEST_BASE_URL=" -Quiet)) { Add-Content -Path $envPath -Value "AI_NEST_BASE_URL=`"http://localhost:3001`"" }
    if (-not (Select-String -Path $envPath -Pattern "^AI_NEST_ALLOWED_ORIGIN=" -Quiet)) { Add-Content -Path $envPath -Value "AI_NEST_ALLOWED_ORIGIN=`"http://localhost:3000`"" }

    Write-Success "Las llaves de seguridad y contraseñas se han configurado correctamente."

    # 3. Volúmenes
    Ensure-ManagedVolumes
    Write-Success "Volúmenes persistentes mapeados."

    # 4. Tarea en Background (Arrancar contenedores junto a Windows)
    if ($script:IsWindowsOS) {
        try {
            # Se requiere Administrador
            $projectRoot = (Resolve-Path $PSScriptRoot).Path
            $action = New-ScheduledTaskAction -Execute "docker" -Argument "compose -f `"$projectRoot/docker-compose.yml`" up -d" -WorkingDirectory "$projectRoot"
            $trigger = New-ScheduledTaskTrigger -AtStartup
            $trigger2 = New-ScheduledTaskTrigger -AtLogOn
            $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -RunLevel Highest
            
            Register-ScheduledTask -TaskName "SmartEconomatoBackend" -Action $action -Trigger @($trigger, $trigger2) -Principal $principal -Description "Arranca los servicios docker de Economato." -Force | Out-Null
            Write-Success "Instalada Tarea de Arranque con Windows Task Scheduler."
        } catch {
            Write-Warn "No se pudo inyectar el Task Scheduler: $($_.Exception.Message)"
            Write-Warn "Aseg$([char]0xFA)rate de ejecutar este panel como Administrador en Producci$([char]0xF3)n."
        }
    }

    Write-Success "La configuración inicial se ha completado."
}


# =============================================================================
# COMANDOS DEL PANEL DE CONTROL
# =============================================================================

function Action-Start {
    Write-Header -Fast
    Write-Centered "--- Desplegando Smart Economato ---" "Cyan"
    
    if (Get-IsRunning) {
        Write-Warn "El sistema ya est$([char]0xE1) encendido y funcionando."
        return
    }
    
    Write-Info "Preparando entorno, certificados y vol$([char]0xFA)menes..."
    Ensure-ManagedVolumes
    Ensure-Certificates
    Fix-ShLineEndings (Join-Path $PWD "postgres-init.sh")
    # Detección de puertos interactiva
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
        Write-Warn "El puerto 443 está ocupado."
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
    
    # También setear en el proceso actual
    $env:PROXY_HTTP_PORT = $httpPort
    $env:PROXY_HTTPS_PORT = $httpsPort
    $env:NGINX_CONF_PATH = "./nginx/reverse-proxy.template"
    $env:AI_NEST_ALLOWED_ORIGIN = $realOrigin

    Write-Info "Desplegando contenedores Docker. Esto puede tardar varios minutos..."
    Write-Host "---------------------------------------------------------------" -ForegroundColor Gray
    & docker compose -p smart-economato-api up -d --build --wait
    $success = ($LASTEXITCODE -eq 0)
    Write-Host "---------------------------------------------------------------" -ForegroundColor Gray

    if ($success) {
        Write-Success "Sistema encendido!"
        
        # ---------------------------------------------------------
        # Insercion de productos en el primer despliegue
        # ---------------------------------------------------------
        if ((Test-Path "productos.sql") -and -not (Test-Path ".db_initialized")) {
            Write-Info "Primer despliegue detectado. Esperando inicialización de tablas por el backend..."
            
            # Obtener variables del .env de forma robusta
            $envLines = Get-Content $envPath
            $dbName = ($envLines | Where-Object { $_ -match "^POSTGRES_DB=" } | Select-Object -First 1)
            $dbUser = ($envLines | Where-Object { $_ -match "^POSTGRES_USER=" } | Select-Object -First 1)

            if ($dbName -match "=(.*)") { $dbName = $matches[1] -replace '"', '' }
            if ($dbUser -match "=(.*)") { $dbUser = $matches[1] -replace '"', '' }
            
            if ([string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser)) {
                Write-Warn "No se pudieron extraer las credenciales del .env. Se omitirá la carga de productos."
            } else {
                $timeout = 180
                $tableExists = $false
                while ($timeout -gt 0 -and -not $tableExists) {
                    try {
                        $result = docker exec -i inventory-postgres psql -U $dbUser -d $dbName -tAc "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'product');" 2>$null
                        if ($result -match "t") {
                            $tableExists = $true
                        }
                    } catch {}
                    
                    if (-not $tableExists) {
                        Start-Sleep -Seconds 5
                        $timeout -= 5
                    }
                }

                if ($tableExists) {
                    Write-Info "Tablas listas. Insertando catálogo de productos inicial..."
                    cmd.exe /c "docker exec -i inventory-postgres psql -U $dbUser -d $dbName -q < productos.sql"
                    if ($LASTEXITCODE -eq 0) {
                        Write-Success "Catálogo de productos insertado correctamente."
                        New-Item -ItemType File -Path ".db_initialized" -Force | Out-Null
                    } else {
                        Write-Warn "No se pudo insertar el SQL (Error de psql). El sistema seguirá funcionando pero sin datos iniciales."
                    }
                } else {
                    Write-Warn "Tiempo agotado esperando al backend. No se cargaron los productos iniciales."
                }
            }
        }
        # ---------------------------------------------------------


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
    Write-Header -Fast
    Write-Centered "--- Apagando Smart Economato ---" "Red"
    
    if (-not (Get-IsRunning)) {
        Write-Warn "El sistema ya est$([char]0xE1) apagado."
        return
    }

    Show-Spinner "Deteniendo servicios y liberando recursos" {
        param($p) cd $p; docker compose -p smart-economato-api down 2>$null
    }
}

function Action-Restart {
    Write-Header -Fast
    Write-Centered "--- Reiniciando Smart Economato ---" "Yellow"

    if (-not (Get-IsRunning)) {
        Write-Info "El sistema estaba apagado. Iniciando de cero..."
        Action-Start
        return
    }

    Show-Spinner "Reiniciando servicios del sistema" {
        param($p) cd $p; docker compose -p smart-economato-api restart 2>$null
    }
}

function Action-Health {
    Write-Header -Fast
    Write-Centered "--- Diagnóstico de Salud del Sistema ---" "Cyan"
    
    # Revisar contenedores detenidos y levantarlos automáticamente.
    Show-Spinner "Verificando estado de los servicios" {
        param($p) cd $p; return docker compose -p smart-economato-api ps --filter "status=exited" --format "{{.Names}}" 2>$null
    } | Set-Variable exited
    
    if (-not [string]::IsNullOrWhiteSpace($exited)) {
        Write-Warn "Se ha detectado que algunos servicios se detuvieron:"
        Write-Host $exited -ForegroundColor Yellow
        Show-Spinner "Intentando recuperación automática" {
            param($p) cd $p; docker compose -p smart-economato-api up -d 2>$null
        }
    }

    Write-Info "Escaneando registros en busca de errores recientes..."
    $logs = docker compose -p smart-economato-api logs --tail=15 2>&1
    if ($logs) {
        Write-Host "`n--- $([char]0xDA)LTIMOS LOGS DEL SISTEMA ---" -ForegroundColor DarkGray
        $logs | ForEach-Object { 
            $line = $_.ToString()
            $c = if ($line -match "ERROR|Fail|Critical") { "Red" } elseif ($line -match "WARN") { "Yellow" } else { "Gray" }
            Write-Host " > $line" -ForegroundColor $c 
        }
    } else {
        Write-Success "No se detectaron registros en los contenedores."
    }

    Write-Host "`n--- ESTADO ACTUAL DEL SISTEMA ---" -ForegroundColor Cyan
    docker compose -p smart-economato-api ps
    
    Write-Info "Verificando disponibilidad de la API del Backend (Healthcheck Externo)..."
    $healthStatus = docker inspect --format='{{.State.Health.Status}}' inventory-backend 2>$null
    if ($healthStatus -eq "healthy") {
        Write-Success "El servicio interno (Backend API) responde correctamente."
    } elseif ($healthStatus -eq "starting") {
        Write-Info "El servicio interno (Backend API) todavía se está iniciando..."
    } else {
        Write-Warn "El contenedor del backend está corriendo pero el servicio interno no reporta salud correcta ($healthStatus)."
        Write-Warn "Sugerencia: Usa la opción de 'Auto-reparar', 'Reiniciar' o revisa los logs."
    }
}

function Action-Panic {
    Write-Header -Fast
    Write-Centered "--- Limpieza Profunda de Docker ---" "Red"
    Write-Warn "Esta acci$([char]0xF3)n detendr$([char]0xE1) el sistema temporalmente para eliminar im$([char]0xE1)genes obsoletas, contenedores parados y cach$([char]0xE9) de compilaci$([char]0xF3)n."
    Write-Host "Los datos de la base de datos (vol$([char]0xFA)menes persistentes) NO se eliminar$([char]0xE1)n, pero es una operaci$([char]0xF3)n destructiva de entorno." -ForegroundColor Yellow
    
    $confirm = Read-Host "`n  $([char]0xBF)Est$([char]0xE1)s seguro de que quieres continuar? (S/N)"
    if ($confirm -match "^[SsYy]$") {
        Write-Info "Deteniendo servicios en curso..."
        docker compose -p smart-economato-api down 2>$null
        
        Write-Info "Iniciando purga de Docker (docker system prune -f)..."
        docker system prune -f
        
        Write-Info "Iniciando limpieza de cach$([char]0xE9) de compilaci$([char]0xF3)n (docker builder prune -f)..."
        docker builder prune -f
        
        Write-Success "Limpieza completada."
    } else {
        Write-Info "Operaci$([char]0xF3)n cancelada."
    }
}


function Action-Logs {
    Write-Header -Fast
    Write-Centered "--- Registro de Actividad en Tiempo Real ---" "Magenta"
    Write-Warn "Presiona CTRL+C para detener el seguimiento y volver al men$([char]0xFA)."
    Write-Host "---------------------------------------------------------------" -ForegroundColor Gray
    
    # El trap evita que el Ctrl+C detenga el script completo al interrumpir docker logs
    trap { continue }
    docker compose -p smart-economato-api logs --tail=50 -f
    
    Write-Info "Volviendo al men$([char]0xFA)..."
    Start-Sleep -Milliseconds 800
}

function Action-RepairBlockchain {
    Write-Header -Fast
    Write-Centered "--- Reparar Libro de Movimientos y Stock ---" "DarkYellow"
    Write-Host "Necesitamos confirmar que eres el responsable de cocina." -ForegroundColor White
    
    $username = Read-Host " Usuario"
    $password = Read-Host -AsSecureString " Contrase$([char]0xF1)a"
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
        }
        
        # Ejecutar directamente sin Spinner para evitar problemas de ambito (scope)
        Write-Info "Enviando solicitud de reparaci$([char]0xF3)n al servidor..."
        $rebuildRes = Invoke-RestMethod @repairParams

        Write-Success "¡El libro de movimientos ha sido reparado con $([char]0xE9)xito!"
    } catch {
        Write-ErrorMsg "Fallo al intentar reparar el libro de movimientos."
        
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd()
            Write-Warn "Respuesta del servidor: $responseBody"
        } else {
            Write-Warn "Error: $($_.Exception.Message)"
        }
        return
    }

    Write-Success "La operaci$([char]0xF3)n de mantenimiento ha finalizado correctamente."
}


function Action-Backup {
    Write-Header -Fast
    Write-Centered "--- Crear Copia de Seguridad ---" "Cyan"
    Write-Warn "Esta acción creará una copia de seguridad de todos los volúmenes de datos."
    $confirm = Read-Host " Confirmar (Escribe SI)"
    if ($confirm -ne "SI") {
        Write-Info "Operación cancelada por el usuario."
        return
    }

    Write-Info "Iniciando copia de seguridad de volumenes Docker..."

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
    Write-Header -Fast
    Write-Centered "--- Restaurar Copia de Seguridad ---" "Cyan"
    Write-Info "Buscando archivos de respaldo..."

    $backupRoot = Get-VolumeBackupRoot
    $backupDirs = Get-ChildItem -Path $backupRoot -Directory | Sort-Object Name -Descending
    if (-not $backupDirs -or $backupDirs.Count -eq 0) {
        Write-ErrorMsg "No hay backups de volúmenes disponibles."
        return
    }

    Write-Centered "--- Backups de vol$([char]0xFA)menes disponibles ---" "Cyan"
    for ($i = 0; $i -lt $backupDirs.Count; $i++) {
        $date = $backupDirs[$i].CreationTime.ToString("dd/MM/yyyy HH:mm:ss")
        $line = " [" + ($i + 1) + "] " + $backupDirs[$i].Name + " ($date)"
        Write-Centered $line "White"
    }
    Write-Centered " [ 0 ] Cancelar y volver" "DarkGray"
    Write-Host ""

    $p = " " * 33
    Write-Host ($p + "Selecciona el n$([char]0xFA)mero del backup: ") -NoNewline -ForegroundColor Cyan
    $selection = Read-Host
    
    if ($selection -eq "0" -or [string]::IsNullOrWhiteSpace($selection)) {
        Write-Info "Operaci$([char]0xF3)n cancelada."
        return
    }

    if (-not ($selection -match '^\d+$')) {
        Write-ErrorMsg "Selecci$([char]0xF3)n inv$([char]0xE1)lida."
        return
    }

    $index = [int]$selection - 1
    if ($index -lt 0 -or $index -ge $backupDirs.Count) {
        Write-ErrorMsg "Selecci$([char]0xF3)n fuera de rango."
        return
    }

    $selectedBackup = $backupDirs[$index]
    Write-Warn "Se guardar$([char]0xE1) una copia del estado actual y luego se reemplazar$([char]0xE1)n los vol$([char]0xFA)menes por el backup elegido."
    Write-Host " >> Confirmar restauraci$([char]0xF3)n (Escribe SI) " -NoNewline -ForegroundColor White
    $confirm = Read-Host
    if ($confirm -ne "SI") {
        Write-Info "Operaci$([char]0xF3)n cancelada por el usuario."
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
    if (Invoke-Docker "compose -p smart-economato-api up -d") {
        Write-Success "Servicios levantados correctamente."
    } else {
        Write-Warn "No se pudieron iniciar todos los servicios automaticamente."
    }
}

function Action-InstallCert {
    Write-Header -Fast
    Write-Centered "--- Instalaci$([char]0xF3)n de Certificado SSL de Confianza ---" "Cyan"
    
    $certPath = Join-Path $PWD "nginx\certs\local.crt"
    
    if (-not (Test-Path $certPath)) {
        Write-ErrorMsg "No se encuentra el certificado en $certPath. Ejecuta la opci$([char]0xF3)n 1 primero."
        return
    }
    
    Write-Info "Intentando instalar el certificado en el almac$([char]0xE9)n de Entidades de Confianza..."
    
    try {
        # Importar el certificado al almacen de Raiz del Equipo Local
        Import-Certificate -FilePath $certPath -CertStoreLocation Cert:\LocalMachine\Root -ErrorAction Stop
        Write-Success "Certificado instalado correctamente en el almac$([char]0xE9)n de Windows."
        Write-Success "El navegador ahora deber$([char]0xED)a confiar en https://localhost y smart-economato."
        Write-Info "Nota: Es posible que debas reiniciar completamente el navegador para ver el cambio."
    } catch {
        Write-ErrorMsg "No se pudo instalar el certificado: $($_.Exception.Message)"
        Write-Warn "Aseg$([char]0xFA)rate de estar ejecutando este panel como Administrador."
    }
}

function Action-Credits {
    Write-Header -Fast
    Write-Centered "--- Equipo de Desarrollo - Smart Economato ---" "Cyan"
    Write-Host ""
    
    $esc = [char]27
    $members = @(
        @{ 
            Name = "Francisco Airam"
            Role = "Backend, Infraestructura y Despliegue"
            GH = "https://github.com/FranWDev"
            LI = "https://www.linkedin.com/in/franciscohdezcrosa"
        },
        @{ 
            Name = "Javier Remedios"
            Role = "Frontend e Integraci$([char]0xF3)n de IA"
            GH = "https://github.com/user-ijavieh"
            LI = "https://www.linkedin.com/in/javier-remedios"
        },
        @{ 
            Name = "Lorena Fudel"
            Role = "Maquetaci$([char]0xF3)n y UI/UX"
            GH = "https://github.com/lorena-fudel"
            LI = "https://www.linkedin.com/in/lorenafumerodelgado"
        },
        @{ 
            Name = "Daniel Pascual"
            Role = "Apoyo y corrección de errores"
            GH = "https://github.com/blablabla277"
            LI = $null
        }
    )

    $others = $members | Where-Object { $_.Name -ne "Javier Pascual" }
    $pascual = $members | Where-Object { $_.Name -eq "Javier Pascual" }
    $finalList = ($others | Get-Random -Count $others.Count) + $pascual

    $padding = " " * 12
    foreach ($m in $finalList) {
        Write-Host ($padding + " $([char]0xBB) ") -NoNewline -ForegroundColor Gray
        Write-Host ($m.Name.PadRight(18)) -NoNewline -ForegroundColor Green
        Write-Host " | " -NoNewline -ForegroundColor DarkGray
        Write-Host $m.Role -ForegroundColor White
        
        Write-Host ($padding + "    ") -NoNewline
        
        # Enlaces (Interactivo + Texto plano para máxima compatibilidad)
        if ($m.GH) {
            $ghLink = "$esc]8;;$($m.GH)$([char]7)GitHub$esc]8;;$([char]7)"
            Write-Host "[$ghLink] " -NoNewline -ForegroundColor Cyan
            Write-Host "($($m.GH))" -ForegroundColor DarkGray
        }
        
        if ($m.LI) {
            Write-Host ($padding + "    ") -NoNewline
            $liLink = "$esc]8;;$($m.LI)$([char]7)LinkedIn$esc]8;;$([char]7)"
            Write-Host "[$liLink] " -NoNewline -ForegroundColor Blue
            Write-Host "($($m.LI))" -ForegroundColor DarkGray
        }
        Write-Host ""
    }
    
    Write-Centered "---------------------------------------------------------------" "DarkGray"
    Write-Centered "Hecho con <3 por el Grupo Turing (IES Domingo P$([char]0xE9)rez Minik)" "White"
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
    Configure-System
    Pause-Execution
}

$firstLoad = $true
while ($true) {
    Write-Header -Fast:(-not $firstLoad)
    $menuTitle = "Acceso r$([char]0xE1)pido al Panel de Control Smart Economato"
    Write-Typewriter $menuTitle 5 'Gray' -Centered
    Write-Centered '-------------------------------------------------------------------------------------' 'DarkGray'
    
    $menuWidth = 40
    $p = " " * [Math]::Max(0, [Math]::Floor((105 - $menuWidth) / 2))
    
    $options = @(
        @{ t = "[ 1 ]  Iniciar Smart Economato"; c = "Green" },
        @{ t = "[ 2 ]  Apagar sistema"; c = "Red" },
        @{ t = "[ 3 ]  Reiniciar sistema"; c = "Yellow" },
        @{ t = "[ 4 ]  Auto-reparar sistema"; c = "Cyan" },
        @{ t = "[ 5 ]  Ver actividad (Logs)"; c = "Magenta" },
        @{ t = "[ 6 ]  Crear copia de seguridad"; c = "Blue" },
        @{ t = "[ 7 ]  Cargar copia de seguridad"; c = "Blue" },
        @{ t = "[ 8 ]  Sincronizar stock"; c = "DarkYellow" },
        @{ t = "[ 9 ]  Ver cr$([char]0xE9)ditos"; c = "White" },
        @{ t = "[ S ]  Instalar Certificado SSL (Quitar aviso de privacidad)"; c = "Cyan" },
        @{ t = "[ P ]  Limpieza Profunda (Optimizar Docker)"; c = "Red" },
        @{ t = "[ 0 ]  Salir"; c = "DarkGray" }
    )

    foreach ($opt in $options) {
        Write-Host ($p + $opt.t) -ForegroundColor $opt.c
        if ($firstLoad) { Start-Sleep -Milliseconds 40 }
    }
    
    Write-Centered '-------------------------------------------------------------------------------------' 'Cyan'
    $footer = "Hecho por el Grupo Turing del IES Domingo P$([char]0xE9)rez Minik"
    Write-Centered $footer 'White'
    Write-Centered '-------------------------------------------------------------------------------------' 'Cyan'
    Write-Host ""
    
    $firstLoad = $false
    $prompt = " >> Selecciona una acci$([char]0xF3)n: "
    Write-Host $prompt -NoNewline -ForegroundColor White
    $choice = Read-Host
    
    if ($choice -ne "5" -and $choice -ne "0") {
        # Para cualquier accion que no sea salir o ver logs, forzamos que el banner no re-anime al volver
        # Pero el usuario dice "al cambiar de seccion", asi que lo seteamos aqui
    }
    
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
        's' { Action-InstallCert; Pause-Execution }
        'S' { Action-InstallCert; Pause-Execution }
        'p' { Action-Panic; Pause-Execution }
        'P' { Action-Panic; Pause-Execution }
        '0' { Write-Host "Saliendo del Panel de Control... $([char]0xA1)Ciao!"; exit }
        default { Write-ErrorMsg "Opci$([char]0xF3)n inv$([char]0xE1)lida." }
    }
}
