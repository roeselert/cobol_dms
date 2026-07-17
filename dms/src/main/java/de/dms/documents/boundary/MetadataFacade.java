package de.dms.documents.boundary;

import de.dms.documents.entity.Document;
import de.dms.documents.control.MetadataValidation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactional boundary: metadata upsert + Aktenbildung + reindex commit together. */
@Component
public class MetadataFacade {

    private final MetadataValidation metadataValidation;

    public MetadataFacade(MetadataValidation metadataValidation) {
        this.metadataValidation = metadataValidation;
    }

    @Transactional
    public void save(Document document, MetadataValidation.MetadataInput input, String userId) {
        metadataValidation.save(document, input, userId);
    }
}
