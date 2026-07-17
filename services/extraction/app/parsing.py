"""Lenient parsing of the model's JSON answer — the Python port of the Java
`AiExtractionClient.parseResponse`/`parseOrdnungsbegriffe`.

Three-valued ordnungsbegriffe contract, preserved on the wire:
  list  = extracted entries (canonicalized, deduplicated)
  []    = key missing/null or model found none
  None  = malformed section -> the main app flags the document for review,
          while the rest of the answer (classification above all) still applies.
"""

import json
import logging

from .models import Catalogs
from .prompt import INTENT_KEY, ORDNUNGSBEGRIFFE_KEY

log = logging.getLogger(__name__)

WELL_KNOWN_KEYS = {"documentDate", "documentClass", "filePlanReference",
                   "extractedText", INTENT_KEY, ORDNUNGSBEGRIFFE_KEY}


class ResponseParseError(Exception):
    pass


def parse_answer(content: str, catalogs: Catalogs) -> dict:
    """Parses the model's message content into the suggestions object of the
    /extract response. Raises ResponseParseError when the content is not a
    JSON object at all."""
    try:
        answer = json.loads(_strip_code_fences(content))
        if not isinstance(answer, dict):
            raise ResponseParseError(f"model answer is not a JSON object: {type(answer).__name__}")
    except json.JSONDecodeError as e:
        raise ResponseParseError(f"cannot parse model answer as JSON: {e}") from e

    additional: dict[str, str] = {}
    intent = _text_or_none(answer, INTENT_KEY)
    if intent is not None:
        # the chosen intent rides along with the fields into the search index
        additional[INTENT_KEY] = intent
    for field in catalogs.field_names():
        if field not in WELL_KNOWN_KEYS:
            value = _text_or_none(answer, field)
            if value is not None:
                additional[field] = value

    return {
        "documentDate": _text_or_none(answer, "documentDate"),
        "documentClass": _text_or_none(answer, "documentClass"),
        "filePlanReference": _text_or_none(answer, "filePlanReference"),
        "additional": additional,
        "ordnungsbegriffe": _parse_ordnungsbegriffe(answer, catalogs),
    }


def _parse_ordnungsbegriffe(answer: dict, catalogs: Catalogs) -> list[dict] | None:
    node = answer.get(ORDNUNGSBEGRIFFE_KEY)
    if node is None:
        return []
    if not isinstance(node, list):
        log.warning("cannot parse ordnungsbegriffe section (not an array: %s), "
                    "document will be flagged for review", type(node).__name__)
        return None
    canonical = {entry.name.lower(): entry.name for entry in catalogs.ordnungsbegriffTypes}
    result: list[dict] = []
    seen: set[tuple[str, str]] = set()
    for entry in node:
        if not isinstance(entry, dict):
            continue  # like entries without type/value: skipped, not fatal
        type_name = _text_or_none(entry, "type")
        value = _text_or_none(entry, "value")
        if type_name is None or value is None or not value.strip():
            continue
        name = canonical.get(type_name.lower())
        if name is None:
            continue  # entries of unknown/inactive types are dropped
        item = (name, value.strip())
        if item not in seen:
            seen.add(item)
            result.append({"type": name, "value": value.strip()})
    return result


def _text_or_none(node: dict, field: str) -> str | None:
    value = node.get(field)
    if value is None:
        return None
    return value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)


def _strip_code_fences(content: str) -> str:
    trimmed = content.strip()
    if trimmed.startswith("```"):
        first_newline = trimmed.find("\n")
        last_fence = trimmed.rfind("```")
        if first_newline >= 0 and last_fence > first_newline:
            return trimmed[first_newline + 1:last_fence].strip()
    return trimmed
