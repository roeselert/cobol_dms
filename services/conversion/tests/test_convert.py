"""Routing and fallback-ladder tests with the tools mocked out — mirrors the
behavior contract of the deleted Java PdfaConversion."""

import subprocess
from pathlib import Path

import pytest

from app import convert as conv

MINIMAL_PDF = b"%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF"


@pytest.fixture(autouse=True)
def no_text_extraction(monkeypatch):
    monkeypatch.setattr(conv, "extract_text", lambda _path: "extracted text")


def test_pdf_passes_through_when_no_toolchain(monkeypatch):
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: False)
    monkeypatch.setattr(conv, "_ghostscript_available", lambda: False)
    result = conv.convert("doc.pdf", "application/pdf", MINIMAL_PDF)
    assert result.pdf == MINIMAL_PDF
    assert result.producer == "passthrough"
    assert result.ocr_applied is False
    assert result.text == "extracted text"


def test_pdf_rejected_by_present_toolchain_raises(monkeypatch):
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: True)
    monkeypatch.setattr(conv, "_ghostscript_available", lambda: True)
    monkeypatch.setattr(conv, "_ocrmypdf", lambda *_a, **_k: False)
    monkeypatch.setattr(conv, "_run", lambda *_a, **_k: False)
    with pytest.raises(conv.ConversionFailedError):
        conv.convert("encrypted.pdf", "application/pdf", MINIMAL_PDF)


def test_pdf_prefers_ocrmypdf(monkeypatch):
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: True)
    def fake_ocrmypdf(_input, output, **kwargs):
        assert kwargs.get("skip_text") is True
        Path(output).write_bytes(b"%PDF-ocrmypdf")
        return True

    monkeypatch.setattr(conv, "_ocrmypdf", fake_ocrmypdf)
    result = conv.convert("doc.pdf", "application/pdf", MINIMAL_PDF)
    assert result.pdf == b"%PDF-ocrmypdf"
    assert result.producer == "ocrmypdf"
    assert result.ocr_applied is True


def test_pdf_falls_back_to_ghostscript(monkeypatch):
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: True)
    monkeypatch.setattr(conv, "_ghostscript_available", lambda: True)
    monkeypatch.setattr(conv, "_ocrmypdf", lambda *_a, **_k: False)

    def fake_run(_work, *cmd):
        assert cmd[0] == "gs"
        Path(cmd[cmd.index("-o") + 1]).write_bytes(b"%PDF-gs")
        return True

    monkeypatch.setattr(conv, "_run", fake_run)
    result = conv.convert("doc.pdf", "application/pdf", MINIMAL_PDF)
    assert result.pdf == b"%PDF-gs"
    assert result.producer == "ghostscript"


def test_image_ocr_uses_image_dpi(monkeypatch):
    def fake_ocrmypdf(_input, output, **kwargs):
        assert kwargs.get("image_dpi") == 300
        Path(output).write_bytes(b"%PDF-image")
        return True

    monkeypatch.setattr(conv, "_ocrmypdf", fake_ocrmypdf)
    result = conv.convert("scan.png", "image/png", b"png-bytes")
    assert result.pdf == b"%PDF-image"
    assert result.ocr_applied is True


def test_image_falls_back_to_libreoffice(monkeypatch):
    monkeypatch.setattr(conv, "_ocrmypdf", lambda *_a, **_k: False)

    def fake_run(work, *cmd):
        assert cmd[0] == "libreoffice"
        (work / "scan.pdf").write_bytes(b"%PDF-lo")
        return True

    monkeypatch.setattr(conv, "_run", fake_run)
    result = conv.convert("scan.png", "image/png", b"png-bytes")
    assert result.pdf == b"%PDF-lo"
    assert result.producer == "libreoffice"
    assert result.ocr_applied is False


def test_office_document_goes_to_libreoffice(monkeypatch):
    def fake_run(work, *cmd):
        (work / "letter.pdf").write_bytes(b"%PDF-lo")
        return True

    monkeypatch.setattr(conv, "_run", fake_run)
    result = conv.convert(
        "letter.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        b"docx-bytes")
    assert result.producer == "libreoffice"


def test_libreoffice_failure_raises(monkeypatch):
    monkeypatch.setattr(conv, "_run", lambda *_a, **_k: False)
    with pytest.raises(conv.ConversionFailedError):
        conv.convert("letter.docx", "application/msword", b"doc-bytes")


def test_tool_timeout_maps_to_timeout_error(monkeypatch):
    def fake_subprocess_run(*_a, **_k):
        raise subprocess.TimeoutExpired(cmd="libreoffice", timeout=180)

    monkeypatch.setattr(conv.subprocess, "run", fake_subprocess_run)
    with pytest.raises(conv.ToolTimeoutError):
        conv.convert("letter.docx", "application/msword", b"doc-bytes")


def test_filename_is_sanitized():
    assert conv._sanitize("../../etc/passwd") == ".._.._etc_passwd"
    assert conv._sanitize("Rechnung 2026.pdf") == "Rechnung_2026.pdf"
    assert conv._sanitize("") == "input"
    assert conv._sanitize("...") == "input"
