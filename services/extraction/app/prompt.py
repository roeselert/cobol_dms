"""System-prompt assembly — the Python port of the Java `ExtractionPrompt`.

Built per request from the catalogs in the payload. The two literal slots
documentDate and filePlanReference stay hard-coded. Unlike the Java original,
the model is NOT asked to echo the full document text (`extractedText`): the
deterministic OCR text from the conversion service feeds the search index, so
responses stay small and cheap."""

from .models import Catalogs

INTENT_KEY = "intent"
ORDNUNGSBEGRIFFE_KEY = "ordnungsbegriffe"


def system(catalogs: Catalogs) -> str:
    parts: list[str] = []
    parts.append(
        "You are the document analysis service of a German document management system. "
        "You receive one document. Classify it and extract filing metadata.\n\n"
        "Rules:\n"
        "- Respond with a single JSON object only - no markdown, no code fences, no explanations.\n"
        "- Use exactly the keys listed below. Use null for any value that cannot be determined "
        "from the document.\n"
        "- Dates always in ISO format yyyy-MM-dd.\n\n"
        "Keys to extract:\n"
        "- documentDate: The date the document was issued, ISO format yyyy-MM-dd.\n"
        "- documentClass: The document category.")
    if catalogs.documentClasses:
        parts.append(" Must be exactly one of the following codes:\n")
        for document_class in catalogs.documentClasses:
            parts.append(f"  - {document_class.name}: {document_class.description}\n")
    else:
        parts.append("\n")
    parts.append(
        "- filePlanReference: The file plan reference assigning the document "
        "to its Akte, e.g. 2026/PER/001.\n")
    if catalogs.ordnungsbegriffTypes:
        parts.append(
            f"- {ORDNUNGSBEGRIFFE_KEY}: JSON array of business reference identifiers "
            "(Ordnungsbegriffe) found in the document. Each element is an object "
            "{\"type\": \"<type name>\", \"value\": \"<identifier exactly as it appears in the "
            "document>\"}. Extract only values matching one of the types listed below; a document "
            "can contain several entries, also several of the same type. Return an empty array [] "
            "when the document contains none:\n")
        for ordnungsbegriff_type in catalogs.ordnungsbegriffTypes:
            parts.append(f"  - {ordnungsbegriff_type.name}: {ordnungsbegriff_type.description}\n")
    if catalogs.intents:
        parts.append(
            f"- {INTENT_KEY}: The processing intent that matches the document best - exactly one "
            "of the intent names listed below, or null when none fits.\n")
        parts.append(
            f"\nIntents - pick the single best match, return its name under \"{INTENT_KEY}\", "
            "and additionally extract only the chosen intent's fields as top-level JSON keys:\n")
        for intent in catalogs.intents:
            parts.append(f"- {intent.name}: {intent.description}\n")
            for field in intent.fields:
                parts.append(f"  - {field.name}: {field.description}\n")
    return "".join(parts)


def user_instruction(filename: str) -> str:
    return f'Analyze the attached document "{filename}" and return the metadata JSON.'
