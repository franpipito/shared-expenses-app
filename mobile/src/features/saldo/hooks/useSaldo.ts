import { useCallback, useEffect, useState } from 'react';

import { ErrorDeApi } from '../../../api/cliente';
import { sincronizar } from '../../gastos/sincronizador';
import type { SaldoTotalRespuesta } from '../../../api/tipos';
import { traerSaldoTotal } from '../api';

/**
 * El "controlador" de la pantalla de saldo: quien le debe a quien, de toda la
 * historia (`GET /saldo/total`), no del mes.
 *
 * **Antes pedia `GET /saldo?mes=`, y era un bug real encontrado en el
 * telefono**: ese endpoint nunca resta `Liquidacion`, asi que apenas alguien
 * anotaba un pago en "Saldar cuentas", esta pantalla seguia mostrando la
 * deuda vieja. El motivo original para acotar el saldo al mes -- "no hay
 * forma de saldar la cuenta" -- ya no aplica desde que existe `Liquidacion`
 * (seccion 2.3), asi que el numero correcto para "quien le debe a quien" es
 * el historico neto de pagos, no el del mes. Detalle completo en
 * `app/saldo.tsx` y en el CLAUDE.md, seccion "Saldar deudas".
 *
 * Es el tercero con esta forma exacta (datos / cargando / error / recargar),
 * despues de `useResumen` y `useGastos`. Con tres casos iguales ya se ve cual es
 * la parte que varia -- es solo la funcion que pide -- asi que **aca si tiene
 * sentido extraer un hook generico**, algo como `usePedido(fn)`.
 *
 * No se hace en esta tanda a proposito: seria refactorizar tres pantallas que
 * recien se estan terminando de probar en un telefono. Primero que anden,
 * despues se limpia.
 */
export function useSaldo() {
  const [saldo, setSaldo] = useState<SaldoTotalRespuesta | null>(null);
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
      // cola. Sin esto, un gasto compartido cargado sin senial no entraria en
      // el total hasta que se sincronice solo, y esta pantalla mostraria un
      // numero viejo sin ningun aviso.
      await sincronizar();
    } catch {
      // No puede pasar (sincronizar no rechaza), pero si pasara no tiene que
      // impedir la lectura.
    }
    try {
      setSaldo(await traerSaldoTotal());
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
