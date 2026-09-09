import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { mesActual } from '../../../api/periodo';
import type { GastoRespuesta } from '../../../api/tipos';
import { traerGastos } from '../api';

/**
 * El "controlador" de la pantalla de gastos, en terminos de MVC.
 *
 * Es hermano de `useResumen` y a proposito se parece: mismo estado
 * (datos / cargando / error), misma funcion `recargar`. Cuando dos hooks de dos
 * features se parecen tanto, la tentacion es abstraerlos en un `usePedido`
 * generico. No se hace todavia: con dos casos, la abstraccion adivina cual es la
 * parte que varia, y suele adivinar mal. Con el tercero se ve de verdad.
 *
 * Igual que en el resumen, no hay libreria de estado de servidor: cada pantalla
 * pide sus datos cuando se muestra.
 */
export function useGastos() {
  const [gastos, setGastos] = useState<GastoRespuesta[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    try {
      setGastos(await traerGastos(mesActual()));
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron traer los gastos.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { gastos, cargando, error, recargar };
}
