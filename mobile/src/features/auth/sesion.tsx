import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { router } from 'expo-router';

import { cuandoSePierdaLaSesion, fijarToken, pedir } from '../../api/cliente';
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
  registrar: (
    nombre: string,
    email: string,
    password: string,
    codigoInvitacion: string,
  ) => Promise<void>;
  salir: () => Promise<void>;
};

const ContextoDeSesion = createContext<Sesion | null>(null);

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
   * Crear la cuenta y quedar logueado, en un solo paso.
   *
   * El backend devuelve token y usuario en el registro igual que en el login,
   * asi que no hace falta un "ya te registraste, ahora entra" -- que es friccion
   * pura justo en el momento mas fragil, el de alguien que todavia no vio nada
   * de la app.
   *
   * La duplicacion con `entrar` es de tres lineas y a proposito: son dos flujos
   * distintos que hoy comparten la forma de la respuesta. Extraerlas ataria el
   * registro al login por una casualidad.
   */
  const registrar = useCallback(
    async (nombre: string, email: string, password: string, codigoInvitacion: string) => {
      const respuesta = await pedir<TokenRespuesta>('/auth/registro', {
        metodo: 'POST',
        cuerpo: { nombre, email, password, codigoInvitacion },
        sinToken: true,
      });
      await Promise.all([
        guardarToken(respuesta.token),
        guardarUsuario(respuesta.usuario),
      ]);
      fijarToken(respuesta.token);
      setUsuario(respuesta.usuario);
    },
    [],
  );

  const salir = useCallback(async () => {
    // Solo local. NO se llama a /auth/cerrar-sesiones, que incrementa
    // token_version e invalida el token de TODOS los dispositivos: eso es el
    // boton de "perdi el celular", no el de "cerrar sesion".
    await Promise.all([borrarToken(), borrarUsuario()]);
    fijarToken(null);
    setUsuario(null);

    // Y NAVEGAR, que es lo que faltaba y hacia parecer que el boton no andaba.
    // Poner el usuario en null no mueve a nadie de lugar: el guardia que manda
    // al login vive en `index.tsx`, y si ya estas parado en /resumen esa ruta no
    // se vuelve a evaluar. Quedabas en la misma pantalla, vacia.
    //
    // `replace` y no `push`: con push, el gesto de "atras" del telefono te
    // devolveria a una pantalla de alguien que ya cerro sesion.
    //
    // Se usa el objeto `router` importado y no el hook `useRouter`, porque esto
    // corre en un provider y no adentro de una pantalla.
    router.replace('/login');
  }, []);

  // Si el backend rechaza el token en cualquier pantalla, cerrar sesion.
  // Es lo que convierte un token vencido en "volves al login" en vez de en una
  // app que se ve logueada y no trae ningun dato. Ver `cuandoSePierdaLaSesion`.
  useEffect(() => {
    cuandoSePierdaLaSesion(() => {
      void salir();
    });
    return () => cuandoSePierdaLaSesion(null);
  }, [salir]);

  const valor = useMemo<Sesion>(
    () => ({ usuario, cargando, entrar, registrar, salir }),
    [usuario, cargando, entrar, registrar, salir],
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
