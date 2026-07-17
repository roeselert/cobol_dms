# GNU COBOL DMS — Apache httpd + CGI runtime (TARGET-ARCHITECTURE.md).
# Build context is the repository root:
#   docker build -f cobol/Dockerfile -t dms-cobol .
# Single stage: cobc compiles src/ at image build time; the runtime
# (libcob + BDB indexed files) ships with the gnucobol3 package.
FROM ubuntu:24.04

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        gnucobol3 apache2 curl \
 && rm -rf /var/lib/apt/lists/*

COPY src/ /build/src/

# one CGI executable (00HTTPC0 is the main program) + the bootstrap
RUN mkdir -p /app/cgi-bin /app/bin \
 && cobc -x -free -I /build/src -o /app/cgi-bin/dmsapi \
        /build/src/00HTTPC0.cob \
        /build/src/00JSONC0.cob /build/src/00JSONC1.cob \
        /build/src/00UUIDC0.cob \
        /build/src/10USERC0.cob /build/src/10AUTHC0.cob \
        /build/src/10AUDTE0.cob \
        /build/src/20ORGSE0.cob /build/src/20USRSE0.cob \
        /build/src/20MEMBE0.cob /build/src/20ORGSC0.cob \
        /build/src/20ORGSB0.cob /build/src/20MEMBB0.cob \
        /build/src/20USRSB0.cob \
 && cobc -x -free -I /build/src -o /app/bin/dmsboot \
        /build/src/90BOOTW0.cob \
        /build/src/20ORGSE0.cob /build/src/20USRSE0.cob \
        /build/src/20MEMBE0.cob /build/src/10AUDTE0.cob \
        /build/src/00UUIDC0.cob

# the JS frontend is kept as-is (green-screen re-skin is a later
# iteration) and served statically by Apache
COPY dms/src/main/resources/static/ /app/static/

COPY cobol/dms.conf /etc/apache2/sites-available/000-default.conf
COPY cobol/start.sh /app/start.sh
RUN a2enmod cgid env \
 && echo "Listen 7860" > /etc/apache2/ports.conf \
 && echo "ServerName dms-cobol" > /etc/apache2/conf-available/servername.conf \
 && a2enconf servername \
 && chmod +x /app/start.sh

ENV DMS_SECURITY_MODE=dev \
    DMS_BOOTSTRAP_ADMINS=admin@example.com \
    DMS_DATA_DIR=/data

EXPOSE 7860
VOLUME /data

HEALTHCHECK --interval=5s --timeout=5s --retries=30 \
    CMD curl -sf http://localhost:7860/api/v1/health || exit 1

CMD ["/app/start.sh"]
