"""Port of the Java ExtractionPromptTest — same catalog fixture, same
assertions, except that extractedText is deliberately no longer requested
(the deterministic OCR text from the conversion service feeds the index)."""

from app.models import Catalogs
from app.prompt import system, user_instruction


def test_system_prompt_contains_classes_with_descriptions_and_intents(catalogs):
    text = system(catalogs)
    assert "RECHNUNG: Eingehende Rechnungen" in text
    assert "VERTRAG: Verträge" in text
    assert "Rechnungseingang: Eine eingehende Rechnung" in text
    assert "absender: the sender of the document" in text
    assert "betrag: the gross amount" in text
    assert "single JSON object" in text


def test_system_prompt_no_longer_requests_extracted_text(catalogs):
    assert "extractedText" not in system(catalogs)


def test_system_prompt_lists_active_ordnungsbegriff_types(catalogs):
    text = system(catalogs)
    assert "ordnungsbegriffe" in text
    assert "Kundennummer: Die Kundennummer des Absenders" in text


def test_ordnungsbegriffe_section_is_omitted_without_active_types(catalogs):
    catalogs.ordnungsbegriffTypes = []
    assert "ordnungsbegriffe" not in system(catalogs)


def test_intent_section_is_omitted_without_intents(catalogs):
    catalogs.intents = []
    assert "intent" not in system(catalogs)


def test_empty_class_catalog_omits_the_code_list():
    text = system(Catalogs())
    assert "Must be exactly one of" not in text
    assert "documentClass" in text


def test_field_names_are_the_union_of_intent_fields(catalogs):
    assert catalogs.field_names() == ["absender", "betrag"]


def test_user_instruction_names_the_file():
    assert user_instruction("rechnung.pdf") \
        == 'Analyze the attached document "rechnung.pdf" and return the metadata JSON.'
