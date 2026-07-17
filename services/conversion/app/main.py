"""DMS conversion service: normalizes an uploaded document to PDF/A (with OCR)
and returns the result plus its plain text in one response.

POST /convert  (multipart: file, mimeType; bearer service token)
  200 -> {"pdfBase64": ..., "text": ..., "producer": ..., "ocrApplied": ...}
GET  /healthz  (unauthenticated; reports tool availability)
"""

import base64
import importlib.util
import shutil

from fastapi import Depends, FastAPI, File, Form, UploadFile

from .auth import require_service_token
from .convert import ConversionFailedError, ToolTimeoutError, convert
from .errors import ApiError, install_error_handlers

app = FastAPI(title="DMS Conversion Service", version="1.0.0")
install_error_handlers(app)


@app.get("/healthz")
def healthz() -> dict:
    return {
        "status": "ok",
        "tools": {
            "ocrmypdf": importlib.util.find_spec("ocrmypdf") is not None,
            "ghostscript": shutil.which("gs") is not None,
            "libreoffice": shutil.which("libreoffice") is not None,
            "pdftotext": shutil.which("pdftotext") is not None,
        },
    }


@app.post("/convert", dependencies=[Depends(require_service_token)])
def convert_endpoint(file: UploadFile = File(...), mimeType: str = Form(...)) -> dict:
    original = file.file.read()
    if not original:
        raise ApiError(400, "bad_request", "empty file")
    try:
        result = convert(file.filename or "input", mimeType, original)
    except ToolTimeoutError as e:
        raise ApiError(504, "timeout", str(e)) from e
    except ConversionFailedError as e:
        raise ApiError(422, "conversion_failed", str(e)) from e
    return {
        "pdfBase64": base64.b64encode(result.pdf).decode("ascii"),
        "text": result.text,
        "producer": result.producer,
        "ocrApplied": result.ocr_applied,
    }
