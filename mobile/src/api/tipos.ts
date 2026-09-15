/**
 * El contrato de la API, en TypeScript.
 *
 * Cada tipo de aca es la contracara de un `record` de
 * `backend/src/main/java/com/gastoscompartidos/dto/`. Se escriben a mano y se
 * mantienen sincronizados a mano: no hay generacion automatica desde OpenAPI
 * porque son diez tipos, y una herramienta de generacion es otra pieza que
 * mantener.
 *
 * Una advertencia que vale la pena tener presente: **TypeScript no valida nada
 * en runtime.** Si el backend cambia un campo, estos tipos siguen compilando y
 * la app rompe recien al usar el dato. Los tipos son documentacion que el
 * editor entiende, no una garantia.
 *
 * OJO CON LOS IDS: son `string`, no `number`. En Mongo la clave primaria es un
 * ObjectId de 12 bytes que viaja como su representacion hexadecimal
 * ("66f1a2b3c4d5e6f7a8b9c0d1"). No se pueden ordenar ni comparar como numeros, y
 * no hay autoincremento: dos documentos creados en el mismo segundo no tienen
 * ningun orden garantizado entre si.
 *
 * OJO CON LOS MONTOS: en Java son BigDecimal, elegido justamente para que el
 * punto flotante no arruine los centavos. Al pasar a JSON llegan como `number`,
 * que en JavaScript es punto flotante de 64 bits. Por eso en la app **no se
 * hace aritmetica con plata**: solo se muestra lo que el backend ya calculo. La
 * unica cuenta que existe (monto x porcentaje) tambien la hace el backend.
 */

export type TipoGasto = 'PERSONAL' | 'COMPARTIDO';

export type AnimoNutria = 'CONTENTA' | 'TRANQUILA' | 'PREOCUPADA';

export type EstadoPozo = 'ABIERTO' | 'CERRADO';

export type UsuarioRespuesta = {
  id: string;
  nombre: string;
};

export type TokenRespuesta = {
  token: string;
  /** ISO-8601, viene de un Instant de Java. */
  expiraEn: string;
  usuario: UsuarioRespuesta;
};

export type CategoriaRespuesta = {
  id: string;
  nombre: string;
  /** Nombre de un icono de Lucide ("coffee", "car", "utensils"), NO un emoji. */
  icono: string;
};

export type GastoRespuesta = {
  id: string;
  monto: number;
  montoPagador: number;
  /** monto - montoPagador. Lo calcula el backend para que nadie lo reste dos veces. */
  deudaGenerada: number;
  /** yyyy-MM-dd */
  fecha: string;
  descripcion: string;
  tipo: TipoGasto;
  esHormiga: boolean;
  categoria: CategoriaRespuesta;
  pagadoPor: UsuarioRespuesta;
  /** Bloqueo optimista: se recibe al leer y se devuelve al editar. */
  version: number;
  /** El pozo del que salio, o null si es un gasto de la vida normal. */
  pozoId: string | null;
};

export type GuardarGastoRequest = {
  monto: number;
  categoriaId: string;
  /** yyyy-MM-dd. No puede ser futura. */
  fecha: string;
  descripcion: string;
  tipo: TipoGasto;
  /** Solo aplica a COMPARTIDO. Si falta, el backend asume 50. */
  porcentajePagador?: number;
  pagadoPorId?: string;
  /** Si falta, el backend lo toma como false. */
  esHormiga?: boolean;
  version?: number;
  /**
   * La vaquita. Si viaja, el backend **exige** que `tipo` sea COMPARTIDO y
   * rechaza el gasto si no lo es -- no lo corrige solo, porque promover un
   * PERSONAL a COMPARTIDO en silencio publicaria un gasto que su duenio marco
   * como privado. Ademas ignora `porcentajePagador`: un gasto del pozo es mitad
   * y mitad por construccion.
   */
  pozoId?: string;
  /**
   * Clave de idempotencia que genera el telefono. La manda el sincronizador de
   * la cola, no las pantallas.
   *
   * Es lo que hace que reintentar un gasto sea seguro: si el POST llego pero la
   * respuesta se perdio, el reintento choca contra un indice unico en el backend
   * y devuelve el gasto que ya existia, en vez de crear un segundo.
   */
  clienteId?: string;
};

export type TotalPorCategoria = {
  categoriaId: string;
  nombre: string;
  /** Nombre de un icono de Lucide, NO un emoji. */
  icono: string;
  total: number;
  totalHormiga: number;
};

export type ResumenRespuesta = {
  /** yyyy-MM */
  mes: string;
  desde: string;
  /** Exclusivo: el periodo es [desde, hasta). */
  hasta: string;
  total: number;
  /** El numero protagonista de la app. */
  totalHormiga: number;
  totalHormigaMesAnterior: number;
  porCategoria: TotalPorCategoria[];
  animo: AnimoNutria;
};

export type SaldoRespuesta = {
  mes: string;
  monto: number;
  deudorId: string | null;
  deudorNombre: string | null;
  acreedorId: string | null;
  acreedorNombre: string | null;
  /** Positivo si me deben; negativo si debo. */
  aFavorMio: number;
};

/** Lo que devuelve `ManejadorDeErrores` en cualquier respuesta que no sea 2xx. */
export type ErrorRespuesta = {
  mensaje: string;
  /** Presente solo en fallos de validacion: campo -> mensaje. */
  errores?: Record<string, string>;
};

/**
 * La vaquita del viaje.
 *
 * `aportado`, `gastado` y `restante` los calcula el backend en cada lectura: la
 * app NO hace la resta. Es la misma regla que rige toda la plata de este
 * cliente -- los montos llegan como `number`, que en JavaScript es punto
 * flotante, y restar ahi reintroduce el problema del centavo que BigDecimal y
 * Decimal128 vienen evitando de punta a punta.
 *
 * @property restante  puede ser NEGATIVO, y no es un error: si se les acabo la
 *                     vaquita en medio de una cena, el gasto se cargo igual y el
 *                     pozo quedo en rojo. La pantalla tiene que saber dibujarlo.
 * @property vigente   si hoy cae dentro de las fechas del viaje. Lo decide el
 *                     backend y no el telefono, porque "hoy" depende de la zona
 *                     horaria: es el mismo motivo que el bean `Clock`.
 */
export type PozoRespuesta = {
  id: string;
  nombre: string;
  objetivo: number | null;
  estado: EstadoPozo;
  /** yyyy-MM-dd */
  desde: string | null;
  hasta: string | null;
  vigente: boolean;
  aportado: number;
  gastado: number;
  restante: number;
  porPersona: TotalPorPersona[];
  aportes: AporteRespuesta[];
  version: number;
};

export type TotalPorPersona = {
  usuarioId: string;
  nombre: string;
  total: number;
};

export type AporteRespuesta = {
  usuario: UsuarioRespuesta;
  monto: number;
  /** yyyy-MM-dd */
  fecha: string;
};

export type CrearPozoRequest = {
  nombre: string;
  objetivo?: number;
  desde?: string;
  hasta?: string;
};

/**
 * Poner plata. Fijate que NO lleva quien aporta: el backend lo saca del token.
 * Nadie puede anotar un aporte a nombre de la otra persona, que es lo correcto
 * -- un aporte es la afirmacion "puse esta plata".
 */
export type AporteRequest = {
  monto: number;
};

export type GrupoRespuesta = {
  id: string;
  nombre: string;
  /** Son dos: el modelo de reparto asume dos integrantes. */
  integrantes: UsuarioRespuesta[];
};
