import { pedir } from '../../api/cliente';
import type { ResumenRespuesta } from '../../api/tipos';

export function traerResumen(mes: string): Promise<ResumenRespuesta> {
  return pedir<ResumenRespuesta>(`/gastos/resumen?mes=${mes}`);
}
