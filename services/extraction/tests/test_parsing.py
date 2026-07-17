"""Port of the parsing half of the Java AiExtractionClientTest — same inputs,
same expectations (minus extractedText, which the model no longer returns)."""

import pytest

from app.parsing import ResponseParseError, parse_answer


def test_parses_answer_into_suggestions(catalogs):
    content = ('{"documentDate":"2026-07-01","documentClass":"RECHNUNG",'
               '"filePlanReference":"2026/PER/001","intent":"Rechnungseingang",'
               '"absender":"Stadtwerke"}')
    suggestions = parse_answer(content, catalogs)
    assert suggestions["documentDate"] == "2026-07-01"
    assert suggestions["documentClass"] == "RECHNUNG"
    assert suggestions["filePlanReference"] == "2026/PER/001"
    assert suggestions["additional"] == {"intent": "Rechnungseingang", "absender": "Stadtwerke"}


def test_tolerates_code_fences_and_null_values(catalogs):
    content = '```json\n{"documentDate":null,"documentClass":"RECHNUNG","intent":null}\n```'
    suggestions = parse_answer(content, catalogs)
    assert suggestions["documentDate"] is None
    assert suggestions["documentClass"] == "RECHNUNG"
    assert suggestions["additional"] == {}


def test_ignores_fields_not_belonging_to_any_intent(catalogs):
    content = '{"documentClass":"RECHNUNG","unbekannt":"x","absender":"Stadtwerke"}'
    suggestions = parse_answer(content, catalogs)
    assert suggestions["additional"] == {"absender": "Stadtwerke"}


def test_parses_ordnungsbegriffe_canonicalizing_types_and_dropping_unknown_ones(catalogs):
    content = ('{"documentClass":"RECHNUNG","ordnungsbegriffe":['
               '{"type":"kundennummer","value":" 7000123456 "},'
               '{"type":"Lieferscheinnummer","value":"L-9"},'
               '{"type":"Kundennummer","value":""},'
               '{"type":null,"value":"x"}]}')
    suggestions = parse_answer(content, catalogs)
    assert suggestions["ordnungsbegriffe"] == [{"type": "Kundennummer", "value": "7000123456"}]


def test_deduplicates_ordnungsbegriffe(catalogs):
    content = ('{"ordnungsbegriffe":['
               '{"type":"Kundennummer","value":"7000123456"},'
               '{"type":"kundennummer","value":"7000123456"}]}')
    suggestions = parse_answer(content, catalogs)
    assert suggestions["ordnungsbegriffe"] == [{"type": "Kundennummer", "value": "7000123456"}]


def test_missing_or_empty_ordnungsbegriffe_means_none_found(catalogs):
    assert parse_answer('{"documentClass":"RECHNUNG"}', catalogs)["ordnungsbegriffe"] == []
    assert parse_answer('{"documentClass":"RECHNUNG","ordnungsbegriffe":null}',
                        catalogs)["ordnungsbegriffe"] == []
    assert parse_answer('{"documentClass":"RECHNUNG","ordnungsbegriffe":[]}',
                        catalogs)["ordnungsbegriffe"] == []


def test_malformed_ordnungsbegriffe_section_yields_null_but_classification_survives(catalogs):
    content = '{"documentClass":"RECHNUNG","ordnungsbegriffe":"7000123456"}'
    suggestions = parse_answer(content, catalogs)
    assert suggestions["ordnungsbegriffe"] is None
    assert suggestions["documentClass"] == "RECHNUNG"


def test_non_object_answer_raises(catalogs):
    with pytest.raises(ResponseParseError):
        parse_answer("not json at all", catalogs)
    with pytest.raises(ResponseParseError):
        parse_answer('["a","b"]', catalogs)
