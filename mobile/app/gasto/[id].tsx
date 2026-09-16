import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';

import { ErrorDeApi } from '../../src/api/cliente';
import type { GastoRespuesta, GuardarGastoRequest } from '../../src/api/tipos';
import { borrarGasto, editarGasto, traerGasto } from '../../src/features/gastos/api';
import { FormularioDeGasto } from '../../src/features/gastos/componentes/FormularioDeGasto';
import { colores } from '../../src/tema/colores';
import { fuentes } from '../../src/tema/tipografia';
import { Cargando } from '../_layout';

/**
 * Editar o borrar un gasto.
 *
 * POR QUE HACIA FALTA: en un viaje se carga parado, rapido y con el pedido
 * esperando. Ahi se erra un monto o se olvida el toggle de hormiga, y hasta
 * ahora no habia forma de corregirlo desde el telefono. Un registro que no se
 * puede corregir deja de ser confiable, y uno que no es confiable se abandona.
 *
 * La ruta es `[id]`, o sea un segmento dinamico de expo-router: `/gasto/abc123`
 * entra aca con `id = 'abc123'`. El gasto se trae por `GET /gastos/{id}` y no
 * por parametro de navegacion, porque los parametros de ruta son texto y meter
 * un objeto ahi serializado es fragil.
 *
 * A DIFERENCIA DEL ALTA, esto SI espera a la red, en los dos sentidos: para
 * traer el gasto y para guardarlo. No pasa por la cola offline, y el motivo
 * esta en `api.ts` -- editar es una correccion deliberada que se hace sentado,
 * no el camino rapido que la cola viene a proteger.
 */
export default function EditarGasto() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();

  const [gasto, setGasto] = useState<GastoRespuesta | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        setGasto(await traerGasto(id));
      } catch (e) {
        setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el gasto.');
      }
    })();
  }, [id]);

  if (error) {
    return (
      <View style={estilos.pantallaDeError}>
        <Text style={estilos.error}>{error}</Text>
        <Pressable onPress={() => router.back()} accessibilityRole="button" hitSlop={12}>
          <Text style={estilos.volver}>Volver</Text>
        </Pressable>
      </View>
    );
  }

  if (!gasto) return <Cargando />;

  async function guardar(datos: GuardarGastoRequest) {
    await editarGasto(id, datos);
    router.back();
  }

  /**
   * Borrar pide confirmacion porque no se puede deshacer: no hay papelera ni
   * endpoint para restaurar. Es la segunda accion irreversible de la app,
   * despues de cerrar la vaquita, y las dos pasan por un Alert.
   */
  function confirmarBorrado() {
    Alert.alert(
      'Borrar este gasto',
      `${gasto!.descripcion}. No se puede deshacer.`,
      [
        { text: 'No', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              try {
                await borrarGasto(id);
                router.back();
              } catch (e) {
                setError(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar el gasto.');
              }
            })();
          },
        },
      ],
    );
  }

  return (
    <FormularioDeGasto
      inicial={gasto}
      titulo="Editar gasto"
      textoDeAccion="Guardar cambios"
      onGuardar={guardar}
      onCancelar={() => router.back()}
      // Borrar va abajo del boton principal y como texto, no como segundo
      // boton: `docs/diseno.md` dice un solo boton terracota por pantalla, y si
      // hay dos ninguno es el principal. Ademas la accion destructiva no deberia
      // competir en peso visual con la que la gente viene a hacer.
      pieExtra={
        <Pressable onPress={confirmarBorrado} accessibilityRole="button" hitSlop={8}>
          <Text style={estilos.borrar}>Borrar este gasto</Text>
        </Pressable>
      }
    />
  );
}

const estilos = StyleSheet.create({
  pantallaDeError: {
    flex: 1,
    backgroundColor: colores.fondo,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 16,
  },
  error: {
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.terracotaProfunda,
    textAlign: 'center',
  },
  volver: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },
  borrar: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.terracotaProfunda,
    textAlign: 'center',
    paddingTop: 16,
  },
});
