# GNU COBOL DMS — Apache httpd + CGI runtime (TARGET-ARCHITECTURE.md).
# Build context is the repository root:
#   docker build -f cobol/Dockerfile -t dms-cobol .
# Single stage: cobc compiles src/ at image build time; the runtime
# (libcob + BDB indexed files) ships with the gnucobol3 package.
FROM ubuntu:24.04

# gnucobol3 + apache/curl for the CGI runtime; ocrmypdf + tesseract
# (+ German language data) and poppler-utils' pdftotext for the OCR-only
# conversion worker (D-9). ocrmypdf pulls ghostscript/qpdf transitively
# as its OCR engine — those are never invoked directly here (no PDF/A).
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        gnucobol3 apache2 curl \
        ocrmypdf tesseract-ocr tesseract-ocr-deu poppler-utils \
        libcurl4-openssl-dev \
 && rm -rf /var/lib/apt/lists/*

COPY src/ /build/src/

# one CGI executable (00HTTPC0 is the main program) + the bootstrap.
# The explicit file lists ARE the authoritative build manifest (CLAUDE.md).
RUN mkdir -p /app/cgi-bin /app/bin \
 && cobc -x -free -I /build/src -o /app/cgi-bin/dmsapi \
        /build/src/00HTTPC0.cob \
        /build/src/00JSONC0.cob /build/src/00JSONC1.cob \
        /build/src/00UUIDC0.cob /build/src/00TIMEC0.cob \
        /build/src/00STORC0.cob /build/src/00MPARC0.cob \
        /build/src/10USERC0.cob /build/src/10AUTHC0.cob \
        /build/src/10AUDTE0.cob \
        /build/src/20ORGSE0.cob /build/src/20USRSE0.cob \
        /build/src/20MEMBE0.cob /build/src/20ORGSC0.cob \
        /build/src/20ORGSB0.cob /build/src/20MEMBB0.cob \
        /build/src/20USRSB0.cob \
        /build/src/30DOCSE0.cob /build/src/30STATE0.cob \
        /build/src/30RENDE0.cob /build/src/30AKTEE0.cob \
        /build/src/30METAE0.cob /build/src/30FPRFE0.cob \
        /build/src/30ORDBE0.cob /build/src/30INTNE0.cob \
        /build/src/30CLASE0.cob /build/src/50JOBSE0.cob \
        /build/src/30VOCAC0.cob /build/src/30AKTBC0.cob \
        /build/src/30METAC0.cob \
        /build/src/30DOCSB0.cob /build/src/30METAB0.cob \
        /build/src/30AKTEB0.cob /build/src/30CLASB0.cob \
        /build/src/30CONFB0.cob /build/src/50JOBSB0.cob \
 && cobc -x -free -I /build/src -Q -Wl,--no-as-needed \
        -o /app/bin/dmsworker \
        /build/src/50JOBSW0.cob /build/src/50CONVC0.cob \
        /build/src/50JOBSE0.cob /build/src/30STATE0.cob \
        /build/src/30RENDE0.cob /build/src/30DOCSE0.cob \
        /build/src/00STORC0.cob /build/src/00UUIDC0.cob \
        /build/src/60EXTRC0.cob /build/src/60CURLC0.cob \
        /build/src/60INTNE0.cob /build/src/60INFDE0.cob \
        /build/src/60ORDTE0.cob /build/src/30CLASE0.cob \
        /build/src/30METAE0.cob /build/src/30FPRFE0.cob \
        /build/src/30ORDBE0.cob /build/src/30INTNE0.cob \
        /build/src/00JSONC0.cob -lcurl \
 && cobc -x -free -I /build/src -o /app/bin/dmsboot \
        /build/src/90BOOTW0.cob \
        /build/src/00UUIDC0.cob /build/src/00STORC0.cob \
        /build/src/10AUDTE0.cob \
        /build/src/20ORGSE0.cob /build/src/20USRSE0.cob \
        /build/src/20MEMBE0.cob \
        /build/src/30DOCSE0.cob /build/src/30STATE0.cob \
        /build/src/30RENDE0.cob /build/src/30AKTEE0.cob \
        /build/src/30METAE0.cob /build/src/30FPRFE0.cob \
        /build/src/30ORDBE0.cob /build/src/30INTNE0.cob \
        /build/src/30CLASE0.cob /build/src/50JOBSE0.cob \
        /build/src/60INTNE0.cob /build/src/60INFDE0.cob \
        /build/src/60ORDTE0.cob

# the JS frontend (kept per rule 2, re-skinned as a green screen) lives
# at the repo root now that the Java module is decommissioned
COPY web/ /app/static/

COPY cobol/dms.conf /etc/apache2/sites-available/000-default.conf
COPY cobol/start.sh /app/start.sh
RUN a2enmod cgid env \
 && echo "Listen 7860" > /etc/apache2/ports.conf \
 && echo "ServerName dms-cobol" > /etc/apache2/conf-available/servername.conf \
 && a2enconf servername \
 && chmod +x /app/start.sh

ENV DMS_SECURITY_MODE=dev \
    DMS_BOOTSTRAP_ADMINS=admin@example.com \
    DMS_DATA_DIR=/data \
    DMS_UPLOAD_MAX_BYTES=104857600 \
    DMS_OCR_LANG=deu+eng \
    DMS_WORKER_ENABLED=true \
    DMS_WORKER_POLL_MILLIS=2000 \
    DMS_WORKER_BATCH_SIZE=2 \
    DMS_WORKER_MAX_ATTEMPTS=5 \
    DMS_WORKER_BACKOFF_BASE_MILLIS=5000 \
    DMS_WORKER_LEASE_SECONDS=300

EXPOSE 7860
VOLUME /data

HEALTHCHECK --interval=5s --timeout=5s --retries=30 \
    CMD curl -sf http://localhost:7860/api/v1/health || exit 1

CMD ["/app/start.sh"]
