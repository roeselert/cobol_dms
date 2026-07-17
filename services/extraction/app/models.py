"""Request/response models. The catalogs — document classes, extraction
intents with their fields, Ordnungsbegriff types — are managed in the main
app's database and travel with every request, so admin edits take effect on
the next extraction without touching this service."""

from pydantic import BaseModel


class CatalogEntry(BaseModel):
    name: str
    description: str = ""


class IntentField(BaseModel):
    name: str
    description: str = ""


class Intent(BaseModel):
    name: str
    description: str = ""
    fields: list[IntentField] = []


class Catalogs(BaseModel):
    documentClasses: list[CatalogEntry] = []
    intents: list[Intent] = []
    ordnungsbegriffTypes: list[CatalogEntry] = []

    def field_names(self) -> list[str]:
        """Union of all intents' field names — the keys the response parser looks for."""
        seen: dict[str, None] = {}
        for intent in self.intents:
            for field in intent.fields:
                seen.setdefault(field.name)
        return list(seen)


class ImagePart(BaseModel):
    mimeType: str
    data: str  # base64


class ExtractRequest(BaseModel):
    filename: str
    mimeType: str = "application/pdf"
    text: str = ""
    pdfBase64: str | None = None
    image: ImagePart | None = None
    catalogs: Catalogs = Catalogs()
