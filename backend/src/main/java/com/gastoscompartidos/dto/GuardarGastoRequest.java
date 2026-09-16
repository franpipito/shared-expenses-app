package com.gastoscompartidos.dto;

import com.gastoscompartidos.modelo.TipoGasto;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que la API recibe para crear o editar un gasto.
 *
 * Las anotaciones son de Bean Validation (jakarta.validation). El controlador
 * marca el parametro con @Valid y Spring las evalua ANTES de llamar al
 * servicio; si algo falla, devuelve 400 con el detalle campo por campo y el
 * servicio nunca se entera.
 *
 * Estas validaciones son las que se pueden decidir mirando solo el JSON. Las que
 * necesitan ir a la base ("existe esa categoria?", "ese gasto es de tu grupo?")
 * viven en el servicio.
 *
 * Ojo con lo que NO se pide: la fecha de creacion la pone la entidad, el grupo
 * sale del usuario, y `montoPagador` lo calcula el backend. Cuanto menos
 * confie la API en el cliente, menos formas hay de que lleguen datos
 * inconsistentes.
 *
 * @param porcentajePagador que porcentaje del gasto le toca a quien pago. Solo
 *                          aplica a los COMPARTIDO; en los PERSONAL se ignora.
 *                          Si viene null, se asume 50.
 * @param pagadoPorId       quien pago. Si viene null, se asume el usuario de la
 *                          request. Solo se puede indicar otro integrante en los
 *                          gastos COMPARTIDO.
 * @param version           solo se usa en PUT. Si viene, el backend verifica que
 *                          nadie haya modificado el gasto desde que lo leiste.
 * @param pozoId            si viene, el gasto sale de la vaquita. Obliga a que
 *                          `tipo` sea COMPARTIDO, y hace que `porcentajePagador`
 *                          se ignore: un gasto del pozo es mitad y mitad por
 *                          construccion, porque el pozo se financio entre los dos.
 */
public record GuardarGastoRequest(

        @NotNull(message = "el monto es obligatorio")
        /*
         * @DecimalMin y no @Positive: @Positive acepta 0.001, y despues
         * normalizar() lo lleva a 0.00 con HALF_UP. Quedaba un gasto de cero
         * pesos guardado, con el mensaje de validacion diciendo lo contrario.
         *
         * @Digits reemplaza al NUMERIC(12,2) que ponia Postgres y que Mongo no
         * tiene como declarar. Sin el, un monto de 35 digitos revienta al
         * escribir con NumberFormatException ("Conversion to Decimal128 would
         * require inexact rounding") y sale como 500 en vez del 400 que
         * corresponde: el error es del input, no del servidor.
         */
        @DecimalMin(value = "0.01", message = "el monto tiene que ser mayor a cero")
        @Digits(integer = 12, fraction = 2, message = "el monto es demasiado grande")
        BigDecimal monto,

        @NotNull(message = "la categoria es obligatoria")
        String categoriaId,

        @NotNull(message = "la fecha es obligatoria")
        @PastOrPresent(message = "la fecha no puede ser futura")
        LocalDate fecha,

        @NotBlank(message = "la descripcion es obligatoria")
        @Size(max = 255, message = "la descripcion no puede pasar de 255 caracteres")
        String descripcion,

        @NotNull(message = "el tipo es obligatorio (PERSONAL o COMPARTIDO)")
        TipoGasto tipo,

        @Min(value = 0, message = "el porcentaje va de 0 a 100")
        @Max(value = 100, message = "el porcentaje va de 0 a 100")
        Integer porcentajePagador,

        String pagadoPorId,

        /*
         * Boolean y no boolean, a proposito.
         *
         * Con el primitivo, un JSON que no traiga el campo hace que Jackson
         * falle al construir el record ("Cannot map null into type boolean") y
         * la request muere con 400 ANTES de llegar a Bean Validation. Es decir:
         * te devuelve un error de validacion que no viene de una validacion.
         *
         * Con el wrapper, ausente significa null y el servicio lo interpreta
         * como false, que es el default correcto: un gasto no es hormiga salvo
         * que quien lo carga lo marque.
         */
        Boolean esHormiga,

        Long version,

        /*
         * La vaquita. Null es el caso normal: un gasto de la vida de todos los
         * dias. Ver docs/vaquita.md.
         */
        String pozoId,

        /*
         * La clave de idempotencia que genera el telefono. Ver el javadoc del
         * campo en Gasto: es lo que hace que la cola offline pueda reintentar un
         * POST sin riesgo de crear el gasto dos veces.
         *
         * Opcional a proposito: un cliente que no la mande funciona igual.
         */
        /*
         * @Pattern ademas de @Size: un clienteId vacio pasaba, y Spring Data SI
         * escribe el string vacio (solo omite los null). Con dos gastos
         * mandando "" los dos entran al indice parcial unico, el segundo choca,
         * y guardarUnaSolaVez lo interpreta como "ya estaba": devuelve el primer
         * gasto con 201 y el segundo **nunca se guarda**. Es el espejo exacto
         * del bug que la clave de idempotencia existe para evitar, y lo dispara
         * un `?? ''` mal puesto en el cliente.
         */
        @Size(max = 64, message = "el id de cliente no puede pasar de 64 caracteres")
        @Pattern(regexp = "[A-Za-z0-9_.:-]+", message = "el id de cliente tiene caracteres invalidos")
        String clienteId
) {
}
