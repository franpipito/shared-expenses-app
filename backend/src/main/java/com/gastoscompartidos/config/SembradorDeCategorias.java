package com.gastoscompartidos.config;

import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Siembra las categorias al arrancar. Es lo que reemplaza a la migracion V2 de
 * Flyway, y NO es lo mismo.
 *
 * Las categorias son datos de referencia: la app no funciona sin ellas, asi que
 * tienen que viajar con el codigo y no en un script aparte que alguien se puede
 * olvidar de correr en produccion. Eso no cambio.
 *
 * LO QUE SI CAMBIO, y conviene tenerlo claro:
 *
 *  - Flyway registraba en una tabla que la migracion ya se habia aplicado, y
 *    guardaba un checksum del archivo. Si alguien editaba una migracion ya
 *    aplicada, la app NO ARRANCABA.
 *  - Esto corre en cada arranque y tiene que verificar por su cuenta que no
 *    haya duplicados. Es idempotente porque lo escribimos idempotente, no
 *    porque nadie lo controle.
 *  - Y sobre todo: esto siembra, pero NO migra. Si algun dia hay que cambiarle
 *    la forma a documentos ya escritos, esta clase no sirve para eso. La
 *    herramienta del ecosistema Mongo para migraciones versionadas es Mongock.
 *
 * Se agregan las que falten y no se toca ninguna existente: si alguien renombro
 * una categoria desde la base, un arranque no se lo pisa.
 */
@Configuration
public class SembradorDeCategorias {

    private static final Logger log = LoggerFactory.getLogger(SembradorDeCategorias.class);

    /**
     * Las palabras que uso la usuaria en la entrevista, no las que suponiamos
     * nosotros. Dijo "uber", no "transporte". Ver docs/entrevista-usuaria.md.
     *
     * "otros" no lo nombro ella, pero sin un cajon de sastre un gasto que no
     * encaja en ninguna categoria no se puede cargar, y eso es friccion justo en
     * el peor momento: parada en el mostrador con el pedido listo.
     *
     * El valor es el nombre del icono de Lucide, NO un emoji.
     */
    private static final Map<String, String> CATEGORIAS = new LinkedHashMap<>(Map.of(
            "cafe", "coffee",
            "uber", "car",
            "comida", "utensils",
            "ropa", "shirt",
            "regalos", "gift",
            "otros", "ellipsis"
    ));

    /**
     * ApplicationRunner corre una vez, despues de que el contexto de Spring
     * termino de levantar. Es el gancho correcto para esto: si se hiciera en el
     * constructor de un bean, correria en medio del arranque y con parte del
     * contexto todavia a medio construir.
     */
    @Bean
    ApplicationRunner sembrarCategorias(CategoriaRepositorio categorias) {
        return args -> CATEGORIAS.forEach((nombre, icono) -> {
            if (categorias.findByNombre(nombre).isEmpty()) {
                categorias.save(new Categoria(nombre, icono));
                log.info("Categoria sembrada: {}", nombre);
            }
        });
    }
}
