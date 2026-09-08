import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import type { ResumenRespuesta } from '../../../api/tipos';
import { mesActual, traerResumen } from '../api';

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
    try {
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
