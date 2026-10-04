import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
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

import { ErrorDeApi } from '../src/api/cliente';
import { Boton } from '../src/componentes/Boton';
import { IconoCategoria } from '../src/componentes/IconoCategoria';
import { nombreDeCategoria } from '../src/componentes/nombreDeCategoria';
import { crearCategoria } from '../src/features/categorias/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * El mini-onboarding de categorías (v1.1, sección 2.7).
 *
 * Hasta esta sección, un grupo nuevo salía sembrado con las seis categorías
 * de la entrevista (café, uber, comida, ropa, regalos, otros). Franco pidió
 * sacar ese sembrado por completo: ninguna categoría es default, y quien se
 * registra elige las suyas. Pero un grupo sin NINGUNA categoría deja
 * `FormularioDeGasto` sin un chip para tocar, y ahí el botón Guardar nunca
 * se habilita -- el mismo problema que ya resolvió `catalogo.ts` para el
 * caso offline, ahora para el caso "recién creado". Esta pantalla es la
 * respuesta: se muestra UNA vez, justo después de `registro.tsx`, con las
 * seis de siempre ya marcadas como punto de partida (confirmado con Franco:
 * "sugeridas y preseleccionadas" en vez de arrancar en blanco o sin marcar
 * ninguna) para que tocar "Continuar" sin cambiar nada siga siendo el camino
 * de un solo tap.
 *
 * NO HAY VUELTA ATRÁS NI "SALTAR": o se continúa con lo preseleccionado, o se
 * ajusta antes de continuar. Dejar pasar con cero elegidas recrearía el mismo
 * agujero que esta pantalla viene a tapar.
 *
 * Por qué esto no se repite en cada login, solo después de REGISTRARSE: es el
 * único momento en que un grupo con certeza no tiene ninguna categoría
 * propia todavía. Una cuenta ya existente entra directo a `/resumen`, como
 * siempre -- y si alguna vez quedara sin categorías por otro camino, el "+
 * Agregar" de `FormularioDeGasto` es la red de seguridad, no esta pantalla.
 *
 * El icono es un EMOJI libre, igual que en el "+ Agregar" del formulario de
 * gasto: no hay selector curado que mantener, y el teclado de emojis de iOS
 * (el globo, al lado de la barra espaciadora) ya resuelve "elegir uno".
 */

type Sugerida = { nombre: string; icono: string };

/**
 * Las seis de siempre, ahora como PUNTO DE PARTIDA y no como default del
 * backend. Mismas claves que `nombreDeCategoria` ya traduce a mostrar
 * ("cafe" -> "Café"), para no mantener una segunda tabla de nombres.
 */
const SUGERIDAS: Sugerida[] = [
  { nombre: 'cafe', icono: '☕' },
  { nombre: 'uber', icono: '🚗' },
  { nombre: 'comida', icono: '🍔' },
  { nombre: 'ropa', icono: '👕' },
  { nombre: 'regalos', icono: '🎁' },
  { nombre: 'otros', icono: '🏷️' },
];

type Elegible = Sugerida & { elegida: boolean };

