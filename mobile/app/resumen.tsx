import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Boton } from '../src/componentes/Boton';
import { IconoCategoria } from '../src/componentes/IconoCategoria';
import { Monto, formatearMonto } from '../src/componentes/Monto';
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { useResumen } from '../src/features/resumen/hooks/useResumen';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * La frase que acompana a la nutria.
 *
 * El animo lo decide el backend (`CalculadorDeAnimo`); la app solo elige el
 * texto y la ilustracion. Asi mobile y web muestran la misma nutria y los
 * umbrales se ajustan sin redeployar las apps.
 *
 * El tono es deliberado: la nutria preocupada NO reta. Es una app de finanzas
 * para alguien que ya se siente mal cuando se queda sin plata antes de cobrar, y
 * una app que la haga sentir culpable se desinstala.
 */
const FRASES = {
  CONTENTA: 'Vas mejor que el mes pasado a esta altura.',
  TRANQUILA: 'Vas parecido al mes pasado a esta altura.',
  PREOCUPADA: 'Vas gastando un poco mas que el mes pasado.',
} as const;

export default function Resumen() {
  const { usuario, salir } = useSesion();
  const { resumen, cargando, error, recargar } = useResumen();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  // Vuelve a pedir el resumen cada vez que la pantalla toma foco. Es lo que hace
  // que al cerrar el modal de "nuevo gasto" el numero ya este actualizado, sin
  // tener que pasarse mensajes entre pantallas.
  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (cargando && !resumen) return <Cargando />;

  return (
    <View style={estilos.pantalla}>
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: 24 },
        ]}
        refreshControl={
          <RefreshControl refreshing={cargando} onRefresh={recargar} tintColor={colores.rio} />
        }
      >
        <View style={estilos.encabezado}>
          <View>
            <Text style={estilos.saludo}>Hola, {usuario?.nombre ?? ''}</Text>
            <Text style={estilos.seccion}>Tus gastos del mes</Text>
          </View>
          <Pressable onPress={salir} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.salir}>Salir</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {resumen ? (
          <>
            <View style={estilos.tarjetaHormiga}>
              <Nutria animo={resumen.animo} tamano={140} />
              {/*
                El numero grande es el TOTAL HORMIGA, no el total del mes. Es la
                decision del mockup de Lovable que no se negocia: la app existe
                para que ese numero se vea primero.
              */}
              <Text style={estilos.rotulo}>Gasto hormiga</Text>
              <Text style={estilos.numeroGrande}>{formatearMonto(resumen.totalHormiga)}</Text>
              <Text style={estilos.comparacion}>{FRASES[resumen.animo]}</Text>
              <Text style={estilos.detalleComparacion}>
                Mismo tramo del mes pasado: {formatearMonto(resumen.totalHormigaMesAnterior)}
              </Text>
            </View>

            <View style={estilos.fila}>
              <Text style={estilos.filaEtiqueta}>Total del mes</Text>
              <Monto valor={resumen.total} tamano={18} />
            </View>

            {/*
              La entrada a la lista. Es un link y no un segundo boton terracota:
              `docs/diseno.md` pide un solo boton principal por pantalla, y el de
              esta es "Cargar un gasto".
            */}
            <Pressable
              onPress={() => router.push('/gastos')}
              accessibilityRole="button"
              style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
            >
              <Text style={estilos.verGastos}>Ver los gastos del mes</Text>
              <Text style={estilos.flecha}>›</Text>
            </Pressable>

            {resumen.porCategoria.length > 0 ? (
              <View style={estilos.categorias}>
                <Text style={estilos.rotuloSeccion}>Por categoria</Text>
                {resumen.porCategoria.map((c) => (
                  <View key={c.categoriaId} style={estilos.categoria}>
                    <IconoCategoria nombre={c.icono} />
                    <Text style={estilos.nombreCategoria}>{c.nombre}</Text>
                    <View style={estilos.montosCategoria}>
                      <Text style={estilos.totalCategoria}>{formatearMonto(c.total)}</Text>
                      {/* El ambar aparece solo si hubo gasto hormiga. */}
                      {c.totalHormiga > 0 ? (
                        <Text style={estilos.hormigaCategoria}>
                          {formatearMonto(c.totalHormiga)} evitable
                        </Text>
                      ) : null}
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <Text style={estilos.vacio}>Todavia no cargaste nada este mes.</Text>
            )}
          </>
        ) : null}
      </ScrollView>

      <View style={[estilos.pie, { paddingBottom: insets.bottom + 12 }]}>
        <Boton titulo="Cargar un gasto" onPress={() => router.push('/gasto/nuevo')} />
      </View>
    </View>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20, gap: 20 },
  encabezado: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  saludo: { fontFamily: fuentes.displaySemi, fontSize: 24, color: colores.texto },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
    marginTop: 4,
  },
  salir: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo },

  tarjetaHormiga: {
    backgroundColor: colores.tarjeta,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colores.borde,
    padding: 24,
    alignItems: 'center',
  },
  rotulo: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
    marginTop: 8,
  },
  numeroGrande: {
    fontFamily: fuentes.displayBold,
    fontSize: 44,
    // El unico ambar de la pantalla, y es el numero que define el producto.
    color: colores.hormiga,
    marginTop: 4,
    ...numerosTabulares,
  },
  comparacion: {
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.texto,
    textAlign: 'center',
    marginTop: 10,
  },
  detalleComparacion: {
    fontFamily: fuentes.cuerpo,
    fontSize: 13,
    color: colores.textoSuave,
    textAlign: 'center',
    marginTop: 4,
  },

  fila: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 18,
    paddingVertical: 14,
  },
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.textoSuave },
  filaPresionada: { backgroundColor: colores.arena },
  verGastos: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },
  flecha: { fontFamily: fuentes.cuerpoSemi, fontSize: 20, color: colores.rioProfundo },

  categorias: { gap: 8 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
    marginBottom: 4,
  },
  categoria: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
  },
  nombreCategoria: { flex: 1, fontFamily: fuentes.cuerpo, fontSize: 16, color: colores.texto },
  montosCategoria: { alignItems: 'flex-end' },
  totalCategoria: {
    fontFamily: fuentes.displaySemi,
    fontSize: 16,
    color: colores.texto,
    ...numerosTabulares,
  },
  hormigaCategoria: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 12,
    color: colores.hormiga,
    ...numerosTabulares,
  },

  vacio: {
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.textoSuave,
    textAlign: 'center',
    paddingVertical: 20,
  },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },

  pie: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colores.borde,
    backgroundColor: colores.fondo,
  },
});
