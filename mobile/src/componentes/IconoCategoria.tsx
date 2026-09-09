import type { LucideIcon } from 'lucide-react-native';
import Car from 'lucide-react-native/icons/car';
import Coffee from 'lucide-react-native/icons/coffee';
import Ellipsis from 'lucide-react-native/icons/ellipsis';
import Gift from 'lucide-react-native/icons/gift';
import Shirt from 'lucide-react-native/icons/shirt';
import Utensils from 'lucide-react-native/icons/utensils';

import { colores } from '../tema/colores';

/**
 * El icono de una categoria.
 *
 * El backend guarda en `categoria.icono` el **nombre de un icono de Lucide**
 * ("coffee", "car", "utensils"), no un emoji: ver `SembradorDeCategorias`. Antes
 * ese string se metia dentro de un `<Text>`, asi que la app mostraba
 * literalmente la palabra "coffee" al lado de "cafe".
 *
 * DOS COSAS QUE PARECEN ESTILO Y SON TAMANO DE BUNDLE:
 *
 * 1. **Cada icono se importa por su subpath**, `lucide-react-native/icons/car`,
 *    y no del barrel `from 'lucide-react-native'`. Se midio: importando del
 *    barrel, el bundle se lleva los ~1500 iconos de la libreria, porque Metro
 *    **no hace tree-shaking** de un barrel ESM. Con los subpaths entran seis.
 *    Metro respeta el mapa `exports` del paquete, que publica `./icons/*`.
 *
 * 2. **El mapa de abajo es explicito y no una busqueda dinamica.** Lo tentador
 *    es `import * as iconos` y despues `iconos[nombre]`, que serviria para
 *    cualquier icono sin tocar este archivo -- pero obliga al bundler a incluir
 *    todos, porque no puede saber cual se usa. Es la misma razon por la que
 *    `Nutria` tiene un mapa de `require()` literales: los bundlers resuelven lo
 *    que pueden leer, no lo que se arma en runtime.
 */
const ICONOS: Record<string, LucideIcon> = {
  coffee: Coffee,
  car: Car,
  utensils: Utensils,
  shirt: Shirt,
  gift: Gift,
  ellipsis: Ellipsis,
};

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
