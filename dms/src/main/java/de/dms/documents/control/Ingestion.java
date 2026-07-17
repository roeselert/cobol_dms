package de.dms.documents.control;

import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.crosscutting.platform.control.PayloadTooLargeException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import de.dms.crosscutting.platform.control.UnsupportedMediaTypeException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Validates the upload, computes the SHA-256 and stores the original binary —
 * streamed, so the file never has to sit on the heap. The binary is durably
 * stored BEFORE any metadata is written; if the store is unreachable the
 * request fails with 503 and nothing is persisted (R-1).
 */
@Service
public class Ingestion {

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "tif", "image/tiff",
            "tiff", "image/tiff",
            "eml", "message/rfc822");

    private final ObjectStore objectStore;
    private final long maxBytes;

    public Ingestion(ObjectStore objectStore, DmsProperties properties) {
        this.objectStore = objectStore;
        this.maxBytes = properties.upload().maxBytes();
    }

    public record StoredBinary(String storageKey, String mimeType, String sha256, long sizeBytes) {
    }

    public StoredBinary store(String documentId, String filename, String declaredMimeType,
                              InputStream content, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new UnprocessableException("file is empty");
        }
        if (sizeBytes > maxBytes) {
            throw new PayloadTooLargeException("file exceeds " + maxBytes + " bytes");
        }
        String mimeType = resolveMimeType(filename, declaredMimeType);
        String key = "originals/" + documentId + "/original";
        // the digest is computed while streaming into the store — the checksum
        // is verified on write once, not re-hashed on every download (S-3)
        DigestInputStream digesting = new DigestInputStream(content, newSha256());
        objectStore.put(key, digesting, sizeBytes); // StorageUnavailableException -> 503, no orphan metadata
        String sha256 = HexFormat.of().formatHex(digesting.getMessageDigest().digest());
        return new StoredBinary(key, mimeType, sha256, sizeBytes);
    }

    public String resolveMimeType(String filename, String declaredMimeType) {
        String byExtension = filename == null ? null
                : EXTENSION_TO_MIME.get(extensionOf(filename.toLowerCase(Locale.ROOT)));
        if (declaredMimeType != null && EXTENSION_TO_MIME.containsValue(stripParams(declaredMimeType))) {
            return stripParams(declaredMimeType);
        }
        if (byExtension != null) {
            return byExtension;
        }
        throw new UnsupportedMediaTypeException("unsupported media type: "
                + (declaredMimeType == null ? filename : declaredMimeType));
    }

    public static String sha256(byte[] content) {
        return HexFormat.of().formatHex(newSha256().digest(content));
    }

    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    private static String stripParams(String mimeType) {
        int semicolon = mimeType.indexOf(';');
        return (semicolon < 0 ? mimeType : mimeType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }
}
