import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  ActionSheetIOS,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { categoriasConRespaldo } from '../src/almacenamiento/catalogo';
import { ErrorDeApi } from '../src/api/cliente';
import type { CategoriaRespuesta } from '../src/api/tipos';
import { Boton } from '../src/componentes/Boton';
import { IconoCategoria } from '../src/componentes/IconoCategoria';
import { nombreDeCategoria } from '../src/componentes/nombreDeCategoria';
import {
  borrarCategoria,
  crearCategoria,
  editarCategoria,
  listarCategorias,
} from '../src/features/categorias/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * Tus categorias (seccion 2.9): crear, editar y borrar.
 *
 * VUELVE UNA PANTALLA QUE LA SECCION 2.7 HABIA SACADO, y no es dar marcha
 * atras. Lo que molestaba de aquella era que fuera la UNICA forma de crear una
 * categoria -- habia que salir del formulario de gasto para ir a darla de alta
 * -- y eso no vuelve: el "+ Agregar" del formulario sigue siendo el camino de
 * todos los dias. Esta pantalla existe para lo que el formulario no puede
 * hacer sin estorbar: EDITAR y BORRAR. Son correcciones deliberadas, que se
 * hacen sentado, y meterlas en la pantalla que se usa parada en el mostrador
 * seria poner acciones destructivas al lado del boton mas apurado de la app.
 *
 * SON TUYAS: lo que hagas aca no le cambia la lista a la otra persona del
 * grupo. Por eso la pantalla vive en "Tu cuenta" del menu y no depende de
 * tener pareja.
 *
 * Mismo molde que `mi-plata.tsx`: tocar una fila abre el menu nativo
 * (Editar / Borrar), y editar convierte la fila en un formulario en el lugar.
 */
