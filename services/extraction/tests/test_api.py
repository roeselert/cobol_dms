from fastapi.testclient import TestClient

from app import llm, main
from app.main import app

client = TestClient(app)

EXTRACT_BODY = {
    "filename": "rechnung.pdf",
    "mimeType": "application/pdf",
    "text": "Rechnung Nr. 4711",
    "pdfBase64": "AQID",
    "catalogs": {
        "documentClasses": [{"name": "RECHNUNG", "description": "Rechnungen"}],
        "intents": [{"name": "Rechnungseingang", "description": "Eine Rechnung",
                     "fields": [{"name": "absender", "description": "sender"}]}],
        "ordnungsbegriffTypes": [{"name": "Kundennummer", "description": "Die Kundennummer"}],
    },
}


def _post(token: str | None = "secret"):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return client.post("/extract", json=EXTRACT_BODY, headers=headers)


def test_healthz_reports_ai_configuration(monkeypatch):
    monkeypatch.delenv("DMS_AI_TOKEN", raising=False)
    body = client.get("/healthz").json()
    assert body == {"status": "ok", "aiConfigured": False}
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    assert client.get("/healthz").json()["aiConfigured"] is True


def test_auth_fails_closed_without_token(monkeypatch):
    monkeypatch.delenv("DMS_SERVICE_TOKEN", raising=False)
    monkeypatch.delenv("DMS_SERVICE_AUTH", raising=False)
    assert _post(token=None).status_code == 503


def test_rejects_wrong_bearer(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_TOKEN", "secret")
    assert _post("wrong").status_code == 401


def test_unconfigured_ai_is_a_graceful_200(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_TOKEN", "secret")
    monkeypatch.delenv("DMS_AI_TOKEN", raising=False)
    response = _post()
    assert response.status_code == 200
    assert response.json() == {"status": "unconfigured"}


def test_extract_happy_path(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_TOKEN", "secret")
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setattr(main.llm, "complete", lambda _r: (
        '{"documentDate":"2026-07-01","documentClass":"RECHNUNG",'
        '"intent":"Rechnungseingang","absender":"Stadtwerke",'
        '"ordnungsbegriffe":[{"type":"kundennummer","value":"7000123456"}]}'))
    response = _post()
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["suggestions"]["documentClass"] == "RECHNUNG"
    assert body["suggestions"]["additional"] == {
        "intent": "Rechnungseingang", "absender": "Stadtwerke"}
    assert body["suggestions"]["ordnungsbegriffe"] == [
        {"type": "Kundennummer", "value": "7000123456"}]


def test_malformed_ordnungsbegriffe_travel_as_json_null(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setattr(main.llm, "complete",
                        lambda _r: '{"documentClass":"RECHNUNG","ordnungsbegriffe":"x"}')
    body = _post(token=None).json()
    assert body["suggestions"]["ordnungsbegriffe"] is None


def test_no_text_is_422(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")

    def raise_no_text(_r):
        raise llm.NoTextError("no text")
    monkeypatch.setattr(main.llm, "complete", raise_no_text)
    response = _post(token=None)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "no_text"


def test_upstream_failure_is_502(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")

    def raise_upstream(_r):
        raise llm.UpstreamAiError("500 from provider")
    monkeypatch.setattr(main.llm, "complete", raise_upstream)
    response = _post(token=None)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "upstream_ai_error"


def test_unparseable_model_answer_is_502(monkeypatch):
    monkeypatch.setenv("DMS_SERVICE_AUTH", "off")
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setattr(main.llm, "complete", lambda _r: "not json")
    response = _post(token=None)
    assert response.status_code == 502
