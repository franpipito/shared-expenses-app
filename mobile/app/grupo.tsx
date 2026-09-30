import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Share, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../src/api/cliente';
import { Boton } from '../src/componentes/Boton';
import { Campo } from '../src/features/auth/componentes/Campo';
import { invitar, sumarse } from '../src/features/grupo/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

type Modo = 'invitar' | 'sumarse';

/**
 * Sumarse a un grupo, o invitar a alguien al propio (v1.1, sección 2.1).
 *
 * Solo se llega acá estando sola: Ajustes ya decide eso antes de mostrar el
 * link, así que esta pantalla no vuelve a chequear `tienePareja` -- si de
 * todos modos algo cambió entre medio, el backend lo rechaza igual y el error
 * se muestra como cualquier otro.
 *
 * Dos modos en una sola pantalla y no dos pantallas separadas, siguiendo el
 * mismo espíritu que la vaquita (`app/vaquita.tsx`): el estado depende de lo
 * que la persona elige, no de una navegación nueva por cada camino.
 *
 * `docs/diseno.md` permite un solo botón terracota por pantalla: por eso
 * "Compartir" (después de generar el código) es un botón secundario, no un
 * segundo `Boton`.
 */
export default function Grupo() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [modo, setModo] = useState<Modo>('invitar');

  const [codigoGenerado, setCodigoGenerado] = useState<string | null>(null);
  const [generando, setGenerando] = useState(false);
  const [errorInvitar, setErrorInvitar] = useState<string | null>(null);

  async function generar() {
    setErrorInvitar(null);
    setGenerando(true);
    try {
      const respuesta = await invitar();
      setCodigoGenerado(respuesta.codigo);
    } catch (e) {
      setErrorInvitar(e instanceof ErrorDeApi ? e.message : 'No se pudo generar el código. Probá de nuevo.');
    } finally {
      setGenerando(false);
    }
  }

  function compartir() {
    if (!codigoGenerado) return;
    // Share.share es de react-native, no de Expo: ya la tiene cualquier app
    // RN, sin agregar una dependencia nueva. En iOS, la hoja de compartir del
    // sistema ya incluye "Copiar", así que no hace falta un botón aparte para
    // eso.
    void Share.share({
      message: `Sumate a MiNutria conmigo. Abrí la app, Ajustes → "Ya tengo un código", y poné: ${codigoGenerado}`,
    });
  }

  const [codigo, setCodigo] = useState('');
  const [sumando, setSumando] = useState(false);
  const [errorSumarse, setErrorSumarse] = useState<string | null>(null);

  async function sumarme() {
    setErrorSumarse(null);
    setSumando(true);
    try {
      await sumarse(codigo.trim());
      // El grupo ya quedo sumado del lado del backend. No hay nada que
      // guardar en la sesion (usuario solo lleva id y nombre, nunca grupoId:
      // ver sesion.tsx), asi que alcanza con volver -- resumen, lista y saldo
      // piden el grupo fresco la proxima vez que se abren.
      router.back();
    } catch (e) {
      setErrorSumarse(e instanceof ErrorDeApi ? e.message : 'No se pudo sumar con ese código.');
      setSumando(false);
    }
  }

  return (
    <View style={estilos.pantalla}>
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Tu cuenta</Text>
            <Text style={estilos.titulo}>Compartir la app</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Cerrar</Text>
          </Pressable>
        </View>

        <View style={estilos.tabs}>
          <Pressable
            onPress={() => setModo('invitar')}
            accessibilityRole="button"
            accessibilityState={{ selected: modo === 'invitar' }}
            style={[estilos.tab, modo === 'invitar' && estilos.tabActivo]}
          >
            <Text style={[estilos.tabTexto, modo === 'invitar' && estilos.tabTextoActivo]}>Invitar</Text>
          </Pressable>
          <Pressable
            onPress={() => setModo('sumarse')}
            accessibilityRole="button"
            accessibilityState={{ selected: modo === 'sumarse' }}
            style={[estilos.tab, modo === 'sumarse' && estilos.tabActivo]}
          >
            <Text style={[estilos.tabTexto, modo === 'sumarse' && estilos.tabTextoActivo]}>
              Ya tengo un código
            </Text>
          </Pressable>
        </View>

        {modo === 'invitar' ? (
          codigoGenerado ? (
            <View style={estilos.tarjeta}>
              <Text style={estilos.rotulo}>Tu código</Text>
              <Text style={estilos.codigo}>{codigoGenerado}</Text>
              <Text style={estilos.punto}>
                Vence en 7 días, y sirve una sola vez. Compartíselo a quien quieras sumar.
              </Text>
              <Pressable onPress={compartir} accessibilityRole="button" style={estilos.secundario}>
                <Text style={estilos.secundarioTexto}>Compartir</Text>
              </Pressable>
            </View>
          ) : (
            <>
              <View style={estilos.tarjeta}>
                <Text style={estilos.punto}>
                  Generá un código y compartíselo a la persona con la que querés cargar gastos
                  compartidos. Desde ese momento van a ver la sección de pareja los dos.
                </Text>
              </View>
              {errorInvitar ? <Text style={estilos.error}>{errorInvitar}</Text> : null}
              <Boton titulo="Generar código" onPress={() => void generar()} cargando={generando} />
            </>
          )
        ) : (
          <>
            <Campo
              etiqueta="Código de invitación"
              valor={codigo}
              alCambiar={setCodigo}
              autoComplete="one-time-code"
              ayuda="El que te compartió tu pareja."
            />
            {errorSumarse ? <Text style={estilos.error}>{errorSumarse}</Text> : null}
            <Boton
              titulo="Sumarme"
              onPress={() => void sumarme()}
              cargando={sumando}
              deshabilitado={codigo.trim().length === 0}
            />
          </>
        )}
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

  tabs: { flexDirection: 'row', gap: 8 },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colores.borde,
    backgroundColor: colores.tarjeta,
  },
  tabActivo: { backgroundColor: colores.rioProfundo, borderColor: colores.rioProfundo },
  tabTexto: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo },
  tabTextoActivo: { color: colores.tarjeta },

  tarjeta: {
    backgroundColor: colores.tarjeta,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colores.borde,
    padding: 20,
    gap: 10,
  },
  rotulo: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  codigo: {
    fontFamily: fuentes.cuerpoBold,
    fontSize: 30,
    letterSpacing: 4,
    color: colores.texto,
    textAlign: 'center',
    paddingVertical: 8,
  },
  punto: { fontFamily: fuentes.cuerpo, fontSize: 15, lineHeight: 21, color: colores.texto },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },

  secundario: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colores.rioProfundo,
  },
  secundarioTexto: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },
});
