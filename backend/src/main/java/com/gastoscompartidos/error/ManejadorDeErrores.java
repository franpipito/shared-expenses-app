package com.gastoscompartidos.error;

import com.gastoscompartidos.seguridad.NoAutenticadoException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones a respuestas HTTP, para toda la aplicacion.
 *
 * @RestControllerAdvice hace que estos metodos apliquen a TODOS los
 * controladores. La alternativa seria un try/catch en cada endpoint, que se
 * repite y se olvida. Aca la regla vive en un solo lugar.
 *
 * El beneficio de fondo: los servicios lanzan excepciones que hablan del
 * dominio ("no existe el gasto 7") y no saben nada de HTTP. La traduccion a
 * codigos de estado ocurre en el borde.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(NoAutenticadoException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorRespuesta noAutenticado(NoAutenticadoException e) {
        return ErrorRespuesta.de(e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorRespuesta noEncontrado(RecursoNoEncontradoException e) {
        return ErrorRespuesta.de(e.getMessage());
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorRespuesta reglaDeNegocio(ReglaDeNegocioException e) {
        return ErrorRespuesta.de(e.getMessage());
    }

    /**
     * La lanza Spring cuando un @RequestBody anotado con @Valid no pasa las
     * validaciones del DTO. Devolvemos el detalle campo por campo para que el
     * cliente pueda marcar los inputs que fallaron.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorRespuesta validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errores.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return new ErrorRespuesta("Hay campos invalidos", errores);
    }

    /**
     * Es el @Version de la entidad haciendo su trabajo: alguien modifico el
     * mismo gasto mientras esta request lo estaba editando. 409 Conflict le
     * dice al cliente "recarga y volve a intentar", que es la accion correcta.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorRespuesta conflicto(OptimisticLockingFailureException e) {
        return ErrorRespuesta.de("El gasto fue modificado por otra persona. Recarga y volve a intentar.");
    }
}
