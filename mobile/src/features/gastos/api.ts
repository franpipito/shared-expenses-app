import { categoriasConRespaldo, grupoConRespaldo } from '../../almacenamiento/catalogo';
import { encolar } from '../../almacenamiento/cola';
import { pedir } from '../../api/cliente';
import type {
  CategoriaRespuesta,
  GastoRespuesta,
  GrupoRespuesta,
  GuardarGastoRequest,
} from '../../api/tipos';
import { sincronizar } from './sincronizador';

/**
 * Las categorias, con respaldo en disco.
 *
 * SIN ESTO LA COLA OFFLINE NO SERVIA PARA NADA, y es el agujero mas grande que
 * tenia. La sesion 6.8 logro que el POST no espere a la red, pero nadie miro que
 * necesita la PANTALLA antes de poder encolar algo: sin categorias no hay chip
 * que tocar, `categoriaId` se queda en null, y el boton Guardar nunca se
 * habilita. O sea que sin senial no habia nada que encolar.
 *
 * Devuelve `esDeCache` para que quien llama sepa si esta mirando datos frescos.
 */
export function traerCategorias() {
  return categoriasConRespaldo(() => pedir<CategoriaRespuesta[]>('/categorias'));
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

/**
 * Cargar un gasto. **No espera a la red, y ese es todo el punto.**
 *
 * El gasto se escribe primero en la cola local del telefono y despues se manda.
 * Guardar pasa a ser una operacion de disco: instantanea, y que funciona igual
 * con una barra de senial o sin ninguna.
 *
 * Es lo que hace falta para sostener el requisito duro del producto. Antes,
 * cargar un cafe parada en un mostrador con mala senial terminaba en un spinner,
 * un error, y el gasto perdido: habia que volver a tipearlo. Con esto, el peor
 * caso es que el gasto tarde un rato en aparecer en la lista.
 *
 * SE ENCOLA SIEMPRE, incluso con red perfecta, y es deliberado: si primero
 * intentaramos mandar y solo encolaramos al fallar, el gasto se perderia igual
 * cuando iOS mata la app en medio de la request -- que es lo que hace en cuanto
 * abris la camara. Escribiendo antes, el gasto existe desde que se toca Guardar.
 *
 * El envio arranca en el acto pero **no se espera**: por eso no hay await. Quien
 * quiera saber si entro, mira la cola.
 */
export async function crearGasto(gasto: GuardarGastoRequest): Promise<void> {
  await encolar(gasto);
  void sincronizar();
}

export function traerGasto(id: string): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>(`/gastos/${id}`);
}

/**
 * Editar y borrar NO pasan por la cola, y es una decision, no un olvido.
 *
 * La cola existe para el camino rapido: cargar un gasto parado en un mostrador,
 * donde esperar a la red es lo que hace que la gente abandone. Editar es lo
 * contrario -- es una correccion deliberada, que se hace sentado y mirando la
 * lista. Ahi esperar dos segundos no molesta a nadie.
 *
 * Y encolarlas costaria mucho mas de lo que parece: una cola de MODIFICACIONES
 * necesita orden garantizado (editar y despues borrar no es lo mismo que al
 * reves), resolver que pasa si editas algo que todavia no se mando, y decidir
 * quien gana cuando el servidor tiene una version mas nueva. Es un log de
 * operaciones, no una lista.
 *
 * El limite practico: **solo se pueden editar y borrar gastos que ya entraron al
 * servidor.** Los que estan esperando en la cola no aparecen en la lista todavia
 * -- se ven en el aviso de arriba -- asi que la situacion no se puede dar.
 */
export function editarGasto(id: string, gasto: GuardarGastoRequest): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>(`/gastos/${id}`, { metodo: 'PUT', cuerpo: gasto });
}

export function borrarGasto(id: string): Promise<void> {
  return pedir<void>(`/gastos/${id}`, { metodo: 'DELETE' });
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
export function traerGrupo() {
  return grupoConRespaldo(() => pedir<GrupoRespuesta>('/grupo'));
}
