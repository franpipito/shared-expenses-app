import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { mesActual, nombreDelMes } from '../src/api/periodo';
import { formatearMonto } from '../src/componentes/Monto';
import { Nutria } from '../src/componentes/Nutria';
import { useSaldo } from '../src/features/saldo/hooks/useSaldo';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * La seccion de pareja: quien le debe a quien.
 *
 * El encuadre importa y esta en el CLAUDE.md: **no es "nuestra plata"**. Viole y
 * Franco tienen ingresos separados y no una economia comun, asi que esta
 * pantalla no muestra un total conjunto ni un presupuesto compartido -- muestra
 * una deuda entre dos personas, que es lo unico que existe entre ellos.
 *
 * Aca va la ilustracion de las dos nutrias, que Viole pidio explicitamente al
 * evaluar los mockups: "dos nutrias que sean ellos", no una sola.
 *
 * No hay ambar en toda la pantalla. El ambar es del gasto hormiga, y un gasto
 * compartido puede ser hormiga o no -- eso se mira en la lista, no aca. El color
 * de esta seccion es el teal.
 */
export default function Saldo() {
  const { saldo, cargando, error, recargar } = useSaldo();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (cargando && !saldo) return <Cargando />;

  // `aFavorMio` viene con signo justamente para no tener que comparar ids aca:
  // positivo = me deben, negativo = debo. El backend ya resolvio de que lado
  // estoy, que es la clase de cuenta que no conviene repetir en el cliente.
  const aFavor = saldo?.aFavorMio ?? 0;
  const aMano = aFavor === 0;

  return (
    <View style={estilos.pantalla}>
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
        refreshControl={
          <RefreshControl refreshing={cargando} onRefresh={recargar} tintColor={colores.rio} />
        }
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>La cuenta de {nombreDelMes(mesActual())}</Text>
            <Text style={estilos.titulo}>Quien le debe a quien</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Resumen</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {saldo ? (
          <View style={estilos.tarjeta}>
            <Nutria animo="NOSOTROS" tamano={150} />

            {aMano ? (
              <>
                <Text style={estilos.aMano}>Estan a mano</Text>
                <Text style={estilos.bajada}>
                  Ningun gasto compartido quedo sin equilibrar este mes.
                </Text>
              </>
            ) : (
              <>
                <Text style={estilos.rotulo}>
                  {aFavor > 0 ? `${saldo.deudorNombre} te debe` : `Le debes a ${saldo.acreedorNombre}`}
                </Text>
                {/*
                  El numero grande. Se usa `monto`, que el backend garantiza
                  positivo, y no `aFavorMio`: el signo ya lo dijo el rotulo de
                  arriba con palabras, y un "-$505,00" abajo de "le debes a
                  Franco" es la misma informacion dos veces, una de ellas en un
                  idioma peor.
                */}
                <Text
                  style={estilos.numeroGrande}
                  // Antes que partirse en dos lineas, se achica. Es exactamente
                  // el bug que hoy tiene el numero grande del resumen, y sale
                  // con estas dos props y no con una cuenta de tamanos.
                  numberOfLines={1}
                  adjustsFontSizeToFit
                >
                  {formatearMonto(saldo.monto)}
                </Text>
              </>
            )}
          </View>
        ) : null}

        {/*
          Esto no es letra chica: el saldo se resetea todos los meses, y un
          numero que desaparece sin aviso es peor que no tenerlo. La alternativa
          (saldo historico) se descarto porque sin una entidad de liquidacion
          -- un "ya te pague" -- solo crece y deja de significar algo.
        */}
        <Text style={estilos.nota}>
          Es la cuenta del mes, no el historico. Arranca de cero cada mes, asi que
          conviene arreglarla antes de que termine.
        </Text>

        <Pressable
          onPress={() => router.push('/gastos')}
          accessibilityRole="button"
          style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
        >
          <Text style={estilos.verGastos}>Ver los gastos del mes</Text>
          <Text style={estilos.flecha}>›</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20, gap: 20 },

  encabezado: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  encabezadoTexto: { flex: 1 },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 24, color: colores.texto, marginTop: 4 },
  volver: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },

  tarjeta: {
    backgroundColor: colores.tarjeta,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colores.borde,
    padding: 24,
    alignItems: 'center',
  },
  rotulo: {
    fontFamily: fuentes.cuerpo,
    fontSize: 16,
    color: colores.textoSuave,
    marginTop: 8,
    textAlign: 'center',
  },
  numeroGrande: {
    fontFamily: fuentes.displayBold,
    fontSize: 40,
    // Teal, que es el color de lo compartido. NUNCA ambar: el ambar significa
    // gasto hormiga y solo eso.
    color: colores.rioProfundo,
    marginTop: 4,
    ...numerosTabulares,
  },
  aMano: {
    fontFamily: fuentes.displaySemi,
    fontSize: 26,
    color: colores.hoja,
    marginTop: 8,
    textAlign: 'center',
  },
  bajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    textAlign: 'center',
    marginTop: 6,
  },

  nota: {
    fontFamily: fuentes.cuerpo,
    fontSize: 13,
    color: colores.textoSuave,
    textAlign: 'center',
    paddingHorizontal: 8,
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
  filaPresionada: { backgroundColor: colores.arena },
  verGastos: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },
  flecha: { fontFamily: fuentes.cuerpoSemi, fontSize: 20, color: colores.rioProfundo },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