export default function Categorias() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [categorias, setCategorias] = useState<CategoriaRespuesta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // El alta, arriba.
  const [nombreNuevo, setNombreNuevo] = useState('');
  const [iconoNuevo, setIconoNuevo] = useState('');

  // La fila que se esta editando, y lo tipeado en ella.
  const [editando, setEditando] = useState<CategoriaRespuesta | null>(null);
  const [nombreEditado, setNombreEditado] = useState('');
  const [iconoEditado, setIconoEditado] = useState('');

  const recargar = useCallback(async () => {
    try {
      const frescas = await listarCategorias();
      setCategorias(frescas);
      setError(null);
      // Se pisa tambien la copia en disco que usa el formulario de gasto sin
      // señal. Si no, despues de borrar una categoria aca, el formulario
      // offline la seguiria ofreciendo y ese gasto volveria rechazado.
      void categoriasConRespaldo(async () => frescas);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron traer tus categorías.');
      setCategorias((actuales) => actuales ?? []);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (categorias === null) return <Cargando />;

  /**
   * Corre una escritura y despues vuelve a pedir la lista entera, en vez de
   * retocar el estado a mano: el orden (alfabetico) y los nombres los decide el
   * backend, y son pocas filas.
   */
  async function ejecutar(accion: () => Promise<unknown>, mensajeDeError: string): Promise<boolean> {
    setError(null);
    setEnviando(true);
    try {
      await accion();
      await recargar();
      return true;
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : mensajeDeError);
      return false;
    } finally {
      setEnviando(false);
    }
  }

  async function agregar() {
    const ok = await ejecutar(
      () => crearCategoria(nombreNuevo.trim(), iconoNuevo.trim()),
      'No se pudo crear la categoría.',
    );
    if (ok) {
      setNombreNuevo('');
      setIconoNuevo('');
    }
  }

  function empezarAEditar(categoria: CategoriaRespuesta) {
    setError(null);
    setEditando(categoria);
    setNombreEditado(nombreDeCategoria(categoria.nombre));
    // Vacio a proposito: las categorias mas viejas guardan el NOMBRE de un
    // icono ("coffee"), no un emoji, y mostrar ese texto en un campo de emoji
    // confunde. Si queda vacio se conserva el icono que ya tenia.
    setIconoEditado('');
  }

  function cancelarEdicion() {
    setError(null);
    setEditando(null);
  }

  async function guardarEdicion() {
    if (!editando) return;
    const ok = await ejecutar(
      () =>
        editarCategoria(editando.id, nombreEditado.trim(), iconoEditado.trim() || editando.icono),
      'No se pudo guardar la categoría.',
    );
    if (ok) setEditando(null);
  }

  function tocarFila(categoria: CategoriaRespuesta) {
    ActionSheetIOS.showActionSheetWithOptions(
      {
        title: nombreDeCategoria(categoria.nombre),
        options: ['Editar', 'Borrar', 'Cancelar'],
        destructiveButtonIndex: 1,
        cancelButtonIndex: 2,
      },
      (indice) => {
        if (indice === 0) empezarAEditar(categoria);
        else if (indice === 1) confirmarBorrado(categoria);
      },
    );
  }

  function confirmarBorrado(categoria: CategoriaRespuesta) {
    Alert.alert(
      `Borrar "${nombreDeCategoria(categoria.nombre)}"`,
      // Lo que mas tranquiliza antes de borrar: que no se pierde ningun gasto.
      'Deja de aparecer al cargar un gasto. Los gastos que ya cargaste con ella no cambian.',
      [
        { text: 'Dejarla', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () =>
            void ejecutar(() => borrarCategoria(categoria.id), 'No se pudo borrar la categoría.'),
        },
      ],
    );
  }

  const altaLista = nombreNuevo.trim() !== '' && iconoNuevo.trim() !== '';
  const edicionLista = nombreEditado.trim() !== '' && !enviando;

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
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Tu cuenta</Text>
            <Text style={estilos.titulo}>Tus categorías</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Menú</Text>
          </Pressable>
        </View>

        {/*
          Oculto mientras se edita una fila, igual que en Mi Plata: dos
          formularios abiertos a la vez no dejan claro cual guarda que.
        */}
        {!editando ? (
          <View style={estilos.bloque}>
            <Text style={estilos.rotuloSeccion}>Agregar una categoría</Text>
            <View style={estilos.filaDeCampos}>
              <TextInput
                value={iconoNuevo}
                onChangeText={setIconoNuevo}
                placeholder="🏷️"
                placeholderTextColor={colores.borde}
                // Un emoji puede ocupar varias unidades UTF-16; ver el mismo
                // campo en FormularioDeGasto.
                maxLength={8}
                autoCapitalize="none"
                autoCorrect={false}
                style={estilos.inputIcono}
                accessibilityLabel="Icono: tocá el globo del teclado para elegir un emoji"
              />
              <TextInput
                value={nombreNuevo}
                onChangeText={setNombreNuevo}
                placeholder="Nombre"
                placeholderTextColor={colores.textoSuave}
                maxLength={40}
                style={estilos.inputNombre}
              />
            </View>
            <Boton
              titulo="Agregar categoría"
              onPress={() => void agregar()}
              cargando={enviando}
              deshabilitado={!altaLista}
            />
          </View>
        ) : null}

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>Las que tenés</Text>
          <Text style={estilos.ayuda}>
            Tocá una para editarla o borrarla. Son solo tuyas: no cambian las de nadie más.
          </Text>

          {categorias.length === 0 ? (
            <Text style={estilos.ayuda}>Todavía no tenés ninguna.</Text>
          ) : null}

          {categorias.map((categoria) =>
            editando?.id === categoria.id ? (
              <View key={categoria.id} style={estilos.filaEditando}>
                <View style={estilos.filaDeCampos}>
                  <TextInput
                    value={iconoEditado}
                    onChangeText={setIconoEditado}
                    // El placeholder es el icono actual cuando es un emoji; si
                    // es un icono viejo de Lucide, una etiqueta generica.
                    placeholder={/^[a-z-]+$/.test(categoria.icono) ? '🏷️' : categoria.icono}
                    placeholderTextColor={colores.borde}
                    maxLength={8}
                    autoCapitalize="none"
                    autoCorrect={false}
                    style={estilos.inputIcono}
                    accessibilityLabel="Icono nuevo. Dejalo vacío para conservar el que tiene"
                  />
                  <TextInput
                    value={nombreEditado}
                    onChangeText={setNombreEditado}
                    autoFocus
                    maxLength={40}
                    style={estilos.inputNombre}
                  />
                </View>
                <Text style={estilos.ayuda}>
                  El cambio también se ve en los gastos que ya cargaste con ella.
                </Text>
                <View style={estilos.accionesInline}>
                  <Pressable onPress={cancelarEdicion} accessibilityRole="button" hitSlop={8}>
                    <Text style={estilos.cancelarInline}>Cancelar</Text>
                  </Pressable>
                  <Pressable
                    onPress={() => void guardarEdicion()}
                    disabled={!edicionLista}
                    accessibilityRole="button"
                    hitSlop={8}
                  >
                    <Text style={[estilos.guardarInline, !edicionLista && { opacity: 0.4 }]}>
                      Guardar
                    </Text>
                  </Pressable>
                </View>
              </View>
            ) : (
              <Pressable
                key={categoria.id}
                onPress={() => tocarFila(categoria)}
                accessibilityRole="button"
                accessibilityLabel={`${nombreDeCategoria(categoria.nombre)}, tocar para editar o borrar`}
                style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
              >
                <IconoCategoria nombre={categoria.icono} tamano={20} />
                <Text style={estilos.filaTexto}>{nombreDeCategoria(categoria.nombre)}</Text>
              </Pressable>
            ),
          )}
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

  bloque: { gap: 8 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },

  filaDeCampos: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  inputIcono: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    width: 56,
    minHeight: 48,
    textAlign: 'center',
    fontSize: 20,
  },
  inputNombre: {
    flex: 1,
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    paddingHorizontal: 14,
    minHeight: 48,
    fontFamily: fuentes.cuerpo,
    fontSize: 16,
    color: colores.texto,
  },

  fila: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 18,
    paddingVertical: 14,
  },
  filaPresionada: { backgroundColor: colores.arena },
  filaTexto: { flex: 1, fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.texto },

  filaEditando: {
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: colores.rioProfundo,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  accionesInline: { flexDirection: 'row', justifyContent: 'flex-end', gap: 24 },
  cancelarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.textoSuave },
  guardarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
