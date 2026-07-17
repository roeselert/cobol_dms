#!/bin/sh
# Container entrypoint: prepare the data volume, run the COBOL
# bootstrap (90BOOTW0 — creates the VSAM files as the CGI user, so
# ownership matches), then Apache in the foreground.
set -e

DATA_DIR="${DMS_DATA_DIR:-/data}"
export COB_FILE_PATH="$DATA_DIR/vsam"

mkdir -p "$COB_FILE_PATH"
chown -R www-data:www-data "$DATA_DIR"

su -s /bin/sh www-data -c "COB_FILE_PATH='$COB_FILE_PATH' /app/bin/dmsboot"

. /etc/apache2/envvars
# mod_cgid needs its socket directory before startup, or every CGI
# request answers 503
mkdir -p "$APACHE_RUN_DIR/socks"
exec apache2 -D FOREGROUND
