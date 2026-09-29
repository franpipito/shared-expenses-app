import { borrarCatalogo } from '../../almacenamiento/catalogo';
import { borrarCola } from '../../almacenamiento/cola';
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
  registrarse: (datos: DatosDeRegistro) => Promise<void>;
  salir: (motivo?: MotivoDeSalida) => Promise<void>;
  borrarCuenta: (password: string) => Promise<void>;
  pedirCodigo: (email: string) => Promise<void>;
  restablecer: (datos: DatosDeRestablecer) => Promise<void>;
};

/** Lo que pide el reseteo: el email, el codigo del mail y la contrasena nueva. */
export type DatosDeRestablecer = {
  email: string;
  codigo: string;
  password: string;
};

const ContextoDeSesion = createContext<Sesion | null>(null);

/**
 * Lo que hace falta para crear una cuenta. Hasta la v1.0 llevaba tambien un
 * codigo de invitacion; el registro ahora es abierto y cada cuenta nueva crea su
 * propio grupo.
 */
export type DatosDeRegistro = {
  nombre: string;
  email: string;
  password: string;
};

/**
 * Por que se cerro la sesion, que NO es lo mismo en los dos casos.
 *
 * - `'manual'`: la persona toco Cerrar sesion. Puede ser para pasarle el
 *   telefono a la otra, asi que hay que borrar todo lo que era suyo.
 * - `'token-vencido'`: el backend devolvio 401 y la app reacciona sola. **Sigue
 *   siendo la misma persona**, que va a volver a entrar con su propia cuenta.
 *
 * La distincion existe por una perdida de datos concreta: la cola de gastos
 * pendientes se borra al cerrar sesion, y un token de 30 dias se vence justo
 * cuando la app lleva rato sin abrirse -- que es cuando mas gastos sin mandar
 * puede haber. Sin esto, abrir la app en el hotel al volver del viaje tiraba los
 * gastos cargados sin senial, en silencio y antes de que nadie los viera.
 *
 * - `'cuenta-borrada'`: la persona borro su cuenta. Se limpia todo, igual que
 *   en la manual: los gastos que quedaron en la cola eran de una cuenta que ya
 *   no existe, y mandarlos con el token de quien entre despues los pondria a su
 *   nombre.
 */
export type MotivoDeSalida = 'manual' | 'token-vencido' | 'cuenta-borrada';

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
   * retipear la contrasena que acabas de elegir es friccion pura, justo en el
   * momento mas fragil -- el de alguien que todavia no vio nada de la app.
   *
   * Cada registro crea su propio grupo, de un solo integrante: sumarse al de
   * otra persona llega en la v1.1, con un codigo por grupo.
   *
   * La duplicacion con `entrar` es de tres lineas y a proposito: son dos flujos
   * distintos que hoy comparten la forma de la respuesta. Extraerlas ataria el
   * registro al login por una casualidad.
   *
   * Recibe un objeto y no tres parametros sueltos porque los tres son
   * `string`: con posicionales, cambiar el email por la contrasena compila
   * igual y se descubre recien en runtime.
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


  /**
   * "Me olvide la contrasena", paso 1: pedir el codigo. El backend responde
   * igual exista o no la cuenta, asi que aca no hay nada que mirar.
   */
  const pedirCodigo = useCallback(async (email: string) => {
    await pedir<void>('/auth/olvide-contrasena', {
      metodo: 'POST',
      cuerpo: { email },
      sinToken: true,
    });
  }, []);

  /**
   * Paso 2: codigo + contrasena nueva. El backend devuelve un token, igual que
   * el login, asi que la persona queda adentro sin volver a tipear la
   * contrasena que acaba de elegir.
   */
  const restablecer = useCallback(async (datos: DatosDeRestablecer) => {
    const respuesta = await pedir<TokenRespuesta>('/auth/restablecer-contrasena', {
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

  const salir = useCallback(async (motivo: MotivoDeSalida = 'manual') => {
    // Solo local. NO se llama a /auth/cerrar-sesiones, que incrementa
    // token_version e invalida el token de TODOS los dispositivos: eso es el
    // boton de "perdi el celular", no el de "cerrar sesion".
    // El catalogo se limpia siempre: son las categorias del grupo de quien
    // estaba adentro, y volver a pedirlas no cuesta nada.
    //
    // LA COLA NO SIEMPRE: se conserva solo si el token vencio. La cola es del grupo
    // de quien estaba adentro: si quedan gastos pendientes y despues entra la
    // otra persona en el mismo telefono, el sincronizador los mandaria con SU
    // token y quedarian a su nombre. Pero un 401 NO es un cambio de persona --
    // es la misma, con el token vencido -- y borrar ahi seria tirar los gastos
    // que todavia no se mandaron, que es exactamente lo que la cola vino a
    // evitar. Se conservan y se mandan cuando vuelva a entrar.
    await Promise.all([
      borrarToken(),
      borrarUsuario(),
      borrarCatalogo(),
      // La excepcion es el token vencido, no la regla: cualquier motivo nuevo
      // que se agregue borra la cola salvo que alguien decida lo contrario.
      ...(motivo === 'token-vencido' ? [] : [borrarCola()]),
    ]);
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
      void salir('token-vencido');
    });
    return () => cuandoSePierdaLaSesion(null);
  }, [salir]);

  /**
   * Borrar la cuenta y salir.
   *
   * La contrasena la verifica el backend. Si esta mal devuelve 400, no 401, y
   * es a proposito: un 401 con token hace que `cliente.ts` cierre la sesion
   * sola (asi detecta un token vencido), y equivocarse al confirmar no puede
   * sacarte de la app.
   *
   * Si el POST falla, la excepcion sube a la pantalla y no se sale: la cuenta
   * sigue existiendo, y la persona tiene que ver por que no se borro.
   */
  const borrarCuenta = useCallback(
    async (password: string) => {
      await pedir<void>('/auth/borrar-cuenta', { metodo: 'POST', cuerpo: { password } });
      await salir('cuenta-borrada');
    },
    [salir],
  );

  const valor = useMemo<Sesion>(
    () => ({ usuario, cargando, entrar, registrarse, salir, borrarCuenta, pedirCodigo, restablecer }),
    [usuario, cargando, entrar, registrarse, salir, borrarCuenta, pedirCodigo, restablecer],
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
