#!/bin/bash
set -e
# Permitir conexiones de replicación sin cifrado (entorno de desarrollo)
# Se añade la regla de replicación confiable para todo host
echo "host replication all 0.0.0.0/0 trust" >> "${PGDATA}/pg_hba.conf"
pg_ctl reload -D "${PGDATA}" 2>/dev/null || true
