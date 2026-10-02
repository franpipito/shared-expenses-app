import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import type { LiquidacionRespuesta, SaldoTotalRespuesta } from '../../../api/tipos';
import { borrarLiquidacion, editarLiquidacion, registrarLiquidacion, traerLiquidaciones, traerSaldoTotal } from '../api';

/**
 * El "controlador" de la pantalla de liquidaciones. Mismo molde que
 * `useSaldo` (datos / cargando / error / recargar), pero sin `mes`: el saldo
 * total no depende del selector de mes, y sin `sincronizar()` tampoco --
 * liquidaciones no pasa por la cola offline, igual que editar y borrar un
 * gasto (es una correccion deliberada, no el alta rapida que la cola existe
 * para proteger).
 *
 * `editar`/`borrar`: cualquiera de los dos integrantes puede corregir
 * CUALQUIER liquidacion del grupo, no solo las que registro -- a diferencia
 * de `editar`/`borrar` en `useBalance` (un ingreso es personal). No hay
 * "dueño" de un pago entre dos personas.
 */
export function useSaldoTotal() {
  const [total, setTotal] = useState<SaldoTotalRespuesta | null>(null);
  const [historial, setHistorial] = useState<LiquidacionRespuesta[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(async () => {
    setError(null);
    setCargando(true);
    try {
      const [totalNuevo, historialNuevo] = await Promise.all([traerSaldoTotal(), traerLiquidaciones()]);
      setTotal(totalNuevo);
      setHistorial(historialNuevo);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el saldo total.');
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  /** Registra el pago y refresca el total y el historial. */
  const registrar = useCallback(async (monto: number, meLoPagaron: boolean) => {
    await registrarLiquidacion(monto, meLoPagaron);
    await recargar();
  }, [recargar]);

  /** Corrige el monto de un pago ya registrado y refresca el total y el historial. */
  const editar = useCallback(async (id: string, monto: number) => {
    await editarLiquidacion(id, monto);
    await recargar();
  }, [recargar]);

  /** Saca un pago del historial y refresca el total y el historial. */
  const borrar = useCallback(async (id: string) => {
    await borrarLiquidacion(id);
    await recargar();
  }, [recargar]);

  return { total, historial, cargando, error, recargar, registrar, editar, borrar };
}
