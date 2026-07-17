"""Plain-text extraction from the converted PDF (port of the Java `PdfTransport`):
pdftotext -layout first, Ghostscript txtwrite as fallback, empty string when
neither works — a scanned page whose OCR produced nothing is still a valid
conversion, so text failure is never an error. The text is returned in full;
any truncation for AI token budgets happens in the extraction service."""

import logging
import subprocess
from pathlib import Path

log = logging.getLogger(__name__)

TOOL_TIMEOUT_SECONDS = 120


def extract_text(pdf_path: Path) -> str:
    text = _run_text("pdftotext", "-layout", str(pdf_path), "-")
    if text is not None and text.strip():
        return text.strip()
    text = _run_text("gs", "-dBATCH", "-dNOPAUSE", "-q", "-sDEVICE=txtwrite", "-o", "-", str(pdf_path))
    if text is not None:
        return text.strip()
    return ""


def _run_text(*cmd: str) -> str | None:
    try:
        process = subprocess.run(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=TOOL_TIMEOUT_SECONDS,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None
    if process.returncode != 0:
        return None
    return process.stdout.decode("utf-8", errors="replace")
