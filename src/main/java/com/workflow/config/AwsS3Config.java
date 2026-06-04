package com.workflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true", matchIfMissing = false)
public class AwsS3Config {

    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKey;

    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretKey;

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    @Value("${AWS_S3_BUCKET:wm-documentos}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        // Trim defensivo: elimina espacios, \r (CRLF de Windows) y cualquier
        // caracter de control que pueda haber quedado al parsear el .env como .properties
        String cleanAccessKey = accessKey != null ? accessKey.trim() : "";
        String cleanSecretKey = secretKey != null ? secretKey.trim() : "";
        String cleanRegion    = region    != null ? region.trim()    : "us-east-1";

        // Log de verificacion: muestra primeros 4 y ultimos 4 del access key ID
        // NUNCA registrar el secret key completo
        if (!cleanAccessKey.isBlank()) {
            String preview = cleanAccessKey.length() > 8
                ? cleanAccessKey.substring(0, 4) + "****" + cleanAccessKey.substring(cleanAccessKey.length() - 4)
                : "****";
            log.info("AWS_ACCESS_KEY_ID leido correctamente: {} (longitud={})",
                preview, cleanAccessKey.length());
        }

        // useArnRegionEnabled: si el bucket esta en una region distinta a la configurada,
        // el SDK seguira el redirect de AWS en lugar de lanzar SignatureDoesNotMatch.
        S3Configuration s3Config = S3Configuration.builder()
            .useArnRegionEnabled(true)
            .build();

        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(cleanRegion))
            .serviceConfiguration(s3Config);

        if (!cleanAccessKey.isBlank() && !cleanSecretKey.isBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)
                )
            );
            log.info("S3Client configurado con credenciales estaticas. Region={}, secretKey.length={}.",
                cleanRegion, cleanSecretKey.length());
        } else {
            // Fallback: usa la cadena de credenciales de AWS (variables de entorno del SO, IAM role, etc.)
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.warn("AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY no encontradas en .env. "
                + "Se usara DefaultCredentialsProvider (IAM Role / variables de entorno del SO).");
        }

        S3Client client = builder.build();

        // Prueba de conectividad al arrancar: detecta region incorrecta antes de la primera subida.
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 conectividad OK: bucket '{}' accesible en region '{}'.", bucket, cleanRegion);
        } catch (S3Exception ex) {
            // AWS incluye la region real del bucket en la cabecera x-amz-bucket-region del error 301/403.
            String bucketRegion = ex.awsErrorDetails() != null
                ? ex.awsErrorDetails().sdkHttpResponse()
                    .firstMatchingHeader("x-amz-bucket-region").orElse("desconocida")
                : "desconocida";
            log.error("S3 conectividad FALLO: httpStatus={}, codigo='{}', mensaje='{}'. "
                + "Region configurada='{}', region real del bucket segun AWS='{}'.",
                ex.statusCode(), ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : "?",
                ex.getMessage(), cleanRegion, bucketRegion);
        } catch (Exception ex) {
            log.error("S3 conectividad FALLO (error inesperado): {}", ex.getMessage());
        }

        return client;
    }
}
