package com.gestioncomunidad.backend.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvidenciasConfig {
    // Limita el archivo a 5 MiB y deja margen para los campos multipart de la petición.
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", 5L * 1024 * 1024, 6L * 1024 * 1024, 0);
    }
}
