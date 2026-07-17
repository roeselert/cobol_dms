"""PDF/A normalization with OCR — the Python port of the Java `PdfaConversion`.

Routing by MIME type, with the same fallback ladder:
  application/pdf -> ocrmypdf (skip existing text, PDF/A output)
                     -> ghostscript -dPDFA=2 -> passthrough unchanged
  image/*         -> ocrmypdf (image-dpi 300, OCR into searchable PDF/A)
                     -> LibreOffice text-less embedding
  everything else -> LibreOffice headless -> PDF

ocrmypdf runs in-process as a library; Ghostscript and LibreOffice are
subprocesses with a hard 180 s timeout each. A missing tool falls through to
the next rung instead of failing. Text extraction happens on the result while
the scratch dir is still alive, so the caller gets bytes + text in one go.
"""

import importlib.util
import logging
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from .textextract import extract_text

log = logging.getLogger(__name__)

PROCESS_TIMEOUT_SECONDS = 180


class ConversionFailedError(Exception):
    """Deterministic conversion failure (corrupt input, unconvertible format)."""


class ToolTimeoutError(ConversionFailedError):
    """A tool exceeded the per-process timeout."""


@dataclass
class ConversionOutput:
    pdf: bytes
    text: str
    producer: str  # ocrmypdf | ghostscript | libreoffice | passthrough
    ocr_applied: bool


def convert(filename: str, mime_type: str, original: bytes) -> ConversionOutput:
    with tempfile.TemporaryDirectory(prefix="conv-") as tmp:
        work = Path(tmp)
        input_path = work / _sanitize(filename)
        input_path.write_bytes(original)

        if mime_type == "application/pdf":
            result_path, producer, ocr = _normalize_pdf(input_path, work)
        elif mime_type and mime_type.startswith("image/"):
            result_path, producer, ocr = _convert_image(input_path, work)
        else:
            result_path, producer, ocr = _convert_with_libreoffice(input_path, work)

        return ConversionOutput(
            pdf=result_path.read_bytes(),
            text=extract_text(result_path),
            producer=producer,
            ocr_applied=ocr,
        )


def _normalize_pdf(input_path: Path, work: Path) -> tuple[Path, str, bool]:
    output = work / "out-pdfa.pdf"
    if _ocrmypdf_available() and _ocrmypdf(input_path, output, skip_text=True):
        return output, "ocrmypdf", True
    if _ghostscript_available() and _run(
            work, "gs", "-dPDFA=2", "-dBATCH", "-dNOPAUSE",
            "-sColorConversionStrategy=UseDeviceIndependentColor",
            "-sDEVICE=pdfwrite", "-o", str(output), str(input_path)):
        return output, "ghostscript", False
    if _ocrmypdf_available() or _ghostscript_available():
        # the toolchain exists but rejected the file (corrupt/encrypted PDF):
        # fail the job instead of silently archiving a non-PDF/A rendition
        raise ConversionFailedError(
            f"PDF/A toolchain rejected {input_path.name} (corrupt or encrypted PDF?)")
    log.warning("no PDF/A toolchain available, passing PDF through unnormalized: %s", input_path.name)
    return input_path, "passthrough", False


def _ocrmypdf_available() -> bool:
    return importlib.util.find_spec("ocrmypdf") is not None


def _ghostscript_available() -> bool:
    return shutil.which("gs") is not None


def _convert_image(input_path: Path, work: Path) -> tuple[Path, str, bool]:
    """Images carry no text layer, so OCR them into a searchable PDF/A; without
    the OCR toolchain fall back to a text-less LibreOffice embedding."""
    output = work / "out-pdfa.pdf"
    if _ocrmypdf(input_path, output, image_dpi=300):
        return output, "ocrmypdf", True
    log.warning("no OCR toolchain available, embedding image without a text layer: %s", input_path.name)
    return _convert_with_libreoffice(input_path, work)


def _convert_with_libreoffice(input_path: Path, work: Path) -> tuple[Path, str, bool]:
    profile = work / "lo-profile"
    ok = _run(work, "libreoffice", "--headless", "--norestore",
              f"-env:UserInstallation=file://{profile.absolute()}",
              "--convert-to", "pdf", "--outdir", str(work), str(input_path))
    output = work / (input_path.stem + ".pdf")
    if not ok or not output.exists():
        raise ConversionFailedError(f"LibreOffice conversion failed for {input_path.name}")
    return output, "libreoffice", False


def _ocrmypdf(input_path: Path, output: Path, **kwargs) -> bool:
    """Run ocrmypdf in-process; any failure (missing Tesseract/Ghostscript,
    unreadable input, ...) falls through to the next rung, mirroring the
    Java ladder where a failed tool returns false rather than raising."""
    try:
        import ocrmypdf
    except ImportError:
        log.debug("ocrmypdf not installed")
        return False
    try:
        ocrmypdf.ocr(input_path, output, output_type="pdfa", progress_bar=False, **kwargs)
        return True
    except Exception as e:  # noqa: BLE001 — every ocrmypdf failure means "try the next tool"
        log.info("ocrmypdf failed (%s: %s), falling back", type(e).__name__, e)
        return False


def _run(work: Path, *cmd: str) -> bool:
    try:
        process = subprocess.run(
            cmd, cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            timeout=PROCESS_TIMEOUT_SECONDS,
        )
    except FileNotFoundError:
        log.debug("tool not available: %s", cmd[0])
        return False
    except subprocess.TimeoutExpired as e:
        raise ToolTimeoutError(f"{cmd[0]} timed out") from e
    if process.returncode != 0:
        head = process.stdout[:500].decode("utf-8", errors="replace") if process.stdout else ""
        log.info("%s exited %d: %s", cmd[0], process.returncode, head)
        return False
    return True


def _sanitize(filename: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", filename or "")
    return safe if safe.strip("._") else "input"
