import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { mesActual, nombreDelMes } from '../src/api/periodo';
import { Boton } from '../src/componentes/Boton';
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { FilaGasto } from '../src/features/gastos/componentes/FilaGasto';
import { useGastos } from '../src/features/gastos/hooks/useGastos';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * La lista de gastos del mes.
 *
 * Es la contraparte del resumen: el resumen contesta "como vengo" con un solo
 * numero, y esta pantalla contesta "que carge" con el detalle. La razon de que
 * exista es una sola: **aca se ve la marca de hormiga gasto por gasto**, que es
 * la distincion que define el producto. Ver `FilaGasto`.
 *
 * Se usa FlatList y no un ScrollView con `.map()`. Con las decenas de filas que
 * tiene un mes las dos andarian igual, pero FlatList virtualiza (dibuja solo lo
 * visible) y trae de fabrica el estado vacio, el separador y el pull to refresh.
 * Es el componente que React Native tiene para esto.
 */
export default function Gastos() {
  const { usuario } = useSesion();
  const { gastos, cargando, error, recargar } = useGastos();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  // Igual que el resumen: recargar al tomar foco es lo que hace que al cerrar el
  // modal de alta el gasto nuevo ya este en la lista, sin pasarse mensajes entre
  // pantallas.
  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (cargando && !gastos) return <Cargando />;

  // Contar filas NO es hacer aritmetica de plata: son gastos, no pesos. Los
  // totales del mes los calcula el backend y se miran en el resumen; sumarlos
  // aca seria tener la misma cuenta en dos lugares, y en punto flotante.
  const cuantos = gastos?.length ?? 0;
  const cuantosHormiga = gastos?.filter((g) => g.esHormiga).length ?? 0;

  return (
    <View style={estilos.pantalla}>
      <FlatList
        data={gastos ?? []}
        keyExtractor={(g) => g.id}
        renderItem={({ item }) => <FilaGasto gasto={item} idUsuarioActual={usuario?.id} />}
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: 24 },
          // Sin esto el estado vacio queda pegado al encabezado en vez de
          // ocupar la pantalla, porque el contenedor solo mide lo que hay.
          cuantos === 0 && estilos.contenidoVacio,
        ]}
        ItemSeparatorComponent={() => <View style={estilos.separador} />}
        refreshControl={
          <RefreshControl refreshing={cargando} onRefresh={recargar} tintColor={colores.rio} />
        }
        ListHeaderComponent={
          <View style={estilos.encabezado}>
            <View style={estilos.encabezadoTexto}>
              <Text style={estilos.seccion}>Gastos de {nombreDelMes(mesActual())}</Text>
              <Text style={estilos.titulo}>
                {cuantos === 0
                  ? 'Nada cargado'
                  : `${cuantos} ${cuantos === 1 ? 'gasto' : 'gastos'}`}
              </Text>
              {cuantosHormiga > 0 ? (
                <Text style={estilos.cuantosHormiga}>
                  {cuantosHormiga} {cuantosHormiga === 1 ? 'evitable' : 'evitables'}
                </Text>
              ) : null}
            </View>
            <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
              <Text style={estilos.volver}>Resumen</Text>
            </Pressable>
          </View>
        }
        ListEmptyComponent={
          error ? null : (
            <View style={estilos.vacio}>
              <Nutria animo="TRANQUILA" tamano={120} />
              <Text style={estilos.vacioTitulo}>Todavia no cargaste nada este mes</Text>
              <Text style={estilos.vacioBajada}>
                Los gastos que cargues van a aparecer aca, con los evitables marcados.
              </Text>
            </View>
          )
        }
        ListFooterComponent={error ? <Text style={estilos.error}>{error}</Text> : null}
      />

      <View style={[estilos.pie, { paddingBottom: insets.bottom + 12 }]}>
        <Boton titulo="Cargar un gasto" onPress={() => router.push('/gasto/nuevo')} />
      </View>
    </View>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20 },
  contenidoVacio: { flexGrow: 1 },

  encabezado: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
  },
  encabezadoTexto: { flex: 1 },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 24, color: colores.texto, marginTop: 4 },
  cuantosHormiga: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 13,
    color: colores.hormiga,
    marginTop: 2,
  },
  volver: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },

  separador: { height: 8 },

  vacio: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 6, paddingBottom: 40 },
  vacioTitulo: {
    fontFamily: fuentes.displaySemi,
    fontSize: 18,
    color: colores.texto,
    textAlign: 'center',
  },
  vacioBajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    textAlign: 'center',
    paddingHorizontal: 20,
  },

  error: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.terracotaProfunda,
    marginTop: 16,
  },

  pie: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colores.borde,
    backgroundColor: colores.fondo,
  },
});
