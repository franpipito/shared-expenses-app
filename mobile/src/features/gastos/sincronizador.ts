import { ErrorDeApi, pedir } from '../../api/cliente';
import type { GastoRespuesta } from '../../api/tipos';
import { leerCola, marcarRechazado, quitarDeLaCola, type GastoPendiente } from '../../almacenamiento/cola';

/**
 * Vacia la cola de gastos contra el servidor.
 *
 * LAS TRES REGLAS, y las tres salieron de pensar que pasa de verdad en un viaje:
 *
 * 1. **En orden y frenando en el primer fallo de red.** Si el primero no entra
 *    porque no hay senial, los siguientes tampoco van a entrar: seguir es gastar
 *    bateria y sumar 75 segundos de timeout por gasto. Se corta y se reintenta
 *    despues, entero.
 *
 * 2. **Un rechazo del servidor (4xx) no se reintenta.** Si el backend dice 400
 *    -- por ejemplo, la vaquita se cerro mientras el gasto estaba en la cola --
 *    mandarlo de nuevo va a dar 400 para siempre, y peor: taparia a los gastos
 *    que si pueden entrar. Se marca y se sigue con el siguiente.
 *
 *    Pero **no se borra**. Descartar un gasto por decision de la app es
 *    exactamente la perdida de datos que esto viene a evitar. Se muestra, y
 *    decide la persona.
 *
 * 3. **Nada bloquea la interfaz.** Esta funcion se llama y no se espera; quien
 *    quiera saber como quedo, relee la cola.
 */
export type ResultadoDeEnvio = {
  enviados: number;
  pendientes: number;
  rechazados: number;
};

/** Evita que dos pantallas que ganan foco a la vez manden el mismo gasto dos veces. */
let enCurso: Promise<ResultadoDeEnvio> | null = null;

export function sincronizar(): Promise<ResultadoDeEnvio> {
  // Sin esto, entrar al resumen y que la lista tambien pida foco dispararia dos
  // envios simultaneos del mismo gasto. La clave de idempotencia lo salvaria en
  // el servidor, pero es mejor no hacer el trabajo dos veces.
  enCurso ??= enviarTodo().finally(() => {
    enCurso = null;
  });
  return enCurso;
}

async function enviarTodo(): Promise<ResultadoDeEnvio> {
  const cola = await leerCola();
  let enviados = 0;

  for (const pendiente of cola) {
    // Los ya rechazados no se vuelven a intentar: ver la regla 2.
    if (pendiente.error) continue;

    try {
      await enviar(pendiente);
      await quitarDeLaCola(pendiente.clienteId);
      enviados++;
    } catch (e) {
      const estado = e instanceof ErrorDeApi ? e.estado : 0;

      // estado 0 = no llegamos al servidor. 5xx = llegamos y se rompio del otro
      // lado. Los dos pueden andar en el proximo intento, asi que se frena aca y
      // la cola queda intacta.
      if (estado === 0 || estado >= 500) break;

      // 4xx: el servidor lo rechazo y lo va a rechazar siempre.
      await marcarRechazado(
        pendiente.clienteId,
        e instanceof ErrorDeApi ? e.message : 'El servidor lo rechazo.',
      );
    }
  }

  const quedan = await leerCola();
  return {
    enviados,
    pendientes: quedan.filter((p) => !p.error).length,
    rechazados: quedan.filter((p) => p.error).length,
  };
}

/**
 * Manda un gasto con su clave de idempotencia.
 *
 * `clienteId` es lo que hace que reintentar sea seguro. El caso que cubre no es
 * raro con mala senial: el POST llega, el servidor escribe el gasto, y la
 * respuesta se pierde en el camino de vuelta. El telefono ve un error de red y
 * no tiene forma de saber si grabo o no, asi que reintenta -- y sin la clave
 * crearia un segundo gasto identico.
 *
 * Con la clave, el backend choca contra un indice unico y devuelve el gasto que
 * ya existia. **Un gasto duplicado es peor que uno perdido**: no se ve como un
 * error, se ve como un total del mes equivocado.
 *
 * El timeout es mas corto que el de una lectura: si estamos vaciando la cola,
 * conviene fallar rapido y reintentar despues antes que quedarse 75 segundos
 * esperando a que Render despierte.
 */
function enviar(pendiente: GastoPendiente): Promise<GastoRespuesta> {
  return pedir<GastoRespuesta>('/gastos', {
    metodo: 'POST',
    cuerpo: { ...pendiente.gasto, clienteId: pendiente.clienteId },
    timeoutMs: 20_000,
  });
}
