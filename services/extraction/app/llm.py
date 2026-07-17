"""The OpenAI-compatible chat-completions call — port of the transport half of
the Java `AiExtractionClient`. The provider connection (URL, API key, model,
document mode) is owned by this service via environment variables; the API key
never appears in request payloads between the main app and this service.

Document transport: images go as image_url data URLs regardless of mode; PDFs
travel per DMS_AI_DOCUMENT_MODE — 'text' (default, works with every
OpenAI-compatible endpoint) sends the OCR text from the conversion service,
'file' sends the PDF as an OpenAI-proprietary inline file part."""

import logging
import os

import httpx

from .models import ExtractRequest
from .prompt import system, user_instruction

log = logging.getLogger(__name__)

CONNECT_TIMEOUT_SECONDS = 10
READ_TIMEOUT_SECONDS = 120

# Token-cost bound for text mode; the index uses the full text app-side.
MAX_TEXT_CHARS = 100_000


class NoTextError(Exception):
    """Text mode with a blank document text — nothing to send to the model."""


class UpstreamAiError(Exception):
    """The AI provider failed: network error, non-2xx, or unusable response."""


def is_configured() -> bool:
    return bool(os.environ.get("DMS_AI_TOKEN", ""))


def complete(request: ExtractRequest) -> str:
    """Runs one chat completion and returns the model's message content."""
    user_content = [{"type": "text", "text": user_instruction(request.filename)}]
    user_content.extend(_document_parts(request))
    body = {
        "model": os.environ.get("DMS_AI_MODEL", "gpt-5-mini"),
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system(request.catalogs)},
            {"role": "user", "content": user_content},
        ],
    }
    base_url = os.environ.get("DMS_AI_URL", "https://api.openai.com/v1").rstrip("/")
    try:
        response = httpx.post(
            f"{base_url}/chat/completions",
            json=body,
            headers={"Authorization": f"Bearer {os.environ['DMS_AI_TOKEN']}"},
            timeout=httpx.Timeout(READ_TIMEOUT_SECONDS, connect=CONNECT_TIMEOUT_SECONDS),
        )
    except httpx.HTTPError as e:
        raise UpstreamAiError(f"AI provider unreachable: {e}") from e
    if response.status_code != 200:
        raise UpstreamAiError(f"AI provider answered {response.status_code}: {response.text[:500]}")
    try:
        content = response.json()["choices"][0]["message"]["content"]
    except (ValueError, LookupError, TypeError) as e:
        raise UpstreamAiError(f"unexpected AI provider response shape: {e}") from e
    if not isinstance(content, str):
        raise UpstreamAiError("AI provider response content is not a string")
    return content


def _document_parts(request: ExtractRequest) -> list[dict]:
    if request.image is not None:
        return [{"type": "image_url", "image_url":
                {"url": f"data:{request.image.mimeType};base64,{request.image.data}"}}]
    mode = os.environ.get("DMS_AI_DOCUMENT_MODE", "text").lower()
    if mode == "file":
        if not request.pdfBase64:
            raise NoTextError("document mode 'file' but the request carries no PDF")
        return [{"type": "file", "file": {
            "filename": request.filename,
            "file_data": f"data:{request.mimeType};base64,{request.pdfBase64}"}}]
    if not request.text.strip():
        raise NoTextError("document mode 'text' but the request carries no document text")
    return [{"type": "text", "text": "Document content:\n" + request.text[:MAX_TEXT_CHARS]}]
