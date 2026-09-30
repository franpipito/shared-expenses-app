import { pedir } from '../../api/cliente';
import type { LiquidacionRespuesta, SaldoRespuesta, SaldoTotalRespuesta } from '../../api/tipos';

/**
 * El saldo del mes: quien le debe a quien.
 *
 * OJO CON EL ALCANCE, que es una decision de producto y no un limite tecnico:
 * **este saldo es del mes pedido, no historico.** Acotarlo al mes lo
 * mantiene chico y accionable, al costo de asumir que se arreglan mes a
 * mes. La pantalla lo dice con todas las letras, porque un saldo que se
 * resetea sin avisar es peor que no tenerlo.
 *
 * El historico SI existe, aparte (seccion 2.3 de CLAUDE.md): ver
 * `traerSaldoTotal`. Este endpoint no cambia -- sigue siendo el pulso del
 * mes, no la cuenta completa.
 */
export function traerSaldo(mes: string): Promise<SaldoRespuesta> {
  return pedir<SaldoRespuesta>(`/saldo?mes=${mes}`);
}

/** El saldo de toda la historia: deudas - pagos, sin recorte por mes. */
export function traerSaldoTotal(): Promise<SaldoTotalRespuesta> {
  return pedir<SaldoTotalRespuesta>('/saldo/total');
}

/** El historial de pagos, el mas nuevo primero. */
export function traerLiquidaciones(): Promise<LiquidacionRespuesta[]> {
  return pedir<LiquidacionRespuesta[]>('/saldo/liquidaciones');
}

/**
 * "Ya le pague esto" (o, con `meLoPagaron`, "ya me pago esto"). Devuelve el
 * saldo total ya actualizado, para no tener que pedirlo aparte.
 */
export function registrarLiquidacion(monto: number, meLoPagaron: boolean): Promise<SaldoTotalRespuesta> {
  return pedir<SaldoTotalRespuesta>('/saldo/liquidaciones', {
    metodo: 'POST',
    cuerpo: { monto, meLoPagaron },
  });
}
