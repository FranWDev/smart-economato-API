#!/bin/bash

# ==============================================================================
# TURING BACKEND - PLUG 'N PLAY INSTALLER
# ==============================================================================

# Colores y Formatos
RESET="\033[0m"
BOLD="\033[1m"
DIM="\033[2m"
RED="\033[31m"
GREEN="\033[32m"
YELLOW="\033[33m"
BLUE="\033[34m"
MAGENTA="\033[35m"
CYAN="\033[36m"

# Función para imprimir cabeceras
print_header() {
    clear
    echo -e "${CYAN}${BOLD}"
    echo "==============================================================="
    echo "  ╔╦╗╦ ╦╦═╗╦╔╗╔╔═╗   ╔╗ ╔═╗╔═╗╦╔═╔═╗╔╗╔╔╦╗  ╦╔╗╔╔═╗╔╦╗╔═╗╦    "
    echo "   ║ ║ ║╠╦╝║║║║║ ╦   ╠╩╗╠═╣║  ╠╩╗║╣ ║║║ ║   ║║║║╚═╗ ║ ╠═╣║    "
    echo "   ╩ ╚═╝╩╚═╩╝╚╝╚═╝   ╚═╝╩ ╩╚═╝╩ ╩╚═╝╝╚╝ ╩   ╩╝╚╝╚═╝ ╩ ╩ ╩╩═╝  "
    echo "==============================================================="
    echo -e "       Instalador Automatizado Plug 'n Play - v1.0.0       "
    echo -e "${RESET}"
}

# Funciones de logs visuales
log_info() { echo -e "${BLUE}[ INFO ]${RESET} $1"; }
log_success() { echo -e "${GREEN}[  OK  ]${RESET} $1"; }
log_warn() { echo -e "${YELLOW}[ WARN ]${RESET} $1"; }
log_error() { echo -e "${RED}[ ERROR ]${RESET} $1"; }

print_header
echo -e "${BOLD}Iniciando el proceso de configuración...${RESET}\n"

# -----------------------------------------------------------------------------
# 1. Comprobación de Dependencias
# -----------------------------------------------------------------------------
log_info "Comprobando dependencias del sistema..."

if ! command -v docker &> /dev/null; then
    log_error "Docker no está instalado. Instalalo antes de continuar."
    exit 1
fi
log_success "Docker detectado."

if docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
    log_success "Docker Compose plugin detectado."
elif docker-compose -v &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
    log_success "Docker Compose standalone detectado."
else
    log_error "Docker Compose no está instalado."
    exit 1
fi

if ! command -v openssl &> /dev/null; then
    log_error "OpenSSL no está instalado (requerido para generar claves y HTTPS)."
    exit 1
fi
log_success "OpenSSL detectado."

# -----------------------------------------------------------------------------
# 2. Generación del .env y Credenciales Seguras
# -----------------------------------------------------------------------------
echo ""
log_info "Configurando el archivo de variables de entorno (.env)..."

ENV_FILE=".env"
if [ ! -f "$ENV_FILE" ]; then
    log_warn "No se encontró un archivo .env. Se creará uno nuevo."
    touch $ENV_FILE
fi

# Función para generar valor aleatorio si no existe
generate_secret() {
    local key=$1
    local length=${2:-32}
    
    # Comprobar si la clave ya existe y no está vacía
    if grep -q "^${key}=" "$ENV_FILE" && [ -n "$(grep "^${key}=" "$ENV_FILE" | cut -d '=' -f2)" ]; then
        return
    fi

    # Generar secreto
    local secret=$(openssl rand -base64 $length | tr -dc 'a-zA-Z0-9!@#%^&*()_+?><' | fold -w $length | head -n 1)
    
    # Asegurar alfanumérico para usuarios/passwords de DB si es necesario
    if [[ "$key" == *"USER"* ]] || [[ "$key" == *"DB"* ]]; then
        secret=$(openssl rand -hex 8) # Más seguro para nombres
    fi
    if [[ "$key" == *"PASSWORD"* ]]; then
        secret=$(openssl rand -base64 16 | tr -dc 'a-zA-Z0-9') # Sin caracteres muy extraños que rompan JDBC
    fi

    # Borrar si existía pero estaba vacío y añadir
    sed -i "/^${key}=/d" "$ENV_FILE"
    echo "${key}=${secret}" >> "$ENV_FILE"
    log_success "Generado secreto seguro para: ${BOLD}${key}${RESET}"
}

# Generación guiada del seeder de administrador
echo -e "\n${BOLD}${CYAN}--- Configuración del Administrador Principal (Seeder) ---${RESET}"
if ! grep -q "^SEED_ADMIN_NAME=" "$ENV_FILE"; then
    read -p "Introduce el nombre del Administrador (ej. Administrador Global): " admin_name
    read -p "Introduce el nombre de usuario de login (ej. admin): " admin_user
    read -sp "Introduce la contraseña del administrador: " admin_pass
    echo ""
    echo "SEED_ADMIN_NAME=${admin_name:-Admin}" >> "$ENV_FILE"
    echo "SEED_ADMIN_USER=${admin_user:-admin}" >> "$ENV_FILE"
    echo "SEED_ADMIN_PASSWORD=${admin_pass:-admin1234}" >> "$ENV_FILE"
    log_success "Credenciales de Administrador configuradas."
else
    log_info "Credenciales de administrador base ya detectadas en .env."
fi

