import { pedir } from '../../api/cliente';
import type {
  CategoriaRespuesta,
  GastoRespuesta,
  GrupoRespuesta,
  GuardarGastoRequest,
} from '../../api/tipos';

export function traerCategorias(): Promise<CategoriaRespuesta[]> {
  return pedir<CategoriaRespuesta[]>('/categorias');
}

/**
 * Los gastos del mes, ya ordenados por el backend: fecha descendente y, dentro
 * del mismo dia, el ultimo cargado primero. **La app no reordena nada.**
 *
 * El orden lo decide `GastoConsultasImpl` desempatando por `_id`, y puede
 * hacerlo porque los primeros bytes de un ObjectId son el timestamp de creacion.
 * Replicar ese criterio aca seria tener la misma regla escrita en dos lugares.
 *
 * Lo que llega ya viene filtrado por visibilidad: un gasto PERSONAL de la otra
 * persona no esta en la respuesta. Esa regla vive en el WHERE del repositorio,
 * no en un `if` de esta pantalla.
 */
export function traerGastos(mes: string): Promise<GastoRespuesta[]> {
  return pedir<GastoRespuesta[]>(`/gastos?mes=${mes}`);
}

export function crearGasto(gasto: GuardarGastoRequest): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>('/gastos', { metodo: 'POST', cuerpo: gasto });
}

/**
 * La fecha de hoy en `yyyy-MM-dd`, tomada del reloj local del telefono.
 *
 * NO se usa `new Date().toISOString().slice(0,10)`, que es lo que aparece en
 * todos lados: `toISOString` convierte a UTC, asi que un gasto cargado a las
 * 22:00 en Buenos Aires quedaria fechado al dia siguiente. Es el mismo problema
 * de zona horaria que motivo el bean `Clock` del backend, visto del lado del
 * cliente.
 */
export function hoyLocal(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * El grupo con sus integrantes.
 *
 * Vive en la feature de gastos y no en una feature propia porque el unico lugar
 * que lo consume es el alta, para poder ofrecer "lo pago la otra persona": es lo
 * unico que le falta al cliente para armar un COMPARTIDO completo, porque el
 * login solo dice quien sos vos. La regla de `docs/diseno.md` es que lo que usa
 * una sola feature se queda adentro.
 *
 * El endpoint no acepta un id: devuelve siempre el grupo de quien pregunta, que
 * sale del token. Un endpoint sin id no puede filtrar datos de otro grupo.
 */
export function traerGrupo(): Promise<GrupoRespuesta> {
  return pedir<GrupoRespuesta>('/grupo');
}

/**
 * Un gasto por id, con su `version` al dia.
 *
 * La pantalla de edicion lo pide en vez de arrastrar el objeto desde la lista
 * por parametros de navegacion. La diferencia importa: la lista pudo haberse
 * cargado hace rato, y editar contra una copia vieja es exactamente lo que el
 * bloqueo optimista viene a detectar. Trayendolo de nuevo, el 409 queda para los
 * conflictos de verdad y no para uno que nos provocamos solos.
 */
export function traerGasto(id: string): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>(`/gastos/${id}`);
}

/**
 * Guardar los cambios de un gasto.
 *
 * `version` viaja adentro de `gasto` y NO es opcional aca aunque el backend la
 * acepte ausente: si no se manda, gana la ultima escritura en silencio. Al
 * mandarla, si la otra persona edito el mismo gasto mientras tanto, el backend
 * responde 409 y la app puede avisar en vez de pisar el cambio ajeno.
 */
export function actualizarGasto(id: string, gasto: GuardarGastoRequest): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>(`/gastos/${id}`, { metodo: 'PUT', cuerpo: gasto });
}

/** Borrar. El backend responde 204 sin cuerpo. */
export function eliminarGasto(id: string): Promise<void> {
  return pedir<void>(`/gastos/${id}`, { metodo: 'DELETE' });
}
