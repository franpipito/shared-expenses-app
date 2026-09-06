package com.gastoscompartidos.modelo;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genera el DDL que Hibernate deduce de las entidades, usando el dialecto de
 * PostgreSQL pero SIN conectarse a ninguna base. Sirve para dos cosas:
 *
 *  1. Validar que los mapeos JPA son coherentes: una anotacion mal puesta
 *     revienta aca, no en runtime contra la base.
 *  2. Poder leer el SQL exacto que producen las entidades, en target/schema.sql.
 */
class EsquemaGeneradoTest {

    @Test
    void generaElEsquemaEsperado() throws Exception {
        Path salida = Path.of("target", "schema.sql");
        Files.deleteIfExists(salida);

        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // "scripts" (y no "database") es lo que hace que escriba a archivo en
        // lugar de intentar abrir una conexion.
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", salida.toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        settings.put("hibernate.format_sql", "true");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Grupo.class)
                    .addAnnotatedClass(Usuario.class)
                    .addAnnotatedClass(Categoria.class)
                    .addAnnotatedClass(Gasto.class)
                    .buildMetadata();

            SchemaManagementToolCoordinator.process(
                    metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);

            String ddl = Files.readString(salida).toLowerCase();

            assertTrue(ddl.contains("create table grupo"), "falta la tabla grupo");
            assertTrue(ddl.contains("create table usuario"), "falta la tabla usuario");
            assertTrue(ddl.contains("create table categoria"), "falta la tabla categoria");
            assertTrue(ddl.contains("create table gasto"), "falta la tabla gasto");

            // El punto del modelo: los montos son NUMERIC(12,2), no float.
            assertTrue(ddl.contains("numeric(12,2)"), "los montos no quedaron como numeric(12,2)");
            // Y el reparto se guarda resuelto, no como porcentaje.
            assertTrue(ddl.contains("monto_pagador"), "falta la columna monto_pagador");

            // La feature central: la marca de gasto hormiga, obligatoria.
            assertTrue(ddl.contains("es_hormiga boolean not null"),
                    "es_hormiga tiene que existir y ser not null");
            // La descripcion la eligio la usuaria como uno de sus tres campos.
            assertTrue(ddl.contains("descripcion varchar(255) not null"),
                    "descripcion tiene que ser obligatoria");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
