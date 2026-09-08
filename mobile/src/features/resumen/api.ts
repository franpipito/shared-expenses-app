import { pedir } from '../../api/cliente';
import type { ResumenRespuesta } from '../../api/tipos';

/**
 * El mes en el formato que espera el backend (`YearMonth` de Java): `yyyy-MM`.
 *
 * Se arma con la fecha local del telefono. Vale saber que **el backend NO confia
 * en esto**: tiene su propio bean `Clock` con zona `America/Argentina/Buenos_Aires`
 * y calcula el periodo `[desde, hasta)` el mismo. Si el telefono estuviera en
 * otra zona, el mes pedido podria no ser el que el backend considera "actual" —
 * y la respuesta trae `desde` y `hasta` justamente para que se pueda ver.
 */
export function mesActual(): string {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`;
}

export function traerResumen(mes: string): Promise<ResumenRespuesta> {
  return pedir<ResumenRespuesta>(`/gastos/resumen?mes=${mes}`);
}
