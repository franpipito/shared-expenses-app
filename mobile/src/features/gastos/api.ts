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
