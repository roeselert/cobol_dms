#!/bin/sh
# Container entrypoint: prepare the data volume, run the COBOL
# bootstrap (90BOOTW0 — creates the VSAM files as the CGI user, so
# ownership matches), then Apache in the foreground.
set -e

DATA_DIR="${DMS_DATA_DIR:-/data}"
export COB_FILE_PATH="$DATA_DIR/vsam"

mkdir -p "$COB_FILE_PATH" "$DATA_DIR/objects/tmp"
chown -R www-data:www-data "$DATA_DIR"

su -s /bin/sh www-data -c "COB_FILE_PATH='$COB_FILE_PATH' /app/bin/dmsboot"

# the ingest worker daemon (§7.5): a long-running GnuCOBOL process that
# polls the VSAM job queue and drives OCR-only conversion in-process.
# Runs as the CGI user so it shares ownership of the VSAM + object files;
# reads the same DMS_DATA_DIR / DMS_WORKER_* / DMS_OCR_LANG env.
su -s /bin/sh www-data -c "COB_FILE_PATH='$COB_FILE_PATH' \
    DMS_DATA_DIR='$DATA_DIR' DMS_OCR_LANG='${DMS_OCR_LANG:-eng}' \
    DMS_AI_URL='${DMS_AI_URL:-}' DMS_AI_TOKEN='${DMS_AI_TOKEN:-}' \
    DMS_AI_MODEL='${DMS_AI_MODEL:-}' \
    DMS_AI_LOG_REQUEST='${DMS_AI_LOG_REQUEST:-}' \
    /app/bin/dmsworker" &

. /etc/apache2/envvars
# mod_cgid needs its socket directory before startup, or every CGI
# request answers 503
mkdir -p "$APACHE_RUN_DIR/socks"
exec apache2 -D FOREGROUND
