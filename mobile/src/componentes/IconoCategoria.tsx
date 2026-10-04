import type { LucideIcon } from 'lucide-react-native';
import Car from 'lucide-react-native/icons/car';
import Coffee from 'lucide-react-native/icons/coffee';
import Ellipsis from 'lucide-react-native/icons/ellipsis';
import Gift from 'lucide-react-native/icons/gift';
import Shirt from 'lucide-react-native/icons/shirt';
import Utensils from 'lucide-react-native/icons/utensils';
import { Text } from 'react-native';

import { colores } from '../tema/colores';

/**
 * El icono de una categoria.
 *
 * Hasta la seccion 2.7, `categoria.icono` era SIEMPRE un nombre de icono de
 * Lucide ("coffee", "car", "utensils"): las unicas categorias que existian
 * eran las seis que sembraba el backend, mas un puñado curado de Lucide para
 * las que se creaban a mano desde `app/categorias.tsx` (pantalla que esa
 * misma seccion borro). Esta seccion sacó el sembrado default y la pantalla
 * de categorias: ahora cualquier categoria se crea desde el mini-onboarding o
 * el "+ Agregar" del formulario de gasto, elegiendo el icono como un **emoji
 * libre** -- mas facil de elegir en un teclado que ya tiene uno, y no hace
 * falta mantener un selector curado de iconos.
 *
 * **Las categorias de Franco y Viole, cargadas en sesiones anteriores, siguen
 * guardando nombres de Lucide** -- nada migra datos viejos (ver "Schema: no
 * hay. Es MongoDB." en CLAUDE.md). Por eso el componente prueba Lucide
 * PRIMERO y cae a texto si el nombre no es una clave conocida: un emoji no
 * matchea ninguna clave de `ICONOS`, asi que cae ahi solo, sin necesitar
 * detectar "es un emoji" de ninguna otra forma.
 *
 * **Cada icono se importa por su subpath**, `lucide-react-native/icons/car`,
 * y no del barrel `from 'lucide-react-native'`. Se midio: importando del
 * barrel, el bundle se lleva los ~1500 iconos de la libreria, porque Metro
 * **no hace tree-shaking** de un barrel ESM.
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
  /** El campo `icono` de `CategoriaRespuesta`: un nombre de Lucide (datos viejos) o un emoji. */
  nombre: string;
  tamano?: number;
  color?: string;
};

export function IconoCategoria({ nombre, tamano = 20, color = colores.corteza }: Props) {
  const Icono = ICONOS[nombre];

  if (Icono) {
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

  // Un emoji (o cualquier nombre que no sea una clave de Lucide) se dibuja
  // tal cual: un emoji YA ES su propio icono, no hace falta traducirlo.
  return (
    <Text
      style={{ fontSize: tamano, lineHeight: tamano, color }}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {nombre}
    </Text>
  );
}
