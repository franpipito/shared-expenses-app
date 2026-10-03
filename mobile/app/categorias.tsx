import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../src/api/cliente';
import type { CategoriaRespuesta } from '../src/api/tipos';
import { Boton } from '../src/componentes/Boton';
import { IconoCategoria, ICONOS_PARA_ELEGIR } from '../src/componentes/IconoCategoria';
import { nombreDeCategoria } from '../src/componentes/nombreDeCategoria';
import { borrarCategoria, crearCategoria, listarCategorias } from '../src/features/categorias/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * Categorias 100% personalizables (v1.1, seccion 2.5): agregar y borrar
 * cualquiera, inclusive las seis que siembra el backend por default.
 *
 * NO HAY "EDITAR". Franco pidio agregar y eliminar, nada mas -- y a
 * diferencia de un ingreso o un aporte, una categoria no tiene un "monto mal
 * tipeado" que corregir: si el nombre quedo mal, se borra y se crea de
 * nuevo. Por eso cada fila es un solo gesto (tocar para borrar, con
 * confirmacion) y no un `ActionSheetIOS` con un unico boton, que seria
 * mostrar un menu para una sola opcion.
 *
 * BORRAR UNA CATEGORIA EN USO NO ROMPE NADA YA CARGADO: `Gasto.categoria` es
 * un snapshot embebido, asi que un gasto viejo sigue mostrando su nombre e
 * icono aunque la categoria en si ya no exista. Lo unico que el backend
 * protege es no dejar el grupo sin NINGUNA -- sin eso, el formulario de alta
 * se queda sin un chip que tocar.
 */
export default function Categorias() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [categorias, setCategorias] = useState<CategoriaRespuesta[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [nombre, setNombre] = useState('');
  const [icono, setIcono] = useState<string>(ICONOS_PARA_ELEGIR[0]);
  const [errorAlta, setErrorAlta] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const cargar = useCallback(async () => {
    setError(null);
    setCargando(true);
    try {
      setCategorias(await listarCategorias());
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron traer las categorías.');
    } finally {
      setCargando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void cargar();
    }, [cargar]),
  );

  if (cargando && categorias === null) return <Cargando />;

  const nombreValido = nombre.trim() !== '';

  function confirmarBorrado(categoria: CategoriaRespuesta) {
    Alert.alert(
      'Borrar esta categoría',
      `Los gastos que ya usaron "${nombreDeCategoria(categoria.nombre)}" no se tocan: siguen mostrándola igual. Solo deja de poder elegirse para gastos nuevos.`,
      [
        { text: 'Dejarla', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              setError(null);
              try {
                await borrarCategoria(categoria.id);
                await cargar();
              } catch (e) {
                setError(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar la categoría.');
              }
            })();
          },
        },
      ],
    );
  }

  async function confirmarAlta() {
    setErrorAlta(null);
    setEnviando(true);
    try {
      await crearCategoria(nombre.trim(), icono);
      setNombre('');
      setIcono(ICONOS_PARA_ELEGIR[0]);
      await cargar();
    } catch (e) {
      setErrorAlta(e instanceof ErrorDeApi ? e.message : 'No se pudo crear la categoría.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={estilos.pantalla}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl refreshing={cargando} onRefresh={cargar} tintColor={colores.rio} />
        }
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Personalizar</Text>
            <Text style={estilos.titulo}>Categorías</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Menú</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>Agregar una categoría</Text>
          <TextInput
            value={nombre}
            onChangeText={setNombre}
            placeholder="Netflix, Mascotas, Gimnasio..."
            placeholderTextColor={colores.textoSuave}
            maxLength={40}
            style={estilos.input}
          />
          <View style={estilos.grillaIconos}>
            {ICONOS_PARA_ELEGIR.map((nombreIcono) => {
              const elegido = nombreIcono === icono;
              return (
                <Pressable
                  key={nombreIcono}
                  onPress={() => setIcono(nombreIcono)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: elegido }}
                  style={[estilos.casillaIcono, elegido && estilos.casillaIconoElegida]}
                >
                  <IconoCategoria
                    nombre={nombreIcono}
                    tamano={22}
                    color={elegido ? colores.rioProfundo : colores.corteza}
                  />
                </Pressable>
              );
            })}
          </View>
          {errorAlta ? <Text style={estilos.error}>{errorAlta}</Text> : null}
          <View style={estilos.accion}>
            <Boton
              titulo="Crear categoría"
              onPress={() => void confirmarAlta()}
              cargando={enviando}
              deshabilitado={!nombreValido}
            />
          </View>
        </View>

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>Tus categorías</Text>
          <Text style={estilos.ayuda}>Tocá una para borrarla. Podés borrar cualquiera, inclusive las que ya tenías.</Text>
          {(categorias ?? []).map((c) => (
            <Pressable
              key={c.id}
              onPress={() => confirmarBorrado(c)}
              accessibilityRole="button"
              accessibilityLabel={`${nombreDeCategoria(c.nombre)}, tocar para borrar`}
              style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
            >
              <View style={estilos.filaIzquierda}>
                <IconoCategoria nombre={c.icono} tamano={18} color={colores.corteza} />
                <Text style={estilos.filaEtiqueta}>{nombreDeCategoria(c.nombre)}</Text>
              </View>
              <Text style={estilos.filaBorrar}>Borrar</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
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

  bloque: { gap: 10 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },

  input: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    paddingHorizontal: 16,
    minHeight: 52,
    fontFamily: fuentes.cuerpo,
    fontSize: 17,
    color: colores.texto,
  },

  grillaIconos: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  casillaIcono: {
    width: 44,
    height: 44,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colores.borde,
    backgroundColor: colores.tarjeta,
    alignItems: 'center',
    justifyContent: 'center',
  },
  casillaIconoElegida: { backgroundColor: colores.rioSuave, borderColor: colores.rio },

  accion: { alignSelf: 'stretch', marginTop: 4 },

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
  filaIzquierda: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  filaBorrar: { fontFamily: fuentes.cuerpoSemi, fontSize: 13, color: colores.terracotaProfunda },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
