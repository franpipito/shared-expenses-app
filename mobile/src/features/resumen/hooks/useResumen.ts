import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { sincronizar } from '../../gastos/sincronizador';
import { mesActual } from '../../../api/periodo';
import type { ResumenRespuesta } from '../../../api/tipos';
import { traerResumen } from '../api';

/**
 * El "controlador" de la pantalla de resumen, en terminos de MVC.
 *
 * `docs/diseno.md` explica el mapeo: los hooks son la capa de control. Este
 * hook tiene el estado y la llamada; la pantalla solo dibuja lo que le llega.
 * Lo que se gana es que el estado de una feature se puede leer entero en un
 * archivo de treinta lineas, sin abrir la pantalla.
 *
 * No hay libreria de estado de servidor (React Query y parientes) a proposito:
 * son tres pantallas y cada una pide sus datos cuando se muestra. Cuando haya
 * cache que invalidar entre pantallas, ahi la discusion cambia.
 */
export function useResumen() {
  const [resumen, setResumen] = useState<ResumenRespuesta | null>(null);
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
      // Vaciar la cola ANTES de leer, y no despues: si hay gastos esperando,
      // el resumen que traigamos sin mandarlos primero seria viejo por
      // definicion, y la nutria opinaria sobre numeros incompletos.
      //
      // sincronizar() nunca rechaza: un fallo al escribir la cola no tiene por
      // que impedir que se lea el resumen.
      await sincronizar();
      setResumen(await traerResumen(mesActual()));
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el resumen.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { resumen, cargando, error, recargar };
}
