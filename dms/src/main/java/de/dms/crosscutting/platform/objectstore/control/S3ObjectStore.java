package de.dms.crosscutting.platform.objectstore.control;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

/** S3-compatible binding (HF Bucket / AWS S3 / MinIO) via AWS SDK v2 (C-1, R-3). */
public class S3ObjectStore implements ObjectStore {

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStore(String endpoint, String region, String keyId, String secret, String bucket) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, secret));
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes) {
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot write " + key, e);
        }
    }

    @Override
    public void put(String key, InputStream in, long sizeBytes) {
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromInputStream(in, sizeBytes));
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot write " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray();
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot read " + key, e);
        }
    }

    @Override
    public InputStream stream(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot stat " + key, e);
        }
    }

    @Override
    public List<String> list(String keyPrefix) {
        try {
            return s3.listObjectsV2Paginator(
                            ListObjectsV2Request.builder().bucket(bucket).prefix(keyPrefix).build())
                    .stream()
                    .flatMap(page -> page.contents().stream())
                    .map(S3Object::key)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot list " + keyPrefix, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("cannot delete " + key, e);
        }
    }

    @Override
    public void verifyReachable() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("bucket unreachable", e);
        }
    }
}
