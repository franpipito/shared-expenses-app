import { Text, type StyleProp, type TextStyle } from 'react-native';

import { colores } from '../tema/colores';
import { fuentes, numerosTabulares } from '../tema/tipografia';

/**
 * Formatea plata en pesos argentinos: punto para los miles, coma para los
 * decimales.
 *
 * Usa `Intl.NumberFormat`, que en React Native 0.86 esta disponible en las dos
 * plataformas (Hermes trae ICU). No se arma a mano con replace de puntos y
 * comas, que es el camino tipico y el que despues falla con los negativos.
 *
 * Ojo: el numero que entra viene de un BigDecimal de Java que paso por JSON, y
 * en JavaScript ya es punto flotante. **Formatear esta bien; hacer cuentas no.**
 * Toda la aritmetica de plata vive en el backend.
 */
const FORMATO = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatearMonto(valor: number): string {
  return FORMATO.format(valor);
}

type Props = {
  valor: number;
  tamano?: number;
  color?: string;
  style?: StyleProp<TextStyle>;
};

export function Monto({ valor, tamano = 20, color = colores.texto, style }: Props) {
  return (
    <Text
      style={[
        {
          fontFamily: fuentes.displaySemi,
          fontSize: tamano,
          color,
        },
        numerosTabulares,
        style,
      ]}
    >
      {formatearMonto(valor)}
    </Text>
  );
}
