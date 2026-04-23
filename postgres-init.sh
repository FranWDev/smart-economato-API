#!/bin/sh
set -e
# Permitir conexiones de replicacion sin cifrado (entorno de desarrollo)
echo "host replication all 0.0.0.0/0 trust" >> "${PGDATA}/pg_hba.conf"
pg_ctl reload -D "${PGDATA}" 2>/dev/null || true
