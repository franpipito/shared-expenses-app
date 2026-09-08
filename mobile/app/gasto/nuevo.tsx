import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../../src/api/cliente';
import type { CategoriaRespuesta } from '../../src/api/tipos';
import { Boton } from '../../src/componentes/Boton';
import { crearGasto, hoyLocal, traerCategorias } from '../../src/features/gastos/api';
import { colores } from '../../src/tema/colores';
import { fuentes, numerosTabulares } from '../../src/tema/tipografia';

/**
 * Cargar un gasto.
 *
 * Este formulario es donde se juega el proyecto entero. La usuaria abandono un
 * intento anterior porque anotar era incomodo, y dijo que prefiere olvidarse un
 * gasto antes que anotar lento. Por eso: **categoria, monto, descripcion, y el
 * toggle de hormiga.** Nada mas.
 *
 * Cada campo que se le agregue se paga en abandono. La fecha no se pregunta (es
 * hoy), el tipo tampoco por ahora (es PERSONAL), y el reparto personalizado
 * aparece cuando exista la seccion de pareja.
 */
export default function NuevoGasto() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [categorias, setCategorias] = useState<CategoriaRespuesta[]>([]);
  const [categoriaId, setCategoriaId] = useState<string | null>(null);
  const [monto, setMonto] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [esHormiga, setEsHormiga] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        setCategorias(await traerCategorias());
      } catch (e) {
        setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron traer las categorias.');
      }
    })();
  }, []);

  /**
   * El monto se escribe con coma (es como se escribe en Argentina) y viaja con
   * punto, que es lo unico que entiende JSON. Es la unica transformacion de
   * plata que hace la app: la aritmetica sigue siendo toda del backend.
   */
  const montoNumero = Number(monto.replace(',', '.'));
  const montoValido = monto.trim() !== '' && Number.isFinite(montoNumero) && montoNumero > 0;
  const listo = montoValido && categoriaId !== null && descripcion.trim() !== '';

  async function guardar() {
    setError(null);
    setEnviando(true);
    try {
      await crearGasto({
        monto: montoNumero,
        categoriaId: categoriaId!,
        fecha: hoyLocal(),
        descripcion: descripcion.trim(),
        tipo: 'PERSONAL',
        esHormiga,
      });
      // `back` y no `replace`: esto es un modal que se cierra. El resumen que
      // queda abajo se recarga solo, porque escucha el foco.
      router.back();
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo guardar el gasto.');
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
        contentContainerStyle={[estilos.contenido, { paddingTop: 24 }]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <Text style={estilos.titulo}>Nuevo gasto</Text>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.cancelar}>Cancelar</Text>
          </Pressable>
        </View>

        <View style={estilos.bloque}>
          <Text style={estilos.etiqueta}>Monto</Text>
          <TextInput
            value={monto}
            onChangeText={setMonto}
            // `decimal-pad` y no `numeric`: no ofrece signos ni exponentes, que
            // en un monto no tienen sentido. En iOS no trae tecla de "listo",
            // pero el boton de guardar esta siempre visible abajo.
            keyboardType="decimal-pad"
            placeholder="0,00"
            placeholderTextColor={colores.borde}
            // Es el primer campo y el que mas se tipea: abre con el foco puesto.
            autoFocus
            style={estilos.inputMonto}
          />
        </View>

        <View style={estilos.bloque}>
          <Text style={estilos.etiqueta}>Categoria</Text>
          <View style={estilos.chips}>
            {categorias.map((c) => {
              const elegida = c.id === categoriaId;
              return (
                <Pressable
                  key={c.id}
                  onPress={() => setCategoriaId(c.id)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: elegida }}
                  style={[estilos.chip, elegida && estilos.chipElegido]}
                >
                  <Text style={estilos.chipIcono}>{c.icono}</Text>
                  <Text style={[estilos.chipTexto, elegida && estilos.chipTextoElegido]}>
                    {c.nombre}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View style={estilos.bloque}>
          <Text style={estilos.etiqueta}>Descripcion</Text>
          <TextInput
            value={descripcion}
            onChangeText={setDescripcion}
            placeholder="Algo que te recuerde el momento"
            placeholderTextColor={colores.textoSuave}
            maxLength={255}
            style={estilos.input}
          />
        </View>

        {/*
          El toggle de hormiga. Es un switch y no un selector de etiquetas porque
          la usuaria pidio UN total, y elegir de una lista es mas lento que tocar
          un switch, lo que choca de frente con el requisito de velocidad.

          El ambar aparece aca y en el numero grande del resumen. En ningun otro
          lado.
        */}
        <View style={[estilos.hormiga, esHormiga && estilos.hormigaActiva]}>
          <View style={estilos.hormigaTexto}>
            <Text style={estilos.hormigaTitulo}>Fue un gasto evitable</Text>
            <Text style={estilos.hormigaBajada}>
              Mirandolo en frio, podria no haberlo hecho
            </Text>
          </View>
          <Switch
            value={esHormiga}
            onValueChange={setEsHormiga}
            trackColor={{ false: colores.borde, true: colores.hormiga }}
            thumbColor={colores.tarjeta}
            ios_backgroundColor={colores.borde}
          />
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}
      </ScrollView>

      <View style={[estilos.pie, { paddingBottom: insets.bottom + 12 }]}>
        <Boton titulo="Guardar" onPress={guardar} cargando={enviando} deshabilitado={!listo} />
      </View>
    </KeyboardAvoidingView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20, paddingBottom: 24, gap: 22 },
  encabezado: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 24, color: colores.texto },
  cancelar: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },

  bloque: { gap: 8 },
  etiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  inputMonto: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 14,
    paddingHorizontal: 18,
    minHeight: 68,
    fontFamily: fuentes.displayBold,
    fontSize: 32,
    color: colores.texto,
    ...numerosTabulares,
  },
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

  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 999,
    paddingHorizontal: 14,
    // 44 de alto: es el minimo tactil de iOS, y estos chips se tocan parada.
    minHeight: 44,
  },
  // La categoria elegida se marca con teal, no con ambar.
  chipElegido: { backgroundColor: colores.rioSuave, borderColor: colores.rio },
  chipIcono: { fontSize: 16 },
  chipTexto: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  chipTextoElegido: { fontFamily: fuentes.cuerpoSemi, color: colores.rioProfundo },

  hormiga: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  hormigaActiva: { backgroundColor: colores.hormigaSuave, borderColor: colores.hormiga },
  hormigaTexto: { flex: 1 },
  hormigaTitulo: { fontFamily: fuentes.cuerpoSemi, fontSize: 16, color: colores.texto },
  hormigaBajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 13,
    color: colores.textoSuave,
    marginTop: 2,
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
