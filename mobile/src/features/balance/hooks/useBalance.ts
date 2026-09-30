import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { sincronizar } from '../../gastos/sincronizador';
import type { BalancePersonalRespuesta } from '../../../api/tipos';
import { agregarIngreso, traerBalance } from '../api';

/**
 * El "controlador" de Mi Plata: mismo molde que `useSaldo`/`useResumen`
 * (datos/cargando/error/recargar), con `registrar` para agregar un ingreso
 * (o corregir uno anterior, con un monto negativo).
 *
 * Sincroniza antes de leer, igual que esos dos hooks: sin esto, un gasto
 * personal recien cargado offline no entraria en "gastado" hasta que la
 * cola se vacie sola, y el restante se veria desactualizado justo al lado
 * del resto de la pantalla de resumen, que ya sincronizo.
 */
export function useBalance() {
  const [balance, setBalance] = useState<BalancePersonalRespuesta | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    setCargando(true);
    try {
      await sincronizar();
      setBalance(await traerBalance());
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer Mi Plata.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  /** Agrega un ingreso y refresca el balance. */
  const registrar = useCallback(async (monto: number) => {
    await agregarIngreso(monto);
    await recargar();
  }, [recargar]);

  return { balance, cargando, error, recargar, registrar };
}
