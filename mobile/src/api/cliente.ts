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
export const URL_API = 'https://brave-wisdom-production-0be5.up.railway.app';

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

type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  cuerpo?: unknown;
  /** Para el login y el registro, que todavia no tienen token. */
  sinToken?: boolean;
};

/**
 * Toda llamada a la API pasa por aca.
 *
 * Tener un solo lugar es lo que permite que el Bearer, el Content-Type y el
 * manejo de errores se escriban una vez. Es lo mismo que daria un interceptor de
 * axios, sin la dependencia.
 */
export async function pedir<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, sinToken = false } = opciones;

  const cabeceras: Record<string, string> = { Accept: 'application/json' };
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  if (!sinToken && tokenActual) cabeceras.Authorization = `Bearer ${tokenActual}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(`${URL_API}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
  } catch {
    // fetch solo rechaza cuando la request no llego a destino: sin internet, DNS
    // caido, o el servidor dormido en Railway. Un 500 NO cae aca, cae abajo.
    throw new ErrorDeApi(0, 'No se pudo conectar. Fijate si tenes internet.');
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
    const error = datos as ErrorRespuesta | undefined;
    throw new ErrorDeApi(
      respuesta.status,
      error?.mensaje ?? `El servidor respondio ${respuesta.status}.`,
      error?.errores,
    );
  }

  return datos as T;
}
