"""Port of the documentParts half of the Java AiExtractionClientTest, plus
the provider-call error mapping (httpx mocked via monkeypatch)."""

import httpx
import pytest

from app import llm
from app.models import ExtractRequest, ImagePart


def request(**overrides) -> ExtractRequest:
    defaults = dict(filename="rechnung.pdf", mimeType="application/pdf",
                    text="Rechnung Nr. 4711", pdfBase64="AQID")
    defaults.update(overrides)
    return ExtractRequest(**defaults)


def test_images_travel_as_image_url_parts_regardless_of_mode(monkeypatch):
    for mode in ("text", "file"):
        monkeypatch.setenv("DMS_AI_DOCUMENT_MODE", mode)
        parts = llm._document_parts(request(image=ImagePart(mimeType="image/png", data="AQID")))
        assert parts == [{"type": "image_url",
                          "image_url": {"url": "data:image/png;base64,AQID"}}]


def test_file_mode_sends_pdf_as_inline_file_part(monkeypatch):
    monkeypatch.setenv("DMS_AI_DOCUMENT_MODE", "file")
    parts = llm._document_parts(request())
    assert parts == [{"type": "file", "file": {
        "filename": "rechnung.pdf",
        "file_data": "data:application/pdf;base64,AQID"}}]


def test_text_mode_sends_document_text_as_text_part(monkeypatch):
    monkeypatch.setenv("DMS_AI_DOCUMENT_MODE", "text")
    parts = llm._document_parts(request())
    assert parts == [{"type": "text", "text": "Document content:\nRechnung Nr. 4711"}]


def test_unset_document_mode_defaults_to_text(monkeypatch):
    monkeypatch.delenv("DMS_AI_DOCUMENT_MODE", raising=False)
    parts = llm._document_parts(request())
    assert parts == [{"type": "text", "text": "Document content:\nRechnung Nr. 4711"}]


def test_text_mode_truncates_to_token_budget(monkeypatch):
    monkeypatch.delenv("DMS_AI_DOCUMENT_MODE", raising=False)
    parts = llm._document_parts(request(text="x" * (llm.MAX_TEXT_CHARS + 1000)))
    assert len(parts[0]["text"]) == len("Document content:\n") + llm.MAX_TEXT_CHARS


def test_blank_text_in_text_mode_raises_no_text(monkeypatch):
    monkeypatch.delenv("DMS_AI_DOCUMENT_MODE", raising=False)
    with pytest.raises(llm.NoTextError):
        llm._document_parts(request(text="   "))


def _fake_post(response: httpx.Response):
    def post(url, **kwargs):
        post.captured = {"url": url, **kwargs}
        return response
    return post


def test_complete_sends_prompt_and_returns_content(monkeypatch, catalogs):
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setenv("DMS_AI_URL", "https://ai.example/v1/")
    monkeypatch.setenv("DMS_AI_MODEL", "test-model")
    monkeypatch.delenv("DMS_AI_DOCUMENT_MODE", raising=False)
    fake = _fake_post(httpx.Response(
        200, json={"choices": [{"message": {"content": '{"documentClass":"RECHNUNG"}'}}]}))
    monkeypatch.setattr(llm.httpx, "post", fake)

    content = llm.complete(request(catalogs=catalogs))

    assert content == '{"documentClass":"RECHNUNG"}'
    assert fake.captured["url"] == "https://ai.example/v1/chat/completions"
    assert fake.captured["headers"]["Authorization"] == "Bearer sk-test"
    body = fake.captured["json"]
    assert body["model"] == "test-model"
    assert body["response_format"] == {"type": "json_object"}
    assert "RECHNUNG: Eingehende Rechnungen" in body["messages"][0]["content"]
    assert body["messages"][1]["content"][0]["text"].startswith("Analyze the attached document")


def test_provider_error_maps_to_upstream_error(monkeypatch):
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setattr(llm.httpx, "post", _fake_post(httpx.Response(500, text="boom")))
    with pytest.raises(llm.UpstreamAiError):
        llm.complete(request())

    def network_error(url, **_k):
        raise httpx.ConnectError("refused")
    monkeypatch.setattr(llm.httpx, "post", network_error)
    with pytest.raises(llm.UpstreamAiError):
        llm.complete(request())


def test_unexpected_response_shape_maps_to_upstream_error(monkeypatch):
    monkeypatch.setenv("DMS_AI_TOKEN", "sk-test")
    monkeypatch.setattr(llm.httpx, "post", _fake_post(httpx.Response(200, json={"choices": []})))
    with pytest.raises(llm.UpstreamAiError):
        llm.complete(request())
