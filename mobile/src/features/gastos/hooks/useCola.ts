import { useCallback, useEffect, useState } from 'react';

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

  useEffect(() => {
    void releer();
  }, [releer]);

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
