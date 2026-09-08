import type { TextStyle } from 'react-native';

/**
 * Las tipografias de MiNutria.
 *
 * Fraunces (serif de alto contraste) para titulos y montos; Nunito Sans para
 * todo lo demas. La eleccion salio de la evaluacion de los mockups: la
 * tipografia de Vercel fue lo que hizo que la app "se viera disenada".
 *
 * Los nombres de abajo son los que devuelve `useFonts`: en React Native no hay
 * `font-weight` que resuelva la familia como en CSS. Cada peso es un archivo y
 * una familia distinta, asi que se pide por nombre completo. Poner
 * `fontWeight: 'bold'` sobre la Regular no la pone en negrita: en iOS la
 * sintetiza fea y en Android la ignora.
 */
export const fuentes = {
  displayRegular: 'Fraunces_400Regular',
  displaySemi: 'Fraunces_600SemiBold',
  displayBold: 'Fraunces_700Bold',
  cuerpo: 'NunitoSans_400Regular',
  cuerpoSemi: 'NunitoSans_600SemiBold',
  cuerpoBold: 'NunitoSans_700Bold',
} as const;

/**
 * Los montos van siempre con esto: `fontVariant: ['tabular-nums']` hace que
 * todos los digitos ocupen el mismo ancho. Sin eso, un `$1.111` es visiblemente
 * mas angosto que un `$8.888` y una columna de plata queda temblando.
 */
export const numerosTabulares: TextStyle = { fontVariant: ['tabular-nums'] };
