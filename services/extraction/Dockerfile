# DMS extraction service: pure HTTP service (FastAPI -> OpenAI-compatible API),
# no OS toolchain. Runs in its own HF Docker Space (port 7860) or anywhere else.
FROM python:3.12-slim-bookworm

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
