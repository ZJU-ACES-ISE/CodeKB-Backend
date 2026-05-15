package com.codekb.graph;

import com.codekb.common.BusinessException;
import com.codekb.config.GraphServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class GraphServiceClient {

    private static final Logger log = LoggerFactory.getLogger(GraphServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GraphServiceClient(GraphServiceProperties props, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createJob(String githubUrl, String ref, int depth) {
        try {
            Map<String, Object> body = Map.of(
                    "github_url", githubUrl,
                    "ref", ref != null ? ref : "",
                    "depth", depth
            );
            String resp = restClient.post()
                    .uri("/api/graph-jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("createJob failed: {}", e.getMessage());
            throw new BusinessException(500, "Graph 服务请求失败: " + e.getMessage());
        }
    }

    /**
     * Graphify：multipart 上传 zip，字段 file；可选 repo_name。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadJob(byte[] zipBytes, String filename, String repoName) {
        try {
            String fn = (filename == null || filename.isBlank()) ? "upload.zip" : filename;
            MultipartBodyBuilder mb = new MultipartBodyBuilder();
            mb.part("file", new ByteArrayResource(zipBytes) {
                @Override
                public String getFilename() {
                    return fn;
                }
            });
            if (repoName != null && !repoName.isBlank()) {
                mb.part("repo_name", repoName);
            }
            String resp = restClient.post()
                    .uri("/api/graph-jobs/upload")
                    .body(mb.build())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("uploadJob failed: {}", e.getMessage());
            throw new BusinessException(500, "Graph ZIP 上传失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobStatus(String jobId) {
        try {
            String resp = restClient.get()
                    .uri("/api/graph-jobs/" + jobId)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("getJobStatus failed jobId={}: {}", jobId, e.getMessage());
            throw new BusinessException(500, "Graph 服务状态查询失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGraph(String jobId) {
        try {
            String resp = restClient.get()
                    .uri("/api/graph-jobs/" + jobId + "/graph")
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("getGraph failed jobId={}: {}", jobId, e.getMessage());
            throw new BusinessException(500, "Graph 服务获取图数据失败: " + e.getMessage());
        }
    }
}
