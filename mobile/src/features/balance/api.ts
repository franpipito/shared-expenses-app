import { pedir } from '../../api/cliente';
import type { BalancePersonalRespuesta } from '../../api/tipos';

/** "Mi Plata": lo que declaraste tener, menos tus gastos personales. */
export function traerBalance(): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>('/balance-personal');
}

/**
 * Declarar plata nueva (o corregir una anterior, con un monto negativo).
 * Devuelve el balance ya actualizado, para no pedirlo aparte -- mismo patron
 * que `registrarLiquidacion`.
 */
export function agregarIngreso(monto: number): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>('/balance-personal/ingresos', {
    metodo: 'POST',
    cuerpo: { monto },
  });
}
