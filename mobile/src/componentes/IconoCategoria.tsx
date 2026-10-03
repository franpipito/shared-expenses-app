import type { LucideIcon } from 'lucide-react-native';
import Bus from 'lucide-react-native/icons/bus';
import Car from 'lucide-react-native/icons/car';
import Coffee from 'lucide-react-native/icons/coffee';
import Dumbbell from 'lucide-react-native/icons/dumbbell';
import Ellipsis from 'lucide-react-native/icons/ellipsis';
import Fuel from 'lucide-react-native/icons/fuel';
import Gift from 'lucide-react-native/icons/gift';
import GraduationCap from 'lucide-react-native/icons/graduation-cap';
import House from 'lucide-react-native/icons/house';
import Music from 'lucide-react-native/icons/music';
import PartyPopper from 'lucide-react-native/icons/party-popper';
import PawPrint from 'lucide-react-native/icons/paw-print';
import Plane from 'lucide-react-native/icons/plane';
import Shirt from 'lucide-react-native/icons/shirt';
import Smartphone from 'lucide-react-native/icons/smartphone';
import Stethoscope from 'lucide-react-native/icons/stethoscope';
import Utensils from 'lucide-react-native/icons/utensils';
import Wallet from 'lucide-react-native/icons/wallet';

import { colores } from '../tema/colores';

/**
 * El icono de una categoria.
 *
 * El backend guarda en `categoria.icono` el **nombre de un icono de Lucide**
 * ("coffee", "car", "utensils"), no un emoji: ver `CategoriaServicio.DEFAULT`
 * en el backend. Antes ese string se metia dentro de un `<Text>`, asi que la
 * app mostraba literalmente la palabra "coffee" al lado de "cafe".
 *
 * DOS COSAS QUE PARECEN ESTILO Y SON TAMANO DE BUNDLE:
 *
 * 1. **Cada icono se importa por su subpath**, `lucide-react-native/icons/car`,
 *    y no del barrel `from 'lucide-react-native'`. Se midio: importando del
 *    barrel, el bundle se lleva los ~1500 iconos de la libreria, porque Metro
 *    **no hace tree-shaking** de un barrel ESM. Con los subpaths entran los
 *    que de verdad se usan. Metro respeta el mapa `exports` del paquete, que
 *    publica `./icons/*`.
 *
 * 2. **El mapa de abajo es explicito y no una busqueda dinamica.** Lo tentador
 *    es `import * as iconos` y despues `iconos[nombre]`, que serviria para
 *    cualquier icono sin tocar este archivo -- pero obliga al bundler a incluir
 *    todos, porque no puede saber cual se usa. Es la misma razon por la que
 *    `Nutria` tiene un mapa de `require()` literales: los bundlers resuelven lo
 *    que pueden leer, no lo que se arma en runtime.
 *
 * SECCION 2.5 (categorias personalizables): paso de seis a dieciocho. Las
 * primeras seis son las que siembra el backend por default; las otras doce
 * son el selector curado que ofrece `app/categorias.tsx` al crear una propia
 * -- un puñado con sentido (vivienda, mascotas, salud, viajes...) y no
 * cualquier icono de Lucide, que seria la misma trampa del barrel pero
 * elegida a mano en vez de automatica. Mismo argumento que ya cerro esa
 * discusion: 12 iconos mas pesan ~15 KB, nada comparado con los ~1500 de la
 * libreria entera.
 */
const ICONOS: Record<string, LucideIcon> = {
  coffee: Coffee,
  car: Car,
  utensils: Utensils,
  shirt: Shirt,
  gift: Gift,
  ellipsis: Ellipsis,
  house: House,
  'paw-print': PawPrint,
  music: Music,
  'graduation-cap': GraduationCap,
  plane: Plane,
  bus: Bus,
  dumbbell: Dumbbell,
  stethoscope: Stethoscope,
  smartphone: Smartphone,
  fuel: Fuel,
  wallet: Wallet,
  'party-popper': PartyPopper,
};

/**
 * Los doce que `app/categorias.tsx` ofrece para una categoria NUEVA (las seis
 * default no se repiten aca: ya estan servidas, nadie necesita "crear" cafe).
 * Vive en este archivo y no en la pantalla para que sea IMPOSIBLE que alguien
 * ofrezca un nombre que `ICONOS` no sabe dibujar.
 */
export const ICONOS_PARA_ELEGIR = [
  'house', 'paw-print', 'music', 'graduation-cap', 'plane', 'bus',
  'dumbbell', 'stethoscope', 'smartphone', 'fuel', 'wallet', 'party-popper',
] as const;

type Props = {
  /** El campo `icono` de `CategoriaRespuesta`, tal cual viene del backend. */
  nombre: string;
  tamano?: number;
  color?: string;
};

export function IconoCategoria({ nombre, tamano = 20, color = colores.corteza }: Props) {
  // El fallback importa: las categorias las siembra el backend, asi que un
  // nombre nuevo puede llegar sin que la app se entere. Sin esto la fila
  // renderizaria `undefined` como componente y la pantalla se cae entera por una
  // categoria que nadie miro. Con esto, se ve el icono de "otros".
  const Icono = ICONOS[nombre] ?? Ellipsis;

  return (
    <Icono
      size={tamano}
      color={color}
      // Los iconos de Lucide son trazo, no relleno. 1.75 los deja del peso de
      // Nunito Sans Semi; el default (2) se ve mas pesado que el texto al lado.
      strokeWidth={1.75}
      // Decorativo: el nombre de la categoria esta escrito al lado, y que un
      // lector de pantalla diga "cafe cafe" es peor que que no diga nada.
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    />
  );
}
