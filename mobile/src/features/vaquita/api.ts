import { pozoConRespaldo } from '../../almacenamiento/catalogo';
import { pedir } from '../../api/cliente';
import type {
  AporteRequest,
  CrearPozoRequest,
  GastoRespuesta,
  PozoRespuesta,
} from '../../api/tipos';

/**
 * La vaquita del viaje: un pozo al que los dos aportan y del que salen los
 * gastos.
 *
 * El invariante, que vive en el backend y la app solo muestra:
 *
 *     restante = aportado - gastado
 *
 * Y el que importa para entender por que esto no es solo una pantalla mas:
 * **sacar plata del pozo NO genera deuda entre ellos**, porque la plata ya se
 * repartio al aportar. Un aporte es una liquidacion anticipada. Ver
 * `docs/vaquita.md`.
 */

/**
 * El pozo abierto del grupo, o `null` si no hay ninguno.
 *
 * OJO CON EL null: el backend devuelve **204 No Content**, no un 404, y
 * `cliente.ts` traduce el 204 a `undefined`. No tener vaquita es un estado
 * normal de la app, no un error, asi que esto NO tira -- devuelve null y la
 * pantalla dibuja el estado vacio.
 */
export function traerPozoActivo() {
  return pozoConRespaldo(async () => (await pedir<PozoRespuesta | undefined>('/pozos/activo')) ?? null);
}

/**
 * La pantalla de la vaquita quiere el pozo pelado y que un fallo sea un fallo,
 * no un caché: ahi mostrar numeros viejos de plata seria peor que un error.
 * El respaldo es solo para el formulario de alta, que necesita saber que el
 * chip Vaquita existe.
 */
export async function traerPozoActivoFresco(): Promise<PozoRespuesta | null> {
  return (await pedir<PozoRespuesta | undefined>('/pozos/activo')) ?? null;
}

export function crearPozo(pozo: CrearPozoRequest): Promise<PozoRespuesta> {
  return pedir<PozoRespuesta>('/pozos', { metodo: 'POST', cuerpo: pozo });
}

export function aportar(pozoId: string, aporte: AporteRequest): Promise<PozoRespuesta> {
  return pedir<PozoRespuesta>(`/pozos/${pozoId}/aportes`, { metodo: 'POST', cuerpo: aporte });
}

export function cerrarPozo(pozoId: string): Promise<PozoRespuesta> {
  return pedir<PozoRespuesta>(`/pozos/${pozoId}/cerrar`, { metodo: 'POST' });
}

/**
 * Los gastos del viaje.
 *
 * Es el unico listado de la app que **no se corta por mes**, y es a proposito:
 * el pozo ES el recorte. El viaje a Bariloche cruza de septiembre a octubre, asi
 * que cualquier vista mensual lo partiria al medio.
 */
export function traerGastosDelPozo(pozoId: string): Promise<GastoRespuesta[]> {
  return pedir<GastoRespuesta[]>(`/pozos/${pozoId}/gastos`);
}
