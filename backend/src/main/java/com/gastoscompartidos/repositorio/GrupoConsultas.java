package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Grupo;

import java.time.Instant;
import java.util.Optional;

/**
 * Las escrituras de Grupo que no se pueden hacer con save().
 *
 * Mismo mecanismo de fragmento que {@link GastoConsultas} y PozoConsultas:
 * Spring Data busca una clase con este nombre + "Impl".
 */
public interface GrupoConsultas {

    /**
     * Busca el grupo por su codigo de invitacion vigente y, si lo encuentra,
     * lo consume en la MISMA operacion: nadie mas va a poder usar ese codigo
     * despues de esta llamada, sin importar cuantas requests lleguen a la vez.
     *
     * Es el patron de UPDATE ... WHERE condicional, otra vez: el filtro
     * (codigo + vigencia) y la escritura (borrar el codigo) viajan juntos, asi
     * que no hay ventana entre "lo encontre" y "lo invalido" donde dos
     * personas puedan sumarse con el mismo codigo.
     *
     * @return el grupo TAL COMO ESTABA antes de consumir el codigo (por eso
     *         sirve para saber a donde sumar a quien se une), o vacio si el
     *         codigo no existe o ya vencio.
     */
    Optional<Grupo> consumirInvitacion(String codigo, Instant ahora);
}
