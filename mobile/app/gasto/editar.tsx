import { Redirect, useLocalSearchParams } from 'expo-router';

import { FormularioDeGasto } from '../../src/features/gastos/componentes/FormularioDeGasto';

/**
 * Ruta de edicion: `/gasto/editar?id=...`.
 *
 * El id va como query y no como segmento (`/gasto/[id]`) para que no choque con
 * la ruta estatica `/gasto/nuevo`: con un segmento dinamico, "nuevo" seria un id
 * valido y las dos rutas competirian por la misma URL.
 */
export default function EditarGasto() {
  const { id } = useLocalSearchParams<{ id?: string }>();

  // Sin id no hay nada que editar. Puede pasar con un deep link mal armado.
  if (!id) return <Redirect href="/gastos" />;

  return <FormularioDeGasto id={id} />;
}
