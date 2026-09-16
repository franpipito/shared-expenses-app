package com.gastoscompartidos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;

import java.time.LocalDate;
import java.util.List;

/**
 * Como se guarda en Mongo el tipo que no tiene equivalente en BSON: `LocalDate`.
 *
 * ACA ESTABAN TAMBIEN LOS CONVERSORES DE BigDecimal, y se fueron a proposito:
 * Spring Boot 4 lo resuelve con una propiedad,
 * `spring.data.mongodb.representation.big-decimal=decimal128`, que hace
 * exactamente lo mismo sin codigo. Escribir a mano lo que el framework ya
 * ofrece es codigo que hay que mantener y que ademas puede quedar desalineado
 * con lo que hace Spring Data internamente.
 *
 * Lo que si hay que escribir es esto, porque no hay una propiedad equivalente.
 */
@Configuration
public class ConfiguracionMongo {

    /**
     * OJO CON ESTE BEAN: declararlo APAGA una propiedad de application.properties.
     *
     * `DataMongoConfiguration.mongoCustomConversions()` de Spring Boot esta
     * anotado `@ConditionalOnMissingBean`. Al declarar el nuestro, el de Boot no
     * se crea, y con el se va el unico lugar que aplica
     * `spring.data.mongodb.representation.big-decimal=decimal128`: el default
     * del adaptador es UNSPECIFIED.
     *
     * Hoy los montos igual se guardan como Decimal128, pero **por otro motivo**:
     * con UNSPECIFIED el BigDecimal llega crudo al driver y ahi lo agarra
     * `BigDecimalCodec`. O sea que la garantia que el CLAUDE.md atribuye a la
     * propiedad la estaba dando el codec del driver. Dos consecuencias feas:
     * cambiar la propiedad no hacia nada (alguien podia creer que probo algo), y
     * con UNSPECIFIED quedaban registrados los DOS conversores de lectura, asi
     * que un documento con la plata guardada como texto se leia en silencio en
     * vez de fallar -- lo contrario de lo que queremos.
     *
     * `create(...)` en vez del constructor deja pedir la representacion explicita
     * y registrar los conversores propios en la misma pasada.
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adaptador -> {
            adaptador.bigDecimal(BigDecimalRepresentation.DECIMAL128);
            adaptador.registerConverters(List.of(
                    new LocalDateATexto(),
                    new TextoALocalDate()
            ));
        });
    }

    /**
     * FECHAS: LocalDate -> texto ISO ("2026-09-07").
     *
     * Mongo NO tiene un tipo "fecha sin hora". Su tipo Date es un instante en
     * UTC, asi que el default convierte un LocalDate a la medianoche UTC de ese
     * dia — y ahi vuelve exactamente el bug que nos costo el bean `Clock`: un
     * gasto del 30 de septiembre en Buenos Aires puede leerse como 1 de octubre
     * segun quien y como lo consulte.
     *
     * Guardandola como texto ISO el dia es el dia y no depende de ninguna zona.
     * Y no se pierde nada al consultar: **las fechas ISO ordenan igual como
     * texto que como fecha**, porque el formato va de la unidad mas grande a la
     * mas chica y todos los campos tienen ancho fijo. Por eso el rango
     * semiabierto [desde, hasta) con $gte y $lt sigue funcionando igual que el
     * BETWEEN de SQL.
     *
     * El costo, que hay que saber: no se pueden usar los operadores de fecha de
     * Mongo ($year, $month, $dateDiff) sobre este campo. No los usamos — el
     * periodo lo calcula `Periodo` en Java, con la zona correcta.
     */
    @WritingConverter
    static class LocalDateATexto implements Converter<LocalDate, String> {
        @Override
        public String convert(LocalDate fuente) {
            return fuente.toString(); // ISO-8601: yyyy-MM-dd
        }
    }

    @ReadingConverter
    static class TextoALocalDate implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String fuente) {
            return LocalDate.parse(fuente);
        }
    }
}
