import { useFocusEffect, useRouter } from 'expo-router';
import MenuIcono from 'lucide-react-native/icons/menu';
import { useCallback } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { AnimoNutria } from '../src/api/tipos';
import { Boton } from '../src/componentes/Boton';
import { IconoCategoria } from '../src/componentes/IconoCategoria';
import { Monto, formatearMonto } from '../src/componentes/Monto';
import { nombreDeCategoria } from '../src/componentes/nombreDeCategoria';
import { Nutria } from '../src/componentes/Nutria';
import { useBalance } from '../src/features/balance/hooks/useBalance';
import { useSesion } from '../src/features/auth/sesion';
import { SelectorDeMes } from '../src/features/mes/SelectorDeMes';
import { useMes } from '../src/features/mes/mes';
import { useResumen } from '../src/features/resumen/hooks/useResumen';
import { AvisoDeCola } from '../src/features/gastos/componentes/AvisoDeCola';
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
  PREOCUPADA: 'Vas gastando un poco más que el mes pasado.',
} as const;

/**
 * CONTENTA significa DOS cosas distintas y la pantalla tenia una sola frase.
 *
 * `CalculadorDeAnimo` devuelve CONTENTA tanto cuando bajaste 10% o mas respecto
 * del mes pasado como cuando **no anotaste ni un gasto evitable** -- y esa
 * segunda regla aplica aunque no haya historia con que comparar, que es una
 * decision deliberada y testeada ("sin gasto hormiga la nutria esta contenta,
 * aunque no haya historia").
 *
 * El problema es que la frase de CONTENTA afirma una comparacion. El dia que
 * Viole abre la app por primera vez, con la base vacia, leia **"Vas mejor que el
 * mes pasado a esta altura"** arriba de un "Mismo tramo del mes pasado: $0,00".
 * La primera pantalla que ve en su vida le afirmaba algo falso, que es la peor
 * forma de empezar a creerle a una app de plata.
 *
 * El arreglo va en el copy y NO en la regla: la regla esta bien y tiene sus
 * tests. Con cero hormiga se dice lo que es verdad sin inventar una comparacion.
 */
function fraseDelAnimo(
  animo: AnimoNutria,
  totalHormiga: number,
  esElMesActual: boolean,
): string {
  // El "todavia" solo vale en el mes en curso. Mirando agosto en octubre, decir
  // "todavia no anotaste" promete algo que ya no puede pasar: ese mes cerro.
  // Es el precio de que ahora se pueda navegar a meses anteriores.
  if (totalHormiga === 0) {
    return esElMesActual
      ? 'Todavía no anotaste ningún gasto evitable este mes.'
      : 'No anotaste ningún gasto evitable ese mes.';
  }
  return FRASES[animo];
}

/**
 * Rediseño de sección 2.3b (v1.1), a pedido de Viole por audio de WhatsApp,
 * probando la app de verdad: quería ver "cuánto gastaste, tu saldo, el
 * gasto hormiga y la nutria" en la misma pantalla, en vez de que el gasto
 * hormiga fuera el único protagonista.
 *
 * Es un cambio consciente contra "el número grande es el gasto hormiga, no
 * se negocia" (comentario histórico de `tarjetaHormiga` más abajo): esa
 * regla salió del mockup de Lovable, antes de que existiera un solo día de
 * uso real. Meses de uso real ya pesaron más que la entrevista original en
 * este proyecto (ver "descripción opcional" en CLAUDE.md), y esta es la
 * misma clase de corrección.
 *
 * El gasto hormiga NO se achica ni se saca: sigue con la nutria, en ámbar,
 * con la comparación contra el mes pasado. Solo deja de ser el único número
 * de la pantalla -- ahora convive arriba con "Gastaste este mes" y "Mi
 * Plata" (`app/mi-plata.tsx`, sección 2.3b).
 *
 * El menú único (sección 2.3c): un solo ícono hamburguesa abre `app/menu.tsx`,
 * que junta las tres filas de navegación (pedido de Viole, sección 2.3b) Y
 * lo que antes vivía en `app/ajustes.tsx` -- a pedido de Franco, mirando
 * cómo Instagram junta todo bajo un solo ícono en vez de dos botones
 * separados en el encabezado. Reemplaza al `Modal` chico que había antes:
 * ahora es una pantalla propia, con sus propias secciones.
 */
