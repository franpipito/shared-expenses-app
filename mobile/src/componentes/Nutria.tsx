import { Image, type ImageStyle, type StyleProp } from 'react-native';

import type { AnimoNutria } from '../api/tipos';

/**
 * La nutria, que es el diferencial de producto y no una decoracion.
 *
 * El `require` con ruta literal no es un capricho de estilo: Metro resuelve los
 * assets en tiempo de compilacion leyendo el codigo, asi que
 * `require('../assets/nutrias/' + animo + '.png')` NO funciona. Tiene que ser un
 * mapa de rutas escritas enteras.
 *
 * Las imagenes estan copiadas de `/assets/nutrias` de la raiz del repo, que es
 * la fuente de verdad y la que comparte con la futura app web. Metro no mira
 * fuera de la carpeta del proyecto sin tocar `metro.config.js`, y esa
 * configuracion es fragil en los builds de EAS.
 */
const ILUSTRACIONES: Record<AnimoNutria | 'NOSOTROS', number> = {
  CONTENTA: require('../../assets/nutrias/contenta.png'),
  TRANQUILA: require('../../assets/nutrias/tranquila.png'),
  PREOCUPADA: require('../../assets/nutrias/preocupada.png'),
  NOSOTROS: require('../../assets/nutrias/nosotros.png'),
};

/**
 * El texto alternativo lo decide el codigo y no el backend, igual que la
 * ilustracion. Lo que decide el backend es el `animo`.
 */
const DESCRIPCIONES: Record<AnimoNutria | 'NOSOTROS', string> = {
  CONTENTA: 'Nutria contenta, flotando tranquila en el agua',
  TRANQUILA: 'Nutria tranquila, sentada',
  PREOCUPADA: 'Nutria preocupada',
  NOSOTROS: 'Dos nutrias tomadas de la mano',
};

type Props = {
  animo: AnimoNutria | 'NOSOTROS';
  tamano?: number;
  style?: StyleProp<ImageStyle>;
};

export function Nutria({ animo, tamano = 160, style }: Props) {
  return (
    <Image
      source={ILUSTRACIONES[animo]}
      accessibilityLabel={DESCRIPCIONES[animo]}
      // `contain` y no `cover`: los PNG tienen fondo transparente y proporciones
      // distintas entre si. Con `cover` la nutria quedaria recortada.
      resizeMode="contain"
      style={[{ width: tamano, height: tamano }, style]}
    />
  );
}