echo -e "\n${BOLD}${CYAN}--- Generando claves de sistema automáticamente ---${RESET}"
# Base de datos
generate_secret "POSTGRES_DB" 16
generate_secret "POSTGRES_USER" 16
generate_secret "POSTGRES_PASSWORD" 32
# Seguridad JWT
generate_secret "JWT_SECRET" 64
if ! grep -q "^JWT_EXPIRATION=" "$ENV_FILE"; then echo "JWT_EXPIRATION=86400000" >> "$ENV_FILE"; fi
# Grafana
generate_secret "GRAFANA_USER" 12
generate_secret "GRAFANA_PASSWORD" 24
# Blockchain/Ledger
generate_secret "LEDGER_HMAC_SECRET" 128

# -----------------------------------------------------------------------------
# 3. Creación de Volúmenes Externos Docker
# -----------------------------------------------------------------------------
echo ""
log_info "Configurando volúmenes persistentes de Docker..."

VOLUMES=(
    "turing-backend_postgres-data"
    "turing-backend_postgres-replica-data"
    "turing-backend_redis-data"
    "turing-backend_kafka-data"
    "turing-backend_prometheus-data"
    "turing-backend_grafana-data"
    "turing-backend_predictor-outbox-data"
    "turing-backend_uploads-data"
)

for vol in "${VOLUMES[@]}"; do
    if ! docker volume ls -q | grep -q "^${vol}$"; then
        docker volume create "$vol" > /dev/null
        log_success "Volumen creado: $vol"
    else
        log_info "Volumen existente: $vol"
    fi
done

# -----------------------------------------------------------------------------
# 4. Certificación Local HTTPS
# -----------------------------------------------------------------------------
echo ""
log_info "Generando certificados HTTPS locales..."

CERTS_DIR="./nginx/certs"
mkdir -p "$CERTS_DIR"

if [ ! -f "$CERTS_DIR/local.crt" ] || [ ! -f "$CERTS_DIR/local.key" ]; then
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout "$CERTS_DIR/local.key" \
        -out "$CERTS_DIR/local.crt" \
        -subj "/C=ES/ST=Madrid/L=Madrid/O=Turing/OU=IT/CN=localhost" 2>/dev/null
    log_success "Certificados SSL auto-firmados generados en $CERTS_DIR/"
else
    log_info "Los certificados HTTPS ya existen."
fi

# -----------------------------------------------------------------------------
# 5. Configuración de Inicio Automático (Junto al servidor)
# -----------------------------------------------------------------------------
echo ""
log_info "Configurando arranque automático con el sistema..."

# Detectar OS
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Estamos en Linux (Arch, Ubuntu, etc.)
    read -p "¿Deseas instalar un servicio Systemd para que inicie con el sistema? (s/N): " setup_systemd
    if [[ "$setup_systemd" =~ ^[sS]$ ]]; then
        SERVICE_FILE="/etc/systemd/system/turing-backend.service"
        CURRENT_DIR=$(pwd)
        log_warn "Se requieren permisos de administrador para crear el servicio..."
        sudo bash -c "cat > $SERVICE_FILE" <<EOF
[Unit]
Description=Turing Backend Docker Compose
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=true
WorkingDirectory=$CURRENT_DIR
ExecStart=/usr/bin/env $DOCKER_COMPOSE_CMD up -d
ExecStop=/usr/bin/env $DOCKER_COMPOSE_CMD down

[Install]
WantedBy=default.target
EOF
        sudo systemctl daemon-reload
        sudo systemctl enable turing-backend.service
        log_success "Servicio Linux Systemd instalado y activado ('turing-backend.service')."
    fi

elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    # Estamos en Windows (Git Bash o similar)
    log_info "Entorno Windows detectado. Se ha generado el script 'setup-windows-task.ps1'."
    cat > setup-windows-task.ps1 <<EOF
# Script PowerShell para crear tarea programada en Windows Server
\$Action = New-ScheduledTaskAction -Execute "docker" -Argument "compose -f $(pwd)/docker-compose.yml up -d" -WorkingDirectory "$(pwd)"
\$Trigger = New-ScheduledTaskTrigger -AtStartup
\$Principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName "TuringBackendStartup" -Action \$Action -Trigger \$Trigger -Principal \$Principal -Description "Inicia los contenedores de Turing Backend al arrancar el servidor Windows."
Write-Host "Tarea programada instalada. El proyecto se iniciará con Windows Server." -ForegroundColor Green
EOF
    log_success "Ejecuta 'setup-windows-task.ps1' como Administrador desde PowerShell en tu Windows Server."
fi


# -----------------------------------------------------------------------------
# 6. Finalización y Puesta en Marcha
# -----------------------------------------------------------------------------
echo ""
echo -e "${GREEN}${BOLD}¡Configuración completada con éxito!${RESET}"
echo -e "Las claves y credenciales están guardadas de forma segura en: ${CYAN}.env${RESET}"
echo ""

read -p "¿Deseas levantar e inicializar el proyecto ahora mismo? (S/n): " start_now
if [[ ! "$start_now" =~ ^[nN]$ ]]; then
    log_info "Construyendo y levantando contenedores..."
    $DOCKER_COMPOSE_CMD up -d --build
    echo ""
    log_success "Proyecto levantado. Puedes ver los logs con: ${BOLD}$DOCKER_COMPOSE_CMD logs -f${RESET}"
else
    log_info "Puedes iniciar el servidor manualmente usando: ${BOLD}$DOCKER_COMPOSE_CMD up -d${RESET}"
fi

echo -e "\n${BOLD}${CYAN}¡La instalación Plug 'n Play ha finalizado!${RESET}\n"
