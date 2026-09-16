import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { mesActual } from '../../api/periodo';

/**
 * El mes que se esta mirando, compartido por las tres pantallas que lo usan.
 *
 * POR QUE EXISTE. Antes las tres pantallas llamaban a `mesActual()` cada una por
 * su cuenta, o sea que la app solo sabia mostrar el mes en curso. El 1 de
 * octubre, todo septiembre se volvia invisible: los gastos seguian en la base y
 * no habia ninguna forma de verlos desde el telefono. Para una app cuyo numero
 * protagonista es una comparacion contra el mes anterior, no poder mirar atras
 * es una limitacion seria.
 *
 * POR QUE UN CONTEXT Y NO UN PARAMETRO DE RUTA. `docs/diseno.md` dice que el
 * unico estado global es el token, y esto agrega el segundo. Se justifica porque
 * la alternativa era arrastrar `?mes=` por cada `router.push` entre resumen,
 * lista y saldo, y con que uno se olvide, la persona cambia de pantalla y vuelve
 * a septiembre sin entender por que. El mes es una sola cosa, cambia por un tap
 * y tiene que valer para toda la sesion de mirada.
 *
 * Lo que NO es: cache de datos del servidor. Cada pantalla sigue pidiendo lo
 * suyo cuando toma foco; esto solo dice QUE mes pedir.
 */
type Contexto = {
  /** `yyyy-MM` */
  mes: string;
  /** true si es el mes en curso. El boton de "siguiente" se apaga ahi. */
  esElMesActual: boolean;
  anterior: () => void;
  siguiente: () => void;
};

const ContextoDeMes = createContext<Contexto | null>(null);

/**
 * Suma o resta meses sobre `yyyy-MM`.
 *
 * Se construye con el constructor de tres argumentos, que es local, y NO con
 * `new Date("2026-09")`, que parsea como UTC y en Buenos Aires cae en el mes
 * anterior. El dia 1 es arbitrario y no se usa: solo importan anio y mes.
 *
 * `new Date(2026, 12, 1)` da enero de 2027 solo: el constructor normaliza el
 * desborde, asi que no hace falta ningun `if` para el cambio de anio.
 */
function correr(mes: string, meses: number): string {
  const [anio, numero] = mes.split('-').map(Number);
  const d = new Date(anio, numero - 1 + meses, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function MesProvider({ children }: { children: ReactNode }) {
  const [mes, setMes] = useState(mesActual());

  const anterior = useCallback(() => setMes((m) => correr(m, -1)), []);

  // No se puede ir al futuro: el backend rechaza gastos con fecha futura, asi
  // que un mes que todavia no empezo solo puede mostrar pantallas vacias.
  const siguiente = useCallback(
    () => setMes((m) => (m >= mesActual() ? m : correr(m, 1))),
    [],
  );

  const valor = useMemo<Contexto>(
    () => ({ mes, esElMesActual: mes >= mesActual(), anterior, siguiente }),
    [mes, anterior, siguiente],
  );

  return <ContextoDeMes.Provider value={valor}>{children}</ContextoDeMes.Provider>;
}

export function useMes(): Contexto {
  const contexto = useContext(ContextoDeMes);
  if (!contexto) {
    throw new Error('useMes tiene que usarse adentro de <MesProvider>');
  }
  return contexto;
}
