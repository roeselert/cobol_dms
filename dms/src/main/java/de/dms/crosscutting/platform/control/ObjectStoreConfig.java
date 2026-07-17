package de.dms.crosscutting.platform.control;

import de.dms.crosscutting.platform.objectstore.control.FilesystemObjectStore;
import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import de.dms.crosscutting.platform.objectstore.control.S3ObjectStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Selects the iteration-1 binding: S3 bucket when configured, local disk otherwise. */
@Configuration
public class ObjectStoreConfig {

    @Bean
    public ObjectStore objectStore(DmsProperties properties) {
        DmsProperties.Bucket bucket = properties.bucket();
        if (bucket != null && bucket.configured()) {
            return new S3ObjectStore(bucket.endpoint(), bucket.region(),
                    bucket.keyId(), bucket.secret(), bucket.name());
        }
        return new FilesystemObjectStore(Path.of(properties.dataDir(), "objects"));
    }
}
