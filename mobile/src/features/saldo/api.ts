import { pedir } from '../../api/cliente';
import type { LiquidacionRespuesta, SaldoTotalRespuesta } from '../../api/tipos';

/**
 * El saldo de toda la historia: deudas - pagos, sin recorte por mes. Es el
 * que muestra `app/saldo.tsx` como "quien le debe a quien" (ver el
 * comentario de esa pantalla): `GET /saldo?mes=` sigue existiendo en el
 * backend -- "cuanto generaron los gastos compartidos ESTE mes" sigue siendo
 * una pregunta valida -- pero ya nada en el cliente la usa, asi que no tiene
 * wrapper aca. Si algun dia hace falta de nuevo, es un `pedir` mas.
 */
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
