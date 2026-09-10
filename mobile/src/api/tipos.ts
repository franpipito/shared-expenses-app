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

export type GrupoRespuesta = {
  id: string;
  nombre: string;
  /** Son dos: el modelo de reparto asume dos integrantes. */
  integrantes: UsuarioRespuesta[];
};