export default function OnboardingCategorias() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [categorias, setCategorias] = useState<Elegible[]>(
    SUGERIDAS.map((s) => ({ ...s, elegida: true })),
  );
  const [creandoPropia, setCreandoPropia] = useState(false);
  const [nombreNueva, setNombreNueva] = useState('');
  const [iconoNueva, setIconoNueva] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function alternar(nombre: string) {
    setCategorias((actuales) =>
      actuales.map((c) => (c.nombre === nombre ? { ...c, elegida: !c.elegida } : c)),
    );
  }

  function agregarPropia() {
    const nombre = nombreNueva.trim();
    const icono = iconoNueva.trim();
    if (!nombre || !icono) return;

    // Chequeo LOCAL, contra lo que ya se eligió en esta pantalla: todavía no
    // se creó nada en el servidor, así que no hay nada que consultarle.
    const yaExiste = categorias.some((c) => c.nombre.toLowerCase() === nombre.toLowerCase());
    if (yaExiste) {
      setError('Ya tenés una categoría con ese nombre');
      return;
    }

    setError(null);
    setCategorias((actuales) => [...actuales, { nombre, icono, elegida: true }]);
    setNombreNueva('');
    setIconoNueva('');
    setCreandoPropia(false);
  }

  const elegidas = categorias.filter((c) => c.elegida);
  const listo = elegidas.length > 0;

  /**
   * Crea una por una las elegidas. TOLERANTE a "ya existe": si "Continuar" se
   * toca dos veces (una respuesta lenta y un segundo tap, o un reintento
   * después de que una categoría fallara por otro motivo a mitad de camino),
   * las que ya se crearon la primera vez no tienen que volver a fallar.
   */
  async function continuar() {
    setError(null);
    setEnviando(true);
    try {
      for (const c of elegidas) {
        try {
          await crearCategoria(c.nombre, c.icono);
        } catch (e) {
          const yaExistia = e instanceof ErrorDeApi && e.message.includes('Ya tenés');
          if (!yaExistia) throw e;
        }
      }
      router.replace('/resumen');
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron crear las categorías.');
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
          { paddingTop: insets.top + 24, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <Text style={estilos.seccion}>Antes de empezar</Text>
          <Text style={estilos.titulo}>Elegí tus categorías</Text>
          <Text style={estilos.bajada}>
            Las vas a usar para anotar cada gasto. Podés sacar las que no te sirvan, o agregar las
            tuyas -- y siempre vas a poder sumar más desde el formulario de carga.
          </Text>
        </View>

        <View style={estilos.chips}>
          {categorias.map((c) => (
            <Pressable
              key={c.nombre}
              onPress={() => alternar(c.nombre)}
              accessibilityRole="button"
              accessibilityState={{ selected: c.elegida }}
              style={[estilos.chip, c.elegida && estilos.chipElegido]}
            >
              <IconoCategoria
                nombre={c.icono}
                tamano={17}
                color={c.elegida ? colores.rioProfundo : colores.corteza}
              />
              <Text style={[estilos.chipTexto, c.elegida && estilos.chipTextoElegido]}>
                {nombreDeCategoria(c.nombre)}
              </Text>
            </Pressable>
          ))}

          <Pressable
            onPress={() => setCreandoPropia((v) => !v)}
            accessibilityRole="button"
            accessibilityState={{ selected: creandoPropia }}
            style={[estilos.chip, creandoPropia && estilos.chipElegido]}
          >
            <Text style={[estilos.chipTexto, creandoPropia && estilos.chipTextoElegido]}>
              + Agregar
            </Text>
          </Pressable>
        </View>

        {creandoPropia ? (
          <View style={estilos.nuevaCategoria}>
            <TextInput
              value={iconoNueva}
              onChangeText={setIconoNueva}
              placeholder="🏷️"
              placeholderTextColor={colores.borde}
              maxLength={8}
              autoCapitalize="none"
              autoCorrect={false}
              style={estilos.inputIcono}
              accessibilityLabel="Icono de la categoría nueva: tocá el globo del teclado para elegir un emoji"
            />
            <TextInput
              value={nombreNueva}
              onChangeText={setNombreNueva}
              placeholder="Nombre de la categoría"
              placeholderTextColor={colores.textoSuave}
              maxLength={40}
              style={estilos.inputNombre}
            />
            <Pressable
              onPress={agregarPropia}
              disabled={!nombreNueva.trim() || !iconoNueva.trim()}
              accessibilityRole="button"
              style={[
                estilos.botonChico,
                (!nombreNueva.trim() || !iconoNueva.trim()) && estilos.botonChicoDeshabilitado,
              ]}
            >
              <Text style={estilos.botonChicoTexto}>Listo</Text>
            </Pressable>
          </View>
        ) : null}

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        <Boton
          titulo="Continuar"
          onPress={continuar}
          cargando={enviando}
          deshabilitado={!listo}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 24, gap: 20 },

  encabezado: { gap: 6, marginBottom: 4 },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 26, color: colores.texto },
  bajada: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.textoSuave, marginTop: 2 },

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
    minHeight: 44,
  },
  chipElegido: { backgroundColor: colores.rioSuave, borderColor: colores.rio },
  chipTexto: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  chipTextoElegido: { fontFamily: fuentes.cuerpoSemi, color: colores.rioProfundo },

  nuevaCategoria: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  inputIcono: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    width: 56,
    minHeight: 44,
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
    minHeight: 44,
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.texto,
  },
  botonChico: {
    backgroundColor: colores.rio,
    borderRadius: 12,
    paddingHorizontal: 16,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  botonChicoDeshabilitado: { opacity: 0.4 },
  botonChicoTexto: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.tarjeta },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
