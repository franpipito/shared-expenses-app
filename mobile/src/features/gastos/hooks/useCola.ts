import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';

import { descartar, leerCola, type GastoPendiente } from '../../../almacenamiento/cola';
import { sincronizar } from '../sincronizador';

/**
 * Los gastos que todavia no entraron al servidor.
 *
 * Existe para que la cola **no sea invisible**. Una app que dice "guardado" y
 * despues no muestra el gasto en la lista se siente rota, aunque este haciendo
 * exactamente lo correcto: la persona no tiene forma de distinguir "esta en
 * camino" de "se perdio", y la unica salida que se le ocurre es cargarlo de
 * nuevo -- o sea, duplicarlo.
 *
 * Mostrar cuantos hay pendientes convierte una sospecha en un dato.
 */
export function useCola() {
  const [pendientes, setPendientes] = useState<GastoPendiente[]>([]);

  const releer = useCallback(async () => {
    setPendientes(await leerCola());
  }, []);

  /** Intenta vaciar la cola y despues relee, para que el contador quede al dia. */
  const enviar = useCallback(async () => {
    await sincronizar();
    await releer();
  }, [releer]);

  const descartarUno = useCallback(
    async (clienteId: string) => {
      await descartar(clienteId);
      await releer();
    },
    [releer],
  );

  /**
   * Se releee CADA VEZ que la pantalla gana foco, no solo al montarse.
   *
   * Con un `useEffect` comun esto tenia un bug: el contador se leia una sola vez
   * y despues quedaba viejo. Volver del alta de un gasto, o que `recargar` de la
   * pantalla vaciara la cola, dejaba el aviso diciendo "1 gasto sin enviar" para
   * siempre -- que es peor que no tener aviso, porque miente sobre plata.
   *
   * Y se llama a `enviar` y no a `releer`: hay que leer DESPUES de que termine
   * el envio, no antes, o el contador sale viejo igual. Engancharse al envio no
   * cuesta nada porque `sincronizar()` deduplica -- si la pantalla ya lo
   * disparo, esto se suma a esa misma promesa en vez de arrancar otro.
   */
  useFocusEffect(
    useCallback(() => {
      void enviar();
    }, [enviar]),
  );

  return {
    /** Esperando a que haya red. Se reintentan solos. */
    enCamino: pendientes.filter((p) => !p.error),
    /** El servidor los rechazo. NO se reintentan, y solo los borra la persona. */
    rechazados: pendientes.filter((p) => p.error),
    releer,
    enviar,
    descartarUno,
  };
}
