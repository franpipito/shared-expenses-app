import { StyleSheet, Text, View } from 'react-native';

import type { GastoRespuesta } from '../../../api/tipos';
import { formatearMonto } from '../../../componentes/Monto';
import { colores } from '../../../tema/colores';
import { fuentes, numerosTabulares } from '../../../tema/tipografia';

/**
 * Una fila de la lista de gastos. **Esta es la pantalla del producto.**
 *
 * Lo que decidio Viole mirando los mockups: los dos ubers de $4.000 tienen que
 * poder distinguirse de un vistazo, uno marcado como evitable y el otro no. Si
 * esa distincion no se ve en la fila, la app es una planilla mas.
 *
 * Por eso el ambar aparece en tres lugares que refuerzan lo mismo -- el filo
 * izquierdo, el fondo apenas tenido y la palabra "evitable" -- y en ninguno
 * mas. Un gasto no hormiga no tiene un solo pixel ambar.
 *
 * No hay icono de categoria a proposito: competiria con la marca ambar
 * justamente donde la marca tiene que ganar.
 */

const DIA_CORTO = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short' });

/**
 * `"2026-09-07"` -> `"7 sept"`.
 *
 * OJO: `new Date("2026-09-07")` parsea el string como medianoche UTC, que en
 * Buenos Aires (UTC-3) es el 6 a las 21:00 -- la fila mostraria el dia
 * anterior. Es el bug de zona horaria mas comun con fechas ISO, y el mismo que
 * motivo el bean `Clock` del backend y el `hoyLocal()` del alta. Partir el texto
 * y usar el constructor de tres argumentos construye la fecha en hora local.
 */
function formatearDia(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return DIA_CORTO.format(new Date(anio, mes - 1, dia));
}

type Props = {
  gasto: GastoRespuesta;
  /** Para no repetir "pago Franco" en cada fila propia: solo se nombra al otro. */
  idUsuarioActual: string | undefined;
};

export function FilaGasto({ gasto, idUsuarioActual }: Props) {
  const compartido = gasto.tipo === 'COMPARTIDO';
  const loPagoElOtro = gasto.pagadoPor.id !== idUsuarioActual;

  // El segundo renglon se arma con las partes que aportan algo y se une con un
  // separador. Encadenar los textos a mano deja "7 sept ·  · cafe" cuando alguna
  // parte falta, que es como se ven las listas mal hechas.
  const detalle = [
    formatearDia(gasto.fecha),
    gasto.categoria.nombre,
    compartido ? (loPagoElOtro ? `pago ${gasto.pagadoPor.nombre}` : 'compartido') : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <View
      style={[estilos.fila, gasto.esHormiga && estilos.filaHormiga]}
      // Sin esto, un lector de pantalla lee cuatro textos sueltos y la marca de
      // hormiga -- que es un color -- no se lee de ninguna forma.
      accessible
      accessibilityLabel={
        `${gasto.descripcion}, ${formatearMonto(gasto.monto)}, ${detalle}` +
        (gasto.esHormiga ? ', gasto evitable' : '')
      }
    >
      {/* El filo ambar. Es la marca que hace scaneable la lista de un vistazo. */}
      {gasto.esHormiga ? <View style={estilos.filo} /> : null}

      <View style={estilos.texto}>
        <Text style={estilos.descripcion} numberOfLines={1}>
          {gasto.descripcion}
        </Text>
        <Text style={estilos.detalle} numberOfLines={1}>
          {detalle}
        </Text>
      </View>

      <View style={estilos.montos}>
        <Text style={estilos.monto}>{formatearMonto(gasto.monto)}</Text>
        {gasto.esHormiga ? (
          <Text style={estilos.evitable}>evitable</Text>
        ) : compartido ? (
          // En un compartido, el monto de arriba es el total del gasto y este es
          // la parte que le toca a quien pago. Los dos numeros los calculo el
          // backend: la app no divide plata.
          <Text style={estilos.parte}>tu parte {formatearMonto(parteMia(gasto, idUsuarioActual))}</Text>
        ) : null}
      </View>
    </View>
  );
}

/**
 * Cuanto de este gasto me toca a mi.
 *
 * **Esto NO es aritmetica de plata**: no suma ni divide, elige entre dos numeros
 * que el backend ya calculo. `montoPagador` es lo que le toca a quien pago y
 * `deudaGenerada` es lo que le toca al otro, y el backend garantiza que suman el
 * total exacto -- es la decision de la sesion 1, el reparto resuelto al
 * escribir, que existe justamente para que nadie divida en la lectura.
 */
function parteMia(gasto: GastoRespuesta, idUsuarioActual: string | undefined): number {
  return gasto.pagadoPor.id === idUsuarioActual ? gasto.montoPagador : gasto.deudaGenerada;
}

const estilos = StyleSheet.create({
  fila: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 16,
    paddingVertical: 14,
    // Deja lugar al filo ambar sin que la fila hormiga tenga otro alto ni otro
    // margen que las demas: lo que cambia entre las dos es el color, nada de la
    // geometria.
    overflow: 'hidden',
  },
  filaHormiga: { backgroundColor: colores.hormigaSuave, borderColor: colores.hormiga },
  filo: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: 5,
    backgroundColor: colores.hormiga,
  },

  texto: { flex: 1 },
  descripcion: { fontFamily: fuentes.cuerpoSemi, fontSize: 16, color: colores.texto },
  detalle: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave, marginTop: 2 },

  montos: { alignItems: 'flex-end' },
  monto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    ...numerosTabulares,
  },
  evitable: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 12,
    color: colores.hormiga,
    marginTop: 2,
  },
  parte: {
    fontFamily: fuentes.cuerpo,
    fontSize: 12,
    color: colores.rioProfundo,
    marginTop: 2,
    ...numerosTabulares,
  },
});
