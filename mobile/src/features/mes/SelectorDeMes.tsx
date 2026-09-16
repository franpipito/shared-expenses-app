import ChevronLeft from 'lucide-react-native/icons/chevron-left';
import ChevronRight from 'lucide-react-native/icons/chevron-right';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { nombreDelMes } from '../../api/periodo';
import { colores } from '../../tema/colores';
import { fuentes } from '../../tema/tipografia';
import { useMes } from './mes';

/**
 * Las dos flechas para moverse de mes, con el mes al medio.
 *
 * Va en el encabezado de las tres pantallas que muestran datos de un periodo, y
 * siempre en el mismo lugar, para que cambiar de mes sea un gesto y no una
 * busqueda.
 *
 * La flecha de "siguiente" se apaga en el mes en curso en vez de desaparecer: un
 * control que se va y vuelve hace que los otros elementos se muevan de lugar, y
 * eso en una barra que se toca seguido produce taps equivocados.
 */
export function SelectorDeMes() {
  const { mes, esElMesActual, anterior, siguiente } = useMes();

  return (
    <View style={estilos.barra}>
      <Pressable
        onPress={anterior}
        hitSlop={10}
        accessibilityRole="button"
        accessibilityLabel="Mes anterior"
        style={({ pressed }) => [estilos.flecha, pressed && estilos.presionada]}
      >
        <ChevronLeft size={20} color={colores.rioProfundo} strokeWidth={2} />
      </Pressable>

      <Text style={estilos.mes}>{nombreDelMes(mes)}</Text>

      <Pressable
        onPress={siguiente}
        disabled={esElMesActual}
        hitSlop={10}
        accessibilityRole="button"
        accessibilityLabel="Mes siguiente"
        accessibilityState={{ disabled: esElMesActual }}
        style={({ pressed }) => [
          estilos.flecha,
          pressed && !esElMesActual && estilos.presionada,
          esElMesActual && estilos.apagada,
        ]}
      >
        <ChevronRight size={20} color={colores.rioProfundo} strokeWidth={2} />
      </Pressable>
    </View>
  );
}

const estilos = StyleSheet.create({
  barra: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 },
  flecha: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
  },
  presionada: { backgroundColor: colores.arena },
  apagada: { opacity: 0.35 },
  mes: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    // Ancho fijo para que las flechas no se muevan entre "mayo" y "septiembre".
    minWidth: 120,
    textAlign: 'center',
    textTransform: 'capitalize',
  },
});
