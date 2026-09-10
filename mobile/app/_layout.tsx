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
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { SesionProvider } from '../src/features/auth/sesion';
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
export default function LayoutRaiz() {
  const [fuentesListas] = useFonts({
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
  if (!fuentesListas) return <Cargando />;

  return (
    <SafeAreaProvider>
      <SesionProvider>
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
          <Stack.Screen name="resumen" />
          <Stack.Screen name="gastos" />
          <Stack.Screen name="saldo" />
          <Stack.Screen
            name="gasto/nuevo"
            options={{
              // Modal y no push: cargar un gasto es una tarea que se abre, se
              // termina y se cierra, no un lugar al que se navega.
              presentation: 'modal',
            }}
          />
        </Stack>
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
