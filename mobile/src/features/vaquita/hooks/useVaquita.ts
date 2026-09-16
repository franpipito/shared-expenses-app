import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { sincronizar } from '../../gastos/sincronizador';
import type { GastoRespuesta, PozoRespuesta } from '../../../api/tipos';
import { traerGastosDelPozo, traerPozoActivoFresco, traerPozos } from '../api';

/**
 * El "controlador" de la pantalla de la vaquita.
 *
 * Misma forma que `useSaldo`, `useResumen` y `useGastos` (datos / cargando /
 * error / recargar). Es el cuarto con la misma forma, asi que el comentario de
 * `useSaldo` sigue valiendo y ahora con mas fuerza: **aca ya se justifica
 * extraer un `usePedido(fn)` generico.** Se sigue sin hacer por lo mismo:
 * refactorizar cuatro pantallas que todavia no se probaron en un telefono es
 * empezar por el lugar equivocado.
 *
 * Lo que este hook agrega sobre los otros tres es que trae DOS cosas
 * encadenadas: el pozo y, solo si hay uno, sus gastos. Por eso la segunda
 * llamada esta adentro del mismo try.
 */
export function useVaquita() {
  const [pozo, setPozo] = useState<PozoRespuesta | null>(null);
  const [gastos, setGastos] = useState<GastoRespuesta[]>([]);
  /**
   * Los viajes ya cerrados. Sin esto sus gastos eran inalcanzables: no salen en
   * la lista del mes y `/pozos/activo` deja de devolverlos apenas se cierran.
   */
  const [cerrados, setCerrados] = useState<PozoRespuesta[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    // `cargando` tiene que volver a true aca, y no solo al montar: si no, el
    // spinner del pull-to-refresh rebota y desaparece al instante, y durante los
    // 40-60 segundos que tarda Render en despertar no hay ninguna senial de que
    // algo este pasando. La reaccion natural es tirar otra vez, y otra.
    setCargando(true);
    try {
      // Igual que el resumen y la lista: primero se manda lo que quedo en la
      // cola. Sin esto, llegar al hotel con wifi y abrir el saldo o la vaquita
      // muestra numeros que NO incluyen los gastos cargados sin senial, y nada
      // en pantalla lo insinua. En la vaquita es peor: `restante` es el numero
      // que contesta "nos alcanza para la cena buena".
      await sincronizar();
    } catch {
      // No puede pasar (sincronizar no rechaza), pero si pasara no tiene que
      // impedir la lectura.
    }
    try {
      const activo = await traerPozoActivoFresco();
      setPozo(activo);
      // Sin pozo no hay gastos que pedir, y pedirlos igual seria una request de
      // mas en el caso mas comun (todavia no abrieron ninguna vaquita).
      setGastos(activo ? await traerGastosDelPozo(activo.id) : []);
      setCerrados((await traerPozos()).filter((p) => p.estado === 'CERRADO'));
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer la vaquita.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  /**
   * Para despues de crear, aportar o cerrar: esas tres llamadas ya devuelven el
   * pozo actualizado, asi que volver a pedirlo seria una request al pedo.
   */
  const fijarPozo = useCallback((actualizado: PozoRespuesta | null) => {
    setPozo(actualizado);
  }, []);

  return { pozo, gastos, cerrados, cargando, error, recargar, fijarPozo };
}
