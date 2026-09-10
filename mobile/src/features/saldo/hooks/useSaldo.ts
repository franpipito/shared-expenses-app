import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { mesActual } from '../../../api/periodo';
import type { SaldoRespuesta } from '../../../api/tipos';
import { traerSaldo } from '../api';

/**
 * El "controlador" de la pantalla de saldo.
 *
 * Es el tercero con esta forma exacta (datos / cargando / error / recargar),
 * despues de `useResumen` y `useGastos`. Con tres casos iguales ya se ve cual es
 * la parte que varia -- es solo la funcion que pide -- asi que **aca si tiene
 * sentido extraer un hook generico**, algo como `usePedido(fn)`.
 *
 * No se hace en esta tanda a proposito: seria refactorizar tres pantallas que
 * todavia no se probaron en un telefono. Primero que anden, despues se limpia.
 */
export function useSaldo() {
  const [saldo, setSaldo] = useState<SaldoRespuesta | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    try {
      setSaldo(await traerSaldo(mesActual()));
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el saldo.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { saldo, cargando, error, recargar };
}