export default function Resumen() {
  const { usuario } = useSesion();
  const { resumen, cargando, error, recargar } = useResumen();
  const { balance, cargando: cargandoBalance, recargar: recargarBalance } = useBalance();
  const { esElMesActual } = useMes();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  // Vuelve a pedir el resumen cada vez que la pantalla toma foco. Es lo que hace
  // que al cerrar el modal de "nuevo gasto" el numero ya este actualizado, sin
  // tener que pasarse mensajes entre pantallas. "Mi Plata" se recarga junto con
  // el resumen: un gasto personal recien cargado tiene que bajar el restante
  // sin que haga falta entrar a su propia pantalla.
  useFocusEffect(
    useCallback(() => {
      void recargar();
      void recargarBalance();
    }, [recargar, recargarBalance]),
  );

  if (cargando && !resumen) return <Cargando />;

  // Solo tinta el tile cuando hay un numero negativo real que mostrar -- igual
  // que en mi-plata.tsx, para no confundir "cargando" con "en rojo".
  const miPlataEnRojo = balance != null && balance.ingresos.length > 0 && balance.restante < 0;

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
          <View style={estilos.encabezadoTexto}>
            {/*
              Sin nombre, "Hola," con la coma colgando se ve roto. Con el arreglo
              del token vencido esto no deberia pasar mas, pero el saludo no tiene
              por que depender de que ningun otro arreglo siga funcionando.
            */}
            <Text style={estilos.saludo}>{usuario?.nombre ? `Hola, ${usuario.nombre}` : 'Hola'}</Text>
            <Text style={estilos.seccion}>Tus gastos del mes</Text>
          </View>
          {/*
            Un solo icono (seccion 2.3c): antes eran dos botones (el menu de
            navegacion y la pastilla de Ajustes). Instagram junta todo bajo
            un unico icono, y es lo que pidio Franco -- ver `app/menu.tsx`.
          */}
          <Pressable
            onPress={() => router.push('/menu')}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Menú"
            style={({ pressed }) => [estilos.iconoMenu, pressed && estilos.pastillaPresionada]}
          >
            <MenuIcono size={18} color={colores.rioProfundo} strokeWidth={1.75} />
          </Pressable>
        </View>

        <SelectorDeMes />

        <AvisoDeCola />

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {resumen ? (
          <>
            {/*
              Los dos tiles nuevos (seccion 2.3b). "Gastaste este mes" es el
              mismo dato que antes vivia en la fila "Total del mes" -- solo
              cambia de lugar y de tamano. "Mi Plata" es nuevo: toca para
              abrir `app/mi-plata.tsx`, donde se carga un ingreso y se ve el
              historial. Nunca ambar (es del gasto hormiga) ni teal (es de lo
              compartido) -- positivo va `hoja`, negativo `terracotaProfunda`.
            */}
            <View style={estilos.filaTiles}>
              <View style={estilos.tile}>
                <Text style={estilos.tileEtiqueta}>Gastaste este mes</Text>
                <Monto valor={resumen.total} tamano={22} />
              </View>
              <Pressable
                onPress={() => router.push('/mi-plata')}
                accessibilityRole="button"
                style={({ pressed }) => [
                  estilos.tile,
                  miPlataEnRojo && estilos.tileEnRojo,
                  pressed && estilos.filaPresionada,
                ]}
              >
                <Text style={[estilos.tileEtiqueta, miPlataEnRojo && estilos.tileEtiquetaEnRojo]}>
                  Mi Plata
                </Text>
                {cargandoBalance && !balance ? (
                  <Text style={estilos.tileMonto}>···</Text>
                ) : balance && balance.ingresos.length > 0 ? (
                  <Text
                    style={[estilos.tileMonto, miPlataEnRojo && estilos.tileMontoNegativo]}
                    numberOfLines={1}
                    adjustsFontSizeToFit
                  >
                    {formatearMonto(Math.abs(balance.restante))}
                  </Text>
                ) : (
                  <Text style={estilos.tileAgregar}>Agregar</Text>
                )}
              </Pressable>
            </View>

            <View style={estilos.tarjetaHormiga}>
              <Nutria animo={resumen.animo} tamano={140} />
              {/*
                El numero grande sigue siendo el TOTAL HORMIGA. Dejo de ser el
                UNICO numero de la pantalla (ver el comentario de arriba del
                componente), pero sigue siendo EL numero de esta tarjeta: la
                marca central del producto no se diluye por compartir espacio.
              */}
              <Text style={estilos.rotulo}>Gasto hormiga</Text>
              <Text
                style={estilos.numeroGrande}
                // El bug de que el numero se partia en dos lineas cuando no
                // entraba. `adjustsFontSizeToFit` lo achica hasta que entre, que
                // es preferible a un total hormiga cortado al medio -- justo el
                // numero que la app existe para mostrar.
                numberOfLines={1}
                adjustsFontSizeToFit
              >
                {formatearMonto(resumen.totalHormiga)}
              </Text>
              <Text style={estilos.comparacion}>{fraseDelAnimo(resumen.animo, resumen.totalHormiga, esElMesActual)}</Text>
              <Text style={estilos.detalleComparacion}>
                Mismo tramo del mes pasado: {formatearMonto(resumen.totalHormigaMesAnterior)}
              </Text>
            </View>

            {resumen.porCategoria.length > 0 ? (
              <View style={estilos.categorias}>
                <Text style={estilos.rotuloSeccion}>Por categoría</Text>
                {resumen.porCategoria.map((c) => (
                  <View key={c.categoriaId} style={estilos.categoria}>
                    <IconoCategoria nombre={c.icono} />
                    <Text style={estilos.nombreCategoria}>{nombreDeCategoria(c.nombre)}</Text>
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
              <Text style={estilos.vacio}>{esElMesActual ? 'Todavía no cargaste nada este mes.' : 'No cargaste nada ese mes.'}</Text>
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
  encabezadoTexto: { flex: 1 },
  // Un solo icono ahora (seccion 2.3c): mismo tamano circular que ya tenia,
  // sin la pastilla de "Ajustes" al lado.
  iconoMenu: {
    width: 36,
    height: 36,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colores.borde,
    backgroundColor: colores.tarjeta,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastillaPresionada: { backgroundColor: colores.arena },

  filaTiles: { flexDirection: 'row', gap: 12 },
  tile: {
    flex: 1,
    backgroundColor: colores.tarjeta,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  tileEtiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.1,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  tileMonto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 22,
    // Nunca ambar (es del gasto hormiga) ni teal (es de lo compartido):
    // "Mi Plata" es individual. Verde de "buenas noticias" cuando alcanza.
    color: colores.hoja,
    marginTop: 6,
    ...numerosTabulares,
  },
  tileMontoNegativo: { color: colores.terracotaProfunda },
  // Mismo tratamiento que la tarjeta de mi-plata.tsx cuando el restante es
  // negativo (seccion 2.3c): fondo tintado, no solo texto en rojo.
  tileEnRojo: { backgroundColor: colores.terracotaSuave, borderColor: colores.terracotaProfunda },
  tileEtiquetaEnRojo: { color: colores.terracotaProfunda },
  tileAgregar: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.rioProfundo,
    marginTop: 6,
  },

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

  filaPresionada: { backgroundColor: colores.arena },

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
