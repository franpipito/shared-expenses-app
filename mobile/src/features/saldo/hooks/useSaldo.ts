import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { sincronizar } from '../../gastos/sincronizador';
import type { SaldoRespuesta } from '../../../api/tipos';
import { useMes } from '../../mes/mes';
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
  const { mes } = useMes();
  const [saldo, setSaldo] = useState<SaldoRespuesta | null>(null);
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
      setSaldo(await traerSaldo(mes));
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el saldo.');
    } finally {
      setCargando(false);
    }
  }, [mes]);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { saldo, cargando, error, recargar };
}
