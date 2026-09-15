import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import type { GastoRespuesta, PozoRespuesta } from '../../../api/tipos';
import { traerGastosDelPozo, traerPozoActivo } from '../api';

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
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    try {
      const activo = await traerPozoActivo();
      setPozo(activo);
      // Sin pozo no hay gastos que pedir, y pedirlos igual seria una request de
      // mas en el caso mas comun (todavia no abrieron ninguna vaquita).
      setGastos(activo ? await traerGastosDelPozo(activo.id) : []);
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

  return { pozo, gastos, cargando, error, recargar, fijarPozo };
}
