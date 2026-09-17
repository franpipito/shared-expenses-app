import type { ErrorRespuesta } from './tipos';

/**
 * La URL del backend deployado en Railway.
 *
 * Esta hardcodeada a proposito y no en una variable de entorno: es un solo
 * valor, y el sistema de env vars de Expo (EXPO_PUBLIC_, cuando se inlinea, que
 * pasa en un build de EAS) es una complejidad que conviene incorporar recien
 * cuando haya un segundo entorno al que apuntar.
 *
 * NUNCA localhost. El bundle de JavaScript lo sirve la compu, pero el codigo
 * corre en el telefono: `localhost` ahi es el telefono mismo, no la maquina de
 * desarrollo. Es el primer error clasico de una app con Expo.
 */
export const URL_API = 'https://minutria-api.onrender.com';

/**
 * Un error que vino de la API con un cuerpo que se pudo leer.
 *
 * Se hereda de Error para que funcione con `throw` y con los stack traces, y se
 * agrega `estado` porque el codigo HTTP es lo que decide que hacer: un 401
 * manda al login, un 400 se muestra al lado del campo, un 500 es "algo se rompio
 * del otro lado".
 */
export class ErrorDeApi extends Error {
  constructor(
    readonly estado: number,
    mensaje: string,
    readonly errores?: Record<string, string>,
  ) {
    super(mensaje);
    this.name = 'ErrorDeApi';
  }
}

/**
 * El token que se adjunta a cada request.
 *
 * Vive en una variable de modulo y no se pasa por parametro en cada llamada.
 * El costo de esta decision es que hay estado global escondido, que es
 * exactamente lo que suele hacer dificil de testear un cliente HTTP. La ventaja
 * es que ninguna pantalla tiene que acordarse de pasar el token, que es la
 * clase de olvido que produce un 401 misterioso.
 *
 * Quien lo mantiene al dia es `SesionProvider`, y nadie mas deberia llamar a
 * esto.
 */
let tokenActual: string | null = null;

export function fijarToken(token: string | null): void {
  tokenActual = token;
}

/**
/**
 * Cuanto se espera antes de dar una request por perdida.
 *
 * ES GENEROSO A PROPOSITO, y el motivo es el hosting: el backend corre en el
 * tier gratis de Render, que duerme el servicio tras ~15 minutos sin trafico, y
 * despertar Spring Boot toma 40-60 segundos. Un timeout de 10s -- que es lo
 * habitual -- cortaria requests que en realidad iban a andar.
 *
 * Que esto sea tolerable es gracias a la cola: **el alta de gasto ya no espera a
 * la red**, asi que nadie mira un spinner de 60 segundos parado en un mostrador.
 * Los 75s aplican a las lecturas, donde esperar es la unica opcion porque el
 * dato no esta.
 *
 * Sin timeout, una conexion que se cuelga sin cerrarse -- tipico de una red de
 * hotel -- deja la promesa colgada para siempre y la pantalla cargando sin fin.
 */
const TIMEOUT_MS = 75_000;

/**
 * Que hacer cuando el backend rechaza el token que estabamos usando.
 *
 * ESTO EXISTE POR UN BUG REAL. Un JWT de esta app dura 30 dias y se guarda en
 * el Keychain, asi que al abrir la app hay token guardado mucho despues de que
 * dejo de servir -- porque expiro, porque alguien uso /auth/cerrar-sesiones, o
 * porque cambio el secreto del servidor.
 *
 * Antes, la app confiaba en que un token guardado era un token valido: te daba
 * por logueado, entraba al resumen y cada pantalla se comia un 401 que se
 * mostraba como "Falta el token, o no es valido". Sin nombre en el saludo, sin
 * datos, y sin forma de salir. Un estado zombi del que no se sale.
 *
 * La alternativa era validar el token al arrancar con un `GET /auth/yo`, que no
 * existe. Reaccionar al 401 no necesita endpoint nuevo y ademas cubre el caso
 * de que el token se invalide con la app abierta, que un chequeo al arranque no
 * cubriria.
 */
let alPerderLaSesion: (() => void) | null = null;

export function cuandoSePierdaLaSesion(callback: (() => void) | null): void {
  alPerderLaSesion = callback;
}

type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  cuerpo?: unknown;
  /** Para el login y el registro, que todavia no tienen token. */
  sinToken?: boolean;
  /** Para el envio de la cola, que no puede quedarse esperando el arranque en frio. */
  timeoutMs?: number;
};

/**
 * Toda llamada a la API pasa por aca.
 *
 * Tener un solo lugar es lo que permite que el Bearer, el Content-Type y el
 * manejo de errores se escriban una vez. Es lo mismo que daria un interceptor de
 * axios, sin la dependencia.
 */
export async function pedir<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, sinToken = false, timeoutMs = TIMEOUT_MS } = opciones;

  const cabeceras: Record<string, string> = { Accept: 'application/json' };
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  if (!sinToken && tokenActual) cabeceras.Authorization = `Bearer ${tokenActual}`;

  // AbortController es la unica forma de cortar un fetch: no acepta una opcion
  // de timeout. El setTimeout dispara el abort, y el finally lo limpia para no
  // dejar timers colgando por cada request.
  const control = new AbortController();
  const reloj = setTimeout(() => control.abort(), timeoutMs);

  let respuesta: Response;
  try {
    respuesta = await fetch(`${URL_API}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: control.signal,
    });
  } catch {
    // fetch solo rechaza cuando la request no llego a destino: sin internet, DNS
    // caido, el servidor dormido, o el abort de arriba. Un 500 NO cae aca, cae
    // abajo.
    //
    // El estado 0 es la senial que usa el sincronizador para distinguir "no
    // llegue" de "el servidor me dijo que no": lo primero se reintenta, lo
    // segundo no.
    throw new ErrorDeApi(0, 'No se pudo conectar. Fijate si tenes internet.');
  } finally {
    clearTimeout(reloj);
  }

  // 204 No Content: el DELETE de gastos no devuelve cuerpo, y hacer .json() de
  // una respuesta vacia tira un error de parseo confuso.
  if (respuesta.status === 204) return undefined as T;

  const texto = await respuesta.text();
  let datos: unknown = undefined;
  if (texto) {
    try {
      datos = JSON.parse(texto);
    } catch {
      datos = undefined;
    }
  }

  if (!respuesta.ok) {
    // Un 401 en una request CON token significa que el token dejo de servir, y
    // la unica salida sana es volver al login.
    //
    // El `!sinToken` importa: el 401 del login es "esa contrasena esta mal", y
    // ahi no hay ninguna sesion que cerrar. Sin esa condicion, escribir mal la
    // contrasena dispararia un cierre de sesion que no existe.
    if (respuesta.status === 401 && !sinToken) {
      alPerderLaSesion?.();
    }

    const error = datos as ErrorRespuesta | undefined;
    throw new ErrorDeApi(
      respuesta.status,
      error?.mensaje ?? `El servidor respondio ${respuesta.status}.`,
      error?.errores,
    );
  }

  return datos as T;
}
