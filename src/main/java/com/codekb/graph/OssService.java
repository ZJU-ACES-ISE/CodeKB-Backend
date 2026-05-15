package com.codekb.graph;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;

    public OssService(
            @Value("${codekb.oss.endpoint}") String endpoint,
            @Value("${codekb.oss.access-key-id}") String accessKeyId,
            @Value("${codekb.oss.access-key-secret}") String accessKeySecret,
            @Value("${codekb.oss.bucket}") String bucketName) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
    }

    private OSS buildClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    public void uploadJson(String key, String json) {
        OSS client = buildClient();
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            client.putObject(bucketName, key, new ByteArrayInputStream(bytes));
            log.info("OSS upload success: {}", key);
        } finally {
            client.shutdown();
        }
    }

    public String downloadJson(String key) {
        OSS client = buildClient();
        try {
            OSSObject obj = client.getObject(bucketName, key);
            try (InputStream is = obj.getObjectContent()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("OSS download failed for key={}: {}", key, e.getMessage());
            return null;
        } finally {
            client.shutdown();
        }
    }

    public void deleteObject(String key) {
        OSS client = buildClient();
        try {
            client.deleteObject(bucketName, key);
            log.info("OSS object deleted: {}", key);
        } finally {
            client.shutdown();
        }
    }
}
