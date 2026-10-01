import { pedir } from '../../api/cliente';
import type { BalancePersonalRespuesta } from '../../api/tipos';

/** "Mi Plata": lo que declaraste tener, menos tus gastos personales. */
export function traerBalance(): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>('/balance-personal');
}

/**
 * Declarar plata nueva. Devuelve el balance ya actualizado, para no pedirlo
 * aparte -- mismo patrón que `registrarLiquidacion`.
 *
 * Siempre positivo (sección 2.3c): a diferencia de un aporte a la vaquita, un
 * ingreso mal cargado ya no se corrige con un monto negativo -- se edita o
 * se borra de verdad, tocando la fila. Ver `editarIngreso`/`borrarIngreso`.
 */
export function agregarIngreso(monto: number): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>('/balance-personal/ingresos', {
    metodo: 'POST',
    cuerpo: { monto },
  });
}

/** Corrige el monto de un ingreso ya cargado. Devuelve el balance actualizado. */
export function editarIngreso(id: string, monto: number): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>(`/balance-personal/ingresos/${id}`, {
    metodo: 'PUT',
    cuerpo: { monto },
  });
}

/** Saca un ingreso del historial. Devuelve el balance actualizado. */
export function borrarIngreso(id: string): Promise<BalancePersonalRespuesta> {
  return pedir<BalancePersonalRespuesta>(`/balance-personal/ingresos/${id}`, { metodo: 'DELETE' });
}
