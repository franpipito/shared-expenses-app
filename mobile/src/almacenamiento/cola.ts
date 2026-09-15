import { File, Paths } from 'expo-file-system';

import type { GuardarGastoRequest } from '../api/tipos';

/**
 * La cola de gastos que todavia no entraron al servidor.
 *
 * POR QUE EXISTE: la usuaria abandono un intento anterior porque anotar era
 * incomodo, y dijo que prefiere olvidarse un gasto antes que anotar lento. Sin
 * esto, cargar un gasto con una barra de senial termina en un spinner y un
 * error, y el gasto se pierde: hay que volver a tipearlo. En Bariloche, parada
 * en un mostrador, eso es el fin de la app.
 *
 * Con la cola, guardar es **instantaneo y no depende de la red**: el gasto se
 * escribe en el telefono y se manda despues.
 *
 * POR QUE UN ARCHIVO Y NO MEMORIA. iOS mata las apps en segundo plano sin
 * avisar, sobre todo con la camara y los mapas abiertos, que es lo que pasa en
 * un viaje. Una cola en memoria pierde todo en ese momento, que es justo cuando
 * hace falta.
 *
 * POR QUE NO expo-secure-store, que ya esta en el proyecto: SecureStore es para
 * secretos chicos y en Android tiene un tope de ~2 KB por valor. Una cola de
 * gastos lo pasa. Los gastos ademas no son credenciales -- el token sigue en el
 * Keychain, que es donde va.
 *
 * `Paths.document` y no `Paths.cache`: el sistema operativo puede vaciar el
 * cache cuando le falta espacio, y perder gastos ahi seria exactamente el bug
 * que esto viene a arreglar.
 */
const ARCHIVO = 'cola-de-gastos.json';

/**
 * Un gasto esperando a que haya red.
 *
 * @property clienteId  la clave de idempotencia. Viaja al backend y es lo que
 *                      hace que reintentar sea seguro: si el POST llego pero la
 *                      respuesta se perdio, el reintento choca contra un indice
 *                      unico y el servidor devuelve el gasto que ya existia en
 *                      vez de crear un segundo. **Sin esto la cola duplicaria
 *                      gastos**, que es peor que perderlos: un total del mes
 *                      equivocado y en silencio.
 * @property error      si el servidor lo RECHAZO (un 4xx). Un gasto rechazado no
 *                      se reintenta mas -- volver a mandarlo daria el mismo 400
 *                      para siempre y taparia a los que si pueden entrar -- pero
 *                      tampoco se borra: se muestra para que la persona decida.
 */
export type GastoPendiente = {
  clienteId: string;
  gasto: GuardarGastoRequest;
  encoladoEn: number;
  error?: string;
};

function archivo(): File {
  return new File(Paths.document, ARCHIVO);
}

/**
 * La clave de idempotencia.
 *
 * NO es un UUID de verdad: `crypto.randomUUID()` no esta garantizado en Hermes y
 * traerlo costaria otra dependencia nativa. Timestamp en base 36 mas ruido
 * alcanza de sobra para lo que tiene que garantizar, que es no repetirse
 * **dentro de un grupo de dos personas**. No es un identificador publico ni una
 * defensa contra nadie: es para distinguir dos gastos entre si.
 */
function nuevaClave(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Leer nunca tira.
 *
 * Si el archivo no existe todavia, o quedo a medio escribir porque el sistema
 * mato la app en el peor momento, se devuelve una cola vacia. Romper el arranque
 * de la app por una cola corrupta seria cambiar un problema chico por uno grande.
 */
export async function leerCola(): Promise<GastoPendiente[]> {
  try {
    const f = archivo();
    if (!f.exists) return [];
    const crudo = await f.text();
    if (!crudo) return [];
    const datos: unknown = JSON.parse(crudo);
    return Array.isArray(datos) ? (datos as GastoPendiente[]) : [];
  } catch {
    return [];
  }
}

async function escribirCola(cola: GastoPendiente[]): Promise<void> {
  const f = archivo();
  // create() tira si el archivo ya existe, asi que se pregunta antes.
  if (!f.exists) f.create();
  f.write(JSON.stringify(cola));
}

/**
 * Mete un gasto en la cola y devuelve la entrada, con su clave ya generada.
 *
 * Se encola SIEMPRE, incluso con red perfecta. Es la parte contraintuitiva y la
 * que hace que el disenio funcione: si primero intentaramos mandar y solo
 * encolaramos al fallar, un gasto se perderia igual cuando el sistema mata la
 * app en el medio de la request. Escribiendo primero, el gasto existe desde el
 * instante en que se toca Guardar.
 */
export async function encolar(gasto: GuardarGastoRequest): Promise<GastoPendiente> {
  const pendiente: GastoPendiente = {
    clienteId: nuevaClave(),
    gasto,
    encoladoEn: Date.now(),
  };
  await escribirCola([...(await leerCola()), pendiente]);
  return pendiente;
}

export async function quitarDeLaCola(clienteId: string): Promise<void> {
  await escribirCola((await leerCola()).filter((p) => p.clienteId !== clienteId));
}

/** Marca un gasto como rechazado por el servidor. No se reintenta, no se borra. */
export async function marcarRechazado(clienteId: string, error: string): Promise<void> {
  await escribirCola(
    (await leerCola()).map((p) => (p.clienteId === clienteId ? { ...p, error } : p)),
  );
}

/** Descarta un gasto rechazado. Lo decide la persona, nunca la app sola. */
export async function descartar(clienteId: string): Promise<void> {
  await quitarDeLaCola(clienteId);
}
