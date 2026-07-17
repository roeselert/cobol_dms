import pytest

from app.models import CatalogEntry, Catalogs, Intent, IntentField


@pytest.fixture
def catalogs() -> Catalogs:
    """The catalog set used by the Java ExtractionPromptTest / AiExtractionClientTest."""
    return Catalogs(
        documentClasses=[
            CatalogEntry(name="RECHNUNG", description="Eingehende Rechnungen"),
            CatalogEntry(name="VERTRAG", description="Verträge"),
        ],
        intents=[
            Intent(name="Rechnungseingang", description="Eine eingehende Rechnung",
                   fields=[
                       IntentField(name="absender", description="the sender of the document"),
                       IntentField(name="betrag", description="the gross amount"),
                   ]),
        ],
        ordnungsbegriffTypes=[
            CatalogEntry(name="Kundennummer", description="Die Kundennummer des Absenders"),
        ],
    )
