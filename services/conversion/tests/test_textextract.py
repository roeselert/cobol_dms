import subprocess
from pathlib import Path

from app import textextract


def test_prefers_pdftotext(monkeypatch):
    def fake_run(cmd, **_k):
        assert cmd[0] == "pdftotext"
        return subprocess.CompletedProcess(cmd, 0, stdout=b"  hello world \n")

    monkeypatch.setattr(textextract.subprocess, "run", fake_run)
    assert textextract.extract_text(Path("x.pdf")) == "hello world"


def test_falls_back_to_ghostscript(monkeypatch):
    def fake_run(cmd, **_k):
        if cmd[0] == "pdftotext":
            raise FileNotFoundError(cmd[0])
        return subprocess.CompletedProcess(cmd, 0, stdout=b"gs text")

    monkeypatch.setattr(textextract.subprocess, "run", fake_run)
    assert textextract.extract_text(Path("x.pdf")) == "gs text"


def test_empty_when_no_tool_works(monkeypatch):
    def fake_run(cmd, **_k):
        raise FileNotFoundError(cmd[0])

    monkeypatch.setattr(textextract.subprocess, "run", fake_run)
    assert textextract.extract_text(Path("x.pdf")) == ""


def test_blank_pdftotext_output_falls_through(monkeypatch):
    def fake_run(cmd, **_k):
        if cmd[0] == "pdftotext":
            return subprocess.CompletedProcess(cmd, 0, stdout=b"   \n")
        return subprocess.CompletedProcess(cmd, 0, stdout=b"gs text")

    monkeypatch.setattr(textextract.subprocess, "run", fake_run)
    assert textextract.extract_text(Path("x.pdf")) == "gs text"
