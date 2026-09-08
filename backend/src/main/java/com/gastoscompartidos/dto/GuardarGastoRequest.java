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
 */
public record GuardarGastoRequest(

        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto tiene que ser mayor a cero")
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

        Long version
) {
}
