package de.dms.documents.control;

import de.dms.crosscutting.platform.objectstore.control.FilesystemObjectStore;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.crosscutting.platform.control.PayloadTooLargeException;
import de.dms.crosscutting.platform.control.UnsupportedMediaTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionTest {

    @TempDir
    Path tempDir;

    private Ingestion ingestion(long maxBytes) {
        DmsProperties properties = new DmsProperties(tempDir.toString(), null,
                new DmsProperties.Upload(maxBytes), null, null, null, null, null);
        return new Ingestion(new FilesystemObjectStore(tempDir), properties);
    }

    @Test
    void storesPdfWithSha256AndDeterministicKey() {
        byte[] content = "%PDF-1.4 test".getBytes();
        var stored = ingestion(1024).store("doc-1", "rechnung.pdf", "application/pdf",
                new ByteArrayInputStream(content), content.length);
        assertThat(stored.mimeType()).isEqualTo("application/pdf");
        assertThat(stored.storageKey()).isEqualTo("originals/doc-1/original");
        assertThat(stored.sha256()).isEqualTo(Ingestion.sha256(content));
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> ingestion(1024).store("doc-1", "evil.exe", "application/octet-stream",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3))
                .isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void rejectsOversizedUpload() {
        assertThatThrownBy(() -> ingestion(2).store("doc-1", "big.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3))
                .isInstanceOf(PayloadTooLargeException.class);
    }
}
