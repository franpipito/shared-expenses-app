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
 */
export type MotivoDeSalida = 'manual' | 'token-vencido';

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
   * El primero que se registra crea el grupo; el segundo se suma; un tercero se
   * rechaza, porque el modelo de reparto asume dos integrantes.
   *
   * La duplicacion con `entrar` es de tres lineas y a proposito: son dos flujos
   * distintos que hoy comparten la forma de la respuesta. Extraerlas ataria el
   * registro al login por una casualidad.
   *
   * Recibe un objeto y no cuatro parametros sueltos porque los cuatro son
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


  const salir = useCallback(async (motivo: MotivoDeSalida = 'manual') => {
    // Solo local. NO se llama a /auth/cerrar-sesiones, que incrementa
    // token_version e invalida el token de TODOS los dispositivos: eso es el
    // boton de "perdi el celular", no el de "cerrar sesion".
    // El catalogo se limpia siempre: son las categorias del grupo de quien
    // estaba adentro, y volver a pedirlas no cuesta nada.
    //
    // LA COLA NO, y solo se borra si la salida fue manual. La cola es del grupo
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
      ...(motivo === 'manual' ? [borrarCola()] : []),
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
