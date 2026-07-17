package de.dms.aiextraction.control;

import de.dms.aiextraction.entity.OrdnungsbegriffType;
import de.dms.aiextraction.entity.OrdnungsbegriffTypeRepository;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrdnungsbegriffCatalogTest {

    private final OrdnungsbegriffTypeRepository repository = mock(OrdnungsbegriffTypeRepository.class);
    private final OrdnungsbegriffCatalog catalog = new OrdnungsbegriffCatalog(repository);

    @Test
    void blankNameOrDescriptionIsRejected() {
        assertThatThrownBy(() -> catalog.create(" ", "desc"))
                .isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> catalog.create("Kundennummer", null))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void duplicateNameIsRejectedCaseInsensitively() {
        when(repository.existsByNameIgnoreCase("kundennummer")).thenReturn(true);
        assertThatThrownBy(() -> catalog.create("kundennummer", "desc"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void normalizeTypeMatchesActiveTypesCaseInsensitively() {
        when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(
                new OrdnungsbegriffType("ob-1", "Kundennummer", "desc", true, 0L)));
        assertThat(catalog.normalizeType(" kundenNUMMER ")).contains("Kundennummer");
        assertThat(catalog.normalizeType("Vertragsnummer")).isEmpty();
        assertThat(catalog.normalizeType(null)).isEmpty();
        assertThat(catalog.normalizeType("  ")).isEmpty();
    }
}
