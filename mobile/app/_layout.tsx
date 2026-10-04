import {
  Fraunces_400Regular,
  Fraunces_600SemiBold,
  Fraunces_700Bold,
} from '@expo-google-fonts/fraunces';
import {
  NunitoSans_400Regular,
  NunitoSans_600SemiBold,
  NunitoSans_700Bold,
} from '@expo-google-fonts/nunito-sans';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { SesionProvider } from '../src/features/auth/sesion';
import { MesProvider } from '../src/features/mes/mes';
import { colores } from '../src/tema/colores';

/**
 * El layout raiz de expo-router.
 *
 * expo-router usa **ruteo por archivos**: cada archivo de `app/` es una ruta, y
 * `_layout.tsx` es lo que envuelve a todas las de su carpeta. Es la misma idea
 * que Next.js. La ventaja sobre React Navigation escrito a mano es que la
 * estructura de carpetas ES el mapa de navegacion, asi que no hay un archivo de
 * rutas que se desincronice de las pantallas.
 *
 * Todo lo que tiene que existir antes que cualquier pantalla vive aca: las
 * fuentes y la sesion.
 */
/**
 * La splash (la nutria contenta sobre el crema, configurada en app.json) se
 * queda puesta hasta que cargan las fuentes. Si se ocultara sola, entre la
 * nutria y la primera pantalla aparecia un crema con una ruedita: un paso de mas
 * en lo primero que ve cualquiera que abre la app.
 *
 * Se llama a nivel de modulo, antes del primer render, que es lo que pide la
 * documentacion de expo-splash-screen.
 */
void SplashScreen.preventAutoHideAsync();

export default function LayoutRaiz() {
  const [fuentesListas, errorDeFuentes] = useFonts({
    Fraunces_400Regular,
    Fraunces_600SemiBold,
    Fraunces_700Bold,
    NunitoSans_400Regular,
    NunitoSans_600SemiBold,
    NunitoSans_700Bold,
  });

  // Sin esto la app se ve un instante con la fuente del sistema y despues salta
  // a Fraunces. En una app cuya mitad de la identidad es la tipografia, ese
  // salto se nota.
  // Se oculta tambien si las fuentes FALLARON, no solo si cargaron. Esperando
  // solo el exito, un error de carga dejaba la splash puesta para siempre: la
  // app no abriria nunca, sin ningun mensaje. Mejor abrir con la fuente del
  // sistema que no abrir.
  const listo = fuentesListas || errorDeFuentes != null;
  useEffect(() => {
    if (listo) SplashScreen.hide();
  }, [listo]);

  if (!listo) return <Cargando />;

  return (
    <SafeAreaProvider>
      <SesionProvider>
        <MesProvider>
        <StatusBar style="dark" />
        <Stack
          screenOptions={{
            headerShown: false,
            // El fondo de la animacion entre pantallas. Sin esto es blanco y se
            // ve un flash sobre el crema.
            contentStyle: { backgroundColor: colores.fondo },
          }}
        >
          <Stack.Screen name="index" />
          <Stack.Screen name="login" />
          <Stack.Screen name="registro" />
          <Stack.Screen name="onboarding-categorias" />
          <Stack.Screen name="recuperar" />
          <Stack.Screen name="resumen" />
          <Stack.Screen name="gastos" />
          <Stack.Screen name="saldo" />
          <Stack.Screen name="vaquita" />
          <Stack.Screen name="mi-plata" />
          <Stack.Screen name="menu" />
          <Stack.Screen name="borrar-cuenta" />
          <Stack.Screen name="viaje/[id]" />
          <Stack.Screen
            name="gasto/nuevo"
            options={{
              // Modal y no push: cargar un gasto es una tarea que se abre, se
              // termina y se cierra, no un lugar al que se navega.
              presentation: 'modal',
            }}
          />
          {/* Editar es la misma tarea que cargar, asi que se abre igual.

              La ruta es `gasto/[id]` y NO `gasto/editar?id=`, aunque el segmento
              dinamico conviva con la ruta estatica `gasto/nuevo`. No compiten:
              el comparador de expo-router (`sortRoutes`) ordena las estaticas
              ANTES que las dinamicas, asi que /gasto/nuevo siempre resuelve al
              alta y "nuevo" nunca se toma como un id. Verificado leyendo
              `expo-router/build/sortRoutes.js`, no asumido. */}
          <Stack.Screen name="gasto/[id]" options={{ presentation: 'modal' }} />
        </Stack>
        </MesProvider>
      </SesionProvider>
    </SafeAreaProvider>
  );
}

export function Cargando() {
  return (
    <View
      style={{
        flex: 1,
        backgroundColor: colores.fondo,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <ActivityIndicator color={colores.rio} size="large" />
    </View>
  );
}
