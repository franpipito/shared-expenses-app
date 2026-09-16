import { borrarCatalogo } from '../../almacenamiento/catalogo';
import { borrarCola } from '../../almacenamiento/cola';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { fijarToken, pedir } from '../../api/cliente';
import type { TokenRespuesta } from '../../api/tipos';
import {
  borrarToken,
  borrarUsuario,
  guardarToken,
  guardarUsuario,
  leerToken,
  leerUsuario,
  type UsuarioGuardado,
} from '../../almacenamiento/sesion';

/**
 * El unico estado global de la app.
 *
 * `docs/diseno.md` dice que no va Redux ni Zustand, y este archivo es el motivo
 * por el que se puede sostener esa decision: el estado que de verdad tiene que
 * ser global es quien esta logueado, y nada mas. Los gastos y el resumen los
 * pide cada pantalla cuando los necesita.
 *
 * Un Context de React tiene una limitacion conocida: cuando el valor cambia, se
 * vuelven a renderizar todos los componentes que lo consumen. Es lo que hace que
 * no sirva para estado que cambia seguido. Este cambia dos veces por mes.
 */
type Sesion = {
  usuario: UsuarioGuardado | null;
  /** true mientras se lee el token del Keychain, al arrancar la app. */
  cargando: boolean;
  entrar: (email: string, password: string) => Promise<void>;
  registrarse: (datos: DatosDeRegistro) => Promise<void>;
  salir: () => Promise<void>;
};

const ContextoDeSesion = createContext<Sesion | null>(null);

/**
 * Lo que hace falta para crear una cuenta.
 *
 * El `codigoInvitacion` no es ceremonia: el backend va a estar publico, y sin el
 * cualquiera que encuentre la URL se crearia una cuenta. Franco se lo pasa a
 * Viole por fuera de la app.
 */
export type DatosDeRegistro = {
  nombre: string;
  email: string;
  password: string;
  codigoInvitacion: string;
};

export function SesionProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioGuardado | null>(null);
  const [cargando, setCargando] = useState(true);

  // Al arrancar: leer del Keychain lo que haya quedado de la ultima vez.
  // Es asincrono, y por eso hace falta `cargando`: sin el, la app mostraria el
  // login por un instante antes de darse cuenta de que ya habia sesion.
  useEffect(() => {
    (async () => {
      try {
        const [token, guardado] = await Promise.all([leerToken(), leerUsuario()]);
        if (token && guardado) {
          fijarToken(token);
          setUsuario(guardado);
        }
      } finally {
        setCargando(false);
      }
    })();
  }, []);

  const entrar = useCallback(async (email: string, password: string) => {
    const respuesta = await pedir<TokenRespuesta>('/auth/login', {
      metodo: 'POST',
      cuerpo: { email, password },
      sinToken: true,
    });
    // Primero el almacenamiento, despues el estado: si guardar falla, no
    // queremos una sesion que existe en memoria y desaparece al cerrar la app.
    await Promise.all([
      guardarToken(respuesta.token),
      guardarUsuario(respuesta.usuario),
    ]);
    fijarToken(respuesta.token);
    setUsuario(respuesta.usuario);
  }, []);

  /**
   * Crear la cuenta y quedar adentro, sin pasar por el login.
   *
   * `POST /auth/registro` devuelve el mismo `TokenRespuesta` que el login, asi
   * que despues de registrarse ya hay sesion: obligar a volver al login y
   * retipear la contraseña que acabas de elegir es fricción sin ninguna
   * contrapartida.
   *
   * El primero que se registra crea el grupo; el segundo se suma; un tercero se
   * rechaza, porque el modelo de reparto asume dos integrantes.
   */
  const registrarse = useCallback(async (datos: DatosDeRegistro) => {
    const respuesta = await pedir<TokenRespuesta>('/auth/registro', {
      metodo: 'POST',
      cuerpo: datos,
      sinToken: true,
    });
    await Promise.all([
      guardarToken(respuesta.token),
      guardarUsuario(respuesta.usuario),
    ]);
    fijarToken(respuesta.token);
    setUsuario(respuesta.usuario);
  }, []);

  const salir = useCallback(async () => {
    // Solo local. NO se llama a /auth/cerrar-sesiones, que incrementa
    // token_version e invalida el token de TODOS los dispositivos: eso es el
    // boton de "perdi el celular", no el de "cerrar sesion".
    // Tambien se limpia el catalogo y la cola: son del grupo de quien estaba
    // adentro. Si Franco se desloguea con gastos pendientes y despues entra
    // Viole en ese telefono, el sincronizador los mandaria con el token de ella
    // y quedarian a su nombre.
    await Promise.all([borrarToken(), borrarUsuario(), borrarCatalogo(), borrarCola()]);
    fijarToken(null);
    setUsuario(null);
  }, []);

  const valor = useMemo<Sesion>(
    () => ({ usuario, cargando, entrar, registrarse, salir }),
    [usuario, cargando, entrar, registrarse, salir],
  );

  return <ContextoDeSesion.Provider value={valor}>{children}</ContextoDeSesion.Provider>;
}

export function useSesion(): Sesion {
  const sesion = useContext(ContextoDeSesion);
  if (!sesion) {
    // Pasa si alguien usa el hook fuera del provider. Es un error de programacion
    // y conviene que explote fuerte y temprano, no que devuelva null y rompa
    // tres componentes mas abajo.
    throw new Error('useSesion tiene que usarse adentro de <SesionProvider>');
  }
  return sesion;
}
