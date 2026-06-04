package com.workflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import jakarta.annotation.PostConstruct;

@Configuration
public class MongoConfig {

    @Autowired
    private MappingMongoConverter mappingMongoConverter;

    @PostConstruct
    public void setUpMongoEscapeCharacterConversion() {
        if (mappingMongoConverter != null) {
            mappingMongoConverter.setMapKeyDotReplacement("__dot__");
        }
    }
}
