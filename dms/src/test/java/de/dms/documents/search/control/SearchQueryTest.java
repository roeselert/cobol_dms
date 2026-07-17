package de.dms.documents.search.control;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryTest {

    @Test
    void quotesEveryTermSoFtsSyntaxCannotBeInjected() {
        assertThat(SearchQuery.toFtsQuery("Rechnung 2026")).isEqualTo("\"Rechnung\" \"2026\"");
    }

    @Test
    void stripsEmbeddedQuotesFromTerms() {
        assertThat(SearchQuery.toFtsQuery("a\"b OR *")).isEqualTo("\"ab\" \"OR\" \"*\"");
    }
}
