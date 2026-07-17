from fastapi.testclient import TestClient

from app import convert as conv
from app.main import app

client = TestClient(app)

MINIMAL_PDF = b"%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF"


def _upload(token: str | None = None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return client.post(
        "/convert",
        files={"file": ("doc.pdf", MINIMAL_PDF, "application/pdf")},
        data={"mimeType": "application/pdf"},
        headers=headers,
    )


def test_healthz_is_open():
    response = client.get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert set(body["tools"]) == {"ocrmypdf", "ghostscript", "libreoffice", "pdftotext"}


def test_auth_fails_closed_without_token(monkeypatch):
    monkeypatch.delenv("DMS_SERVICE_TOKEN", raising=False)
    monkeypatch.delenv("DMS_SERVICE_AUTH", raising=False)
    response = _upload()
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "auth_unconfigured"


def test_rejects_missing_and_wrong_bearer(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_TOKEN", "secret")
    assert _upload().status_code == 401
    assert _upload("wrong").status_code == 401


def test_convert_happy_path(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_TOKEN", "secret")
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: False)
    monkeypatch.setattr(conv, "_ghostscript_available", lambda: False)
    monkeypatch.setattr(conv, "extract_text", lambda _p: "hello")

    response = _upload("secret")
    assert response.status_code == 200
    body = response.json()
    assert body["producer"] == "passthrough"
    assert body["text"] == "hello"
    import base64
    assert base64.b64decode(body["pdfBase64"]) == MINIMAL_PDF


def test_auth_can_be_disabled_explicitly(monkeypatch):
    monkeypatch.delenv("DMS_SERVICE_TOKEN", raising=False)
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setattr(conv, "_ocrmypdf_available", lambda: False)
    monkeypatch.setattr(conv, "_ghostscript_available", lambda: False)
    monkeypatch.setattr(conv, "extract_text", lambda _p: "")
    assert _upload().status_code == 200


def test_empty_file_is_bad_request(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    response = client.post(
        "/convert",
        files={"file": ("doc.pdf", b"", "application/pdf")},
        data={"mimeType": "application/pdf"},
    )
    assert response.status_code == 400


def test_conversion_failure_is_422(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setattr(conv, "_run", lambda *_a, **_k: False)
    response = client.post(
        "/convert",
        files={"file": ("x.docx", b"docx-bytes", "application/msword")},
        data={"mimeType": "application/msword"},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "conversion_failed"
