import { Redirect } from 'expo-router';

import { useSesion } from '../src/features/auth/sesion';
import { Cargando } from './_layout';

/**
 * La ruta `/`, que no dibuja nada: solo decide adonde ir.
 *
 * Es el patron habitual con expo-router para el "guardia" de autenticacion.
 * Hace falta esperar a `cargando` porque leer el token del Keychain es
 * asincrono: sin esa espera, la app mandaria al login por un instante a alguien
 * que ya tenia sesion, y eso se ve.
 */
export default function Entrada() {
  const { usuario, cargando } = useSesion();

  if (cargando) return <Cargando />;
  return <Redirect href={usuario ? '/resumen' : '/login'} />;
}
