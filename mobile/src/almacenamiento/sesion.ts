import * as SecureStore from 'expo-secure-store';

/**
 * Donde vive el token JWT entre una apertura de la app y la siguiente.
 *
 * Va en expo-secure-store y NO en AsyncStorage. La diferencia es real, no una
 * prolijidad: AsyncStorage guarda texto plano en el sandbox de la app, asi que
 * cualquiera con acceso al backup del telefono, o con el telefono desbloqueado
 * y un cable, lee el token. SecureStore usa el Keychain en iOS y el Keystore en
 * Android, que estan respaldados por hardware.
 *
 * Importa mas de lo habitual por una decision que ya se tomo en el backend: los
 * tokens de esta app duran 30 dias y NO se pueden revocar de a uno. Si se filtra
 * uno, la unica salida es POST /auth/cerrar-sesiones, que invalida todos los de
 * esa persona.
 *
 * Limite conocido: SecureStore no existe en web. Cuando llegue `web/`, esa
 * plataforma va a necesitar otra estrategia (idealmente una cookie httpOnly,
 * que el JavaScript no puede leer).
 */
const CLAVE_TOKEN = 'minutria.token';

export async function guardarToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(CLAVE_TOKEN, token);
}

export async function leerToken(): Promise<string | null> {
  return SecureStore.getItemAsync(CLAVE_TOKEN);
}

export async function borrarToken(): Promise<void> {
  await SecureStore.deleteItemAsync(CLAVE_TOKEN);
}

/**
 * El usuario se guarda junto al token, y no se vuelve a pedir a la API.
 *
 * El motivo es que no existe un `GET /auth/yo`: al reabrir la app tendriamos el
 * token pero no sabriamos como se llama quien entro. Las alternativas eran
 * agregar ese endpoint al backend, o guardar el dato. Se guarda porque el nombre
 * no puede quedar viejo: no hay ningun endpoint que permita cambiarlo.
 *
 * Si algun dia se puede editar el perfil, esto pasa a ser un cache que hay que
 * invalidar, y ahi conviene el endpoint.
 */
const CLAVE_USUARIO = 'minutria.usuario';

export type UsuarioGuardado = { id: string; nombre: string };

export async function guardarUsuario(usuario: UsuarioGuardado): Promise<void> {
  await SecureStore.setItemAsync(CLAVE_USUARIO, JSON.stringify(usuario));
}

export async function leerUsuario(): Promise<UsuarioGuardado | null> {
  const crudo = await SecureStore.getItemAsync(CLAVE_USUARIO);
  if (!crudo) return null;
  try {
    return JSON.parse(crudo) as UsuarioGuardado;
  } catch {
    // Si quedo basura guardada, tratarla como "no hay sesion" en vez de romper
    // el arranque de la app.
    return null;
  }
}

export async function borrarUsuario(): Promise<void> {
  await SecureStore.deleteItemAsync(CLAVE_USUARIO);
}
