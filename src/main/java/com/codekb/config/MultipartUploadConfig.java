package com.codekb.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * ZIP 构图上传需要较大 multipart 限制；不修改 application.yml，在此用编程方式放宽。
 */
@Configuration
public class MultipartUploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(512));
        factory.setMaxRequestSize(DataSize.ofMegabytes(512));
        return factory.createMultipartConfig();
    }
}
