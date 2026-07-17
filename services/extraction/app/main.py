"""DMS extraction service: classification, intent detection and metadata
extraction via an OpenAI-compatible API. What to extract (document classes,
intents with fields, Ordnungsbegriff types) arrives as catalogs in each
request; how to reach the AI provider is this service's own configuration
(DMS_AI_URL / DMS_AI_TOKEN / DMS_AI_MODEL / DMS_AI_DOCUMENT_MODE).

POST /extract  (JSON; bearer service token)
  200 -> {"status": "ok", "suggestions": {...}}
  200 -> {"status": "unconfigured"}        # no DMS_AI_TOKEN — graceful skip,
                                           # deliberately not an error status
GET  /healthz  (unauthenticated)
"""

from fastapi import Depends, FastAPI

from . import llm
from .auth import require_service_token
from .errors import ApiError, install_error_handlers
from .models import ExtractRequest
from .parsing import ResponseParseError, parse_answer

app = FastAPI(title="DMS Extraction Service", version="1.0.0")
install_error_handlers(app)


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok", "aiConfigured": llm.is_configured()}


@app.post("/extract", dependencies=[Depends(require_service_token)])
def extract(request: ExtractRequest) -> dict:
    if not llm.is_configured():
        return {"status": "unconfigured"}
    try:
        content = llm.complete(request)
        suggestions = parse_answer(content, request.catalogs)
    except llm.NoTextError as e:
        raise ApiError(422, "no_text", str(e)) from e
    except llm.UpstreamAiError as e:
        raise ApiError(502, "upstream_ai_error", str(e)) from e
    except ResponseParseError as e:
        raise ApiError(502, "upstream_ai_error", str(e)) from e
    return {"status": "ok", "suggestions": suggestions}
