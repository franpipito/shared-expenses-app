import { pedir } from '../../api/cliente';
import type { SaldoRespuesta } from '../../api/tipos';

/**
 * El saldo del mes: quien le debe a quien.
 *
 * OJO CON EL ALCANCE, que es una decision de producto y no un limite tecnico:
 * **este saldo es del mes pedido, no historico.** El alcance original decia
 * "saldo actual" sobre toda la historia, pero no existe ninguna forma de saldar
 * la cuenta -- no hay entidad de liquidacion -- asi que un saldo historico solo
 * crece y a los pocos meses es un numero grande que no representa nada real.
 *
 * Acotarlo al mes lo mantiene chico y accionable, al costo de asumir que se
 * arreglan mes a mes. La pantalla lo dice con todas las letras, porque un saldo
 * que se resetea sin avisar es peor que no tenerlo.
 */
export function traerSaldo(mes: string): Promise<SaldoRespuesta> {
  return pedir<SaldoRespuesta>(`/saldo?mes=${mes}`);
}
