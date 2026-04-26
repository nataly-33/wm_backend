package com.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Force JSON-only message converters to prevent XML serialization
        // (jackson-dataformat-xml on classpath via firebase-admin causes XML to be used)
        restTemplate.setMessageConverters(List.of(
                new MappingJackson2HttpMessageConverter(),
                new StringHttpMessageConverter(),
                new ByteArrayHttpMessageConverter()
        ));
        return restTemplate;
    }
}
