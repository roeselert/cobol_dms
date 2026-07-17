# DMS conversion service: FastAPI + the PDF/A/OCR toolchain (ocrmypdf via pip,
# ghostscript/tesseract/libreoffice/poppler via apt). Runs in its own HF Docker
# Space (port 7860) or anywhere else.
FROM python:3.12-slim-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    ghostscript tesseract-ocr tesseract-ocr-deu \
    libreoffice-core libreoffice-writer poppler-utils \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-cache-dir .

# run as non-root with uid 1000: HF Spaces mounts persistent storage writable
# by uid 1000 only; remove any existing owner of that uid first.
RUN existing="$(getent passwd 1000 | cut -d: -f1)" \
 && if [ -n "$existing" ]; then userdel "$existing"; fi \
 && useradd --create-home --uid 1000 dms \
 && chown -R dms:dms /app
USER dms

EXPOSE 7860
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7860"]
