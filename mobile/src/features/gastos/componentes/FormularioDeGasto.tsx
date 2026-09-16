import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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

import { ErrorDeApi } from '../../../api/cliente';
import type {
  CategoriaRespuesta,
  GuardarGastoRequest,
  UsuarioRespuesta,
} from '../../../api/tipos';
import { Boton } from '../../../componentes/Boton';
import { IconoCategoria } from '../../../componentes/IconoCategoria';
import { useSesion } from '../../../features/auth/sesion';
import {
  actualizarGasto,
  crearGasto,
  eliminarGasto,
  hoyLocal,
  traerCategorias,
  traerGasto,
  traerGrupo,
} from '../api';
import { colores } from '../../../tema/colores';
import { fuentes, numerosTabulares } from '../../../tema/tipografia';

/**
 * Cargar un gasto.
 *
 * Este formulario es donde se juega el proyecto entero. La usuaria abandono un
 * intento anterior porque anotar era incomodo, y dijo que prefiere olvidarse un
 * gasto antes que anotar lento. Por eso: **categoria, monto, descripcion, y el
 * toggle de hormiga.** Nada mas.
 *
 * Cada campo que se le agregue se paga en abandono. La fecha no se pregunta:
 * es hoy.
 *
 * Lo de compartido -- el reparto y quien pago -- existe pero **arranca cerrado**
 * y aparece recien al prender el switch. Es revelacion progresiva: el camino
 * rapido sigue siendo el de siempre, y lo que solo hace falta a veces no ocupa
 * lugar el resto del tiempo.
 */

/**
 * Los repartos que se ofrecen, como porcentaje que le toca a quien paga.
 *
 * Son chips y no un campo numerico ni un slider: cualquiera de estos es UN tap,
 * y el reparto es el camino raro -- el 50/50 ya viene puesto. Un slider ademas
 * costaria una dependencia nativa mas.
 *
 * Los valores son arbitrarios a proposito y cubren lo que existe entre dos
 * personas: mitad y mitad, o alguien que pone mas. Un 63% no es un caso real
 * dividiendo una cena; si alguna vez lo es, esto pasa a ser un input.
 *
 * El 100% no es lo mismo que un gasto PERSONAL aunque los numeros den igual:
 * significa "esto es nuestro y esta vez lo pago yo entero", y por eso vive en el
 * campo `tipo` y no se deduce de los montos. Ver el CLAUDE.md.
 */
const REPARTOS = [50, 60, 70, 80, 100] as const;
const REPARTO_POR_DEFECTO = 50;

export function FormularioDeGasto({ id }: { id?: string }) {
  const editando = id !== undefined;
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { usuario } = useSesion();

  const [categorias, setCategorias] = useState<CategoriaRespuesta[]>([]);
  const [categoriaId, setCategoriaId] = useState<string | null>(null);
  const [monto, setMonto] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [esHormiga, setEsHormiga] = useState(false);
  const [esCompartido, setEsCompartido] = useState(false);
  const [porcentaje, setPorcentaje] = useState(REPARTO_POR_DEFECTO);
  const [otro, setOtro] = useState<UsuarioRespuesta | null>(null);
  const [pagueYo, setPagueYo] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // La fecha y la version del gasto que se esta editando. NO se piden ni se
  // muestran: la fecha es del momento en que se cargo y editar un monto no
  // deberia moverla, y la version es plomeria del bloqueo optimista.
  const [fecha, setFecha] = useState(hoyLocal());
  const [version, setVersion] = useState<number | undefined>(undefined);
  const [cargando, setCargando] = useState(editando);

  // En modo edicion, traer el gasto y llenar el formulario con lo que ya tiene.
  //
  // Se pide a la API en vez de recibirlo por parametros de navegacion desde la
  // lista. Cuesta un viaje mas y lo vale: la lista pudo cargarse hace rato, y
  // editar sobre una copia vieja es justo lo que el bloqueo optimista detecta.
  // Trayendolo ahora, un 409 significa "alguien edito mientras vos editabas" y
  // no "tu lista estaba desactualizada".
  useEffect(() => {
    if (!editando) return;
    (async () => {
      try {
        const gasto = await traerGasto(id);
        setCategoriaId(gasto.categoria.id);
        // El monto vuelve a la pantalla con coma, que es como se escribe aca.
        setMonto(String(gasto.monto).replace('.', ','));
        setDescripcion(gasto.descripcion);
        setEsHormiga(gasto.esHormiga);
        setEsCompartido(gasto.tipo === 'COMPARTIDO');
        setFecha(gasto.fecha);
        setVersion(gasto.version);

        // Reconstruir quien pago y TU parte a partir de lo que guardo el
        // backend, deshaciendo la misma inversion que se aplica al guardar.
        // `montoPagador` es la parte de quien pago; si pagaste vos, tu parte es
        // esa, y si pago el otro, tu parte es la que queda.
        const loPagueYo = gasto.pagadoPor.id === usuario?.id;
        setPagueYo(loPagueYo);
        if (gasto.tipo === 'COMPARTIDO' && gasto.monto > 0) {
          const miParte = loPagueYo ? gasto.montoPagador : gasto.deudaGenerada;
          setPorcentaje(Math.round((miParte / gasto.monto) * 100));
        }
      } catch (e) {
        setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el gasto.');
      } finally {
        setCargando(false);
      }
    })();
  }, [editando, id, usuario?.id]);

  useEffect(() => {
    (async () => {
      try {
        setCategorias(await traerCategorias());
      } catch (e) {
        setError(e instanceof ErrorDeApi ? e.message : 'No se pudieron traer las categorias.');
      }
    })();
  }, []);

  // El grupo se pide aparte y su fallo NO se muestra como error: sin el, el
  // formulario sigue sirviendo entero -- lo unico que se pierde es poder decir
  // "lo pago la otra persona". Mezclarlo con el catch de las categorias haria
  // que un problema en un extra tape el camino principal, que es el rapido.
  useEffect(() => {
    (async () => {
      try {
        const grupo = await traerGrupo();
        setOtro(grupo.integrantes.find((u) => u.id !== usuario?.id) ?? null);
      } catch {
        setOtro(null);
      }
    })();
  }, [usuario?.id]);

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
      const datos = {
        monto: montoNumero,
        categoriaId: categoriaId!,
        // Al editar se conserva la fecha original. Cambiar el monto de un gasto
        // del martes no deberia mudarlo a hoy: lo moveria de mes cerca del
        // corte, y con el los totales y el saldo.
        fecha,
        descripcion: descripcion.trim(),
        tipo: esCompartido ? 'COMPARTIDO' : 'PERSONAL',
        // Los dos solo viajan si es compartido: en un PERSONAL el backend
        // ignora el porcentaje (montoPagador == monto) y ademas rechaza un
        // pagador que no seas vos.
        //
        // OJO CON LA INVERSION, que es el punto mas facil de romper de toda la
        // pantalla. El campo se llama `porcentajePagador`: es la parte de QUIEN
        // PAGO, no la tuya. Los chips en cambio preguntan por TU parte, que es
        // como piensa quien carga el gasto. Cuando pago la otra persona, los dos
        // no son lo mismo y hay que dar vuelta el numero.
        //
        // Ejemplo: elegis "Todo yo" (100) y pago Viole -> le toca 0% a ella, o
        // sea que le debes el gasto entero. Sin invertir, el backend entenderia
        // lo contrario exacto y el saldo saldria al reves.
        //
        // Es aritmetica de PORCENTAJES, no de plata: el 100 - x es sobre enteros
        // y no toca un peso. La cuenta que si es de plata (monto x porcentaje)
        // la sigue haciendo el backend, que es el unico que sabe donde cae el
        // centavo.
        porcentajePagador: esCompartido ? (pagueYo ? porcentaje : 100 - porcentaje) : undefined,
        // Ausente significa "lo pague yo", que es lo que el backend asume.
        pagadoPorId: esCompartido && !pagueYo ? otro?.id : undefined,
        esHormiga,
        // Solo al editar. Es lo que hace que, si la otra persona toco el mismo
        // gasto mientras tanto, el backend devuelva 409 en vez de pisar su
        // cambio en silencio.
        version,
      } satisfies GuardarGastoRequest;

      if (editando) {
        await actualizarGasto(id, datos);
      } else {
        await crearGasto(datos);
      }
      // `back` y no `replace`: esto es un modal que se cierra. La pantalla que
      // queda abajo se recarga sola, porque escucha el foco.
      router.back();
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo guardar el gasto.');
    } finally {
      setEnviando(false);
    }
  }

  /**
   * Borrar, con confirmacion.
   *
   * El `Alert` no es ceremonia: borrar un gasto no se puede deshacer -- no hay
   * papelera ni historial -- y el boton vive al lado del de guardar. Un toque
   * accidental sin confirmacion se lleva un dato que Viole cargo parada en un
   * mostrador y no va a poder reconstruir.
   */
  function confirmarBorrado() {
    Alert.alert('Borrar este gasto', 'No se puede deshacer.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Borrar',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            setError(null);
            setEnviando(true);
            try {
              await eliminarGasto(id!);
              router.back();
            } catch (e) {
              setError(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar el gasto.');
            } finally {
              setEnviando(false);
            }
          })();
        },
      },
    ]);
  }

  // Mientras se trae el gasto a editar, no se dibuja el formulario vacio: si se
  // dibujara, se veria un instante en blanco y despues saltarian los valores,
  // que parece un error aunque no lo sea.
  if (cargando) {
    return (
      <View style={[estilos.pantalla, estilos.centrado]}>
        <ActivityIndicator color={colores.rio} size="large" />
      </View>
    );
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
          <Text style={estilos.titulo}>{editando ? 'Editar gasto' : 'Nuevo gasto'}</Text>
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
            // Al editar no, que el valor ya esta cargado y el teclado solo
            // taparia la pantalla que la persona vino a mirar.
            autoFocus={!editando}
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
                  <IconoCategoria
                    nombre={c.icono}
                    tamano={17}
                    // El icono acompana al texto del chip: cuando el chip esta
                    // elegido, los dos pasan a teal.
                    color={elegida ? colores.rioProfundo : colores.corteza}
                  />
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

        {/*
          El compartido va DESPUES del de hormiga, y no es un detalle de orden:
          el camino rapido es monto -> categoria -> descripcion -> guardar, y
          hormiga es el corazon del producto. Compartido es el caso menos
          frecuente, asi que va ultimo y arranca cerrado.

          Teal y no ambar: el ambar es del gasto hormiga y de nada mas. Aca el
          teal significa lo que significa en toda la app, que es "lo compartido".
        */}
        <View style={[estilos.compartido, esCompartido && estilos.compartidoActivo]}>
          <View style={estilos.filaSwitch}>
            <View style={estilos.compartidoTexto}>
              <Text style={estilos.compartidoTitulo}>Es un gasto compartido</Text>
              <Text style={estilos.compartidoBajada}>
                {esCompartido
                  ? 'Lo van a ver los dos y entra en el saldo'
                  : 'Un gasto personal lo ves solo vos'}
              </Text>
            </View>
            <Switch
              value={esCompartido}
              onValueChange={setEsCompartido}
              trackColor={{ false: colores.borde, true: colores.rio }}
              thumbColor={colores.tarjeta}
              ios_backgroundColor={colores.borde}
            />
          </View>

          {/*
            El reparto aparece recien al prender el switch. Es "revelacion
            progresiva": el 90% de las veces el formulario no lo muestra, y quien
            lo necesita lo tiene a un tap. Mostrarlo siempre seria un campo mas
            en la pantalla que tiene que ser la mas rapida de la app.
          */}
          {esCompartido ? (
            <View style={estilos.reparto}>
              {/*
                Quien pago aparece SOLO si el grupo ya tiene a la otra persona.
                Mientras Viole no se haya registrado, un selector con una sola
                opcion no es una eleccion: es un control que ocupa lugar y no
                hace nada.
              */}
              {otro ? (
                <>
                  <Text style={estilos.repartoEtiqueta}>Quien pago</Text>
                  <View style={estilos.chips}>
                    {[true, false].map((yo) => (
                      <Pressable
                        key={String(yo)}
                        onPress={() => setPagueYo(yo)}
                        accessibilityRole="button"
                        accessibilityState={{ selected: pagueYo === yo }}
                        style={[estilos.chip, pagueYo === yo && estilos.chipElegido]}
                      >
                        <Text
                          style={[
                            estilos.chipTexto,
                            pagueYo === yo && estilos.chipTextoElegido,
                          ]}
                        >
                          {yo ? 'Pague yo' : `Pago ${otro.nombre}`}
                        </Text>
                      </Pressable>
                    ))}
                  </View>
                </>
              ) : null}

              <Text style={estilos.repartoEtiqueta}>Tu parte</Text>
              <View style={estilos.chips}>
                {REPARTOS.map((p) => {
                  const elegido = p === porcentaje;
                  return (
                    <Pressable
                      key={p}
                      onPress={() => setPorcentaje(p)}
                      accessibilityRole="button"
                      accessibilityState={{ selected: elegido }}
                      // El porcentaje solo no dice nada leido en voz alta.
                      accessibilityLabel={
                        p === 100 ? 'Pagas vos el total' : `Vos ${p} por ciento, la otra persona ${100 - p}`
                      }
                      style={[estilos.chip, elegido && estilos.chipElegido]}
                    >
                      <Text style={[estilos.chipTexto, elegido && estilos.chipTextoElegido]}>
                        {p === 100 ? 'Todo yo' : `${p} / ${100 - p}`}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
              {/*
                No se muestra cuanto le toca a cada uno en pesos. La app NO hace
                aritmetica con plata: monto x porcentaje lo calcula el backend,
                que es el unico que sabe donde cae el centavo cuando la division
                no es exacta ($10,01 al 50/50). Un preview calculado aca podria
                no coincidir con lo que despues queda guardado.
              */}
            </View>
          ) : null}
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}
      </ScrollView>

      <View style={[estilos.pie, { paddingBottom: insets.bottom + 12 }]}>
        <Boton
          titulo={editando ? 'Guardar cambios' : 'Guardar'}
          onPress={guardar}
          cargando={enviando}
          deshabilitado={!listo}
        />
        {editando ? (
          <Pressable onPress={confirmarBorrado} hitSlop={12} accessibilityRole="button" disabled={enviando}>
            <Text style={estilos.borrar}>Borrar este gasto</Text>
          </Pressable>
        ) : null}
      </View>
    </KeyboardAvoidingView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  centrado: { alignItems: 'center', justifyContent: 'center' },
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

  // Misma caja que el bloque de hormiga, pero en columna: adentro entra el
  // reparto cuando el switch esta prendido.
  compartido: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  // Al activarse cambia solo el borde, no el fondo. El bloque de hormiga SI se
  // tine entero, y esa asimetria es deliberada: el hormiga es el corazon del
  // producto y tiene permiso para gritar; este no tiene que competirle. Ademas,
  // si el fondo pasara a teal, el chip de reparto elegido -- que tambien es teal
  // suave -- se perderia contra el.
  compartidoActivo: { borderColor: colores.rio },
  filaSwitch: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  compartidoTexto: { flex: 1 },
  compartidoTitulo: { fontFamily: fuentes.cuerpoSemi, fontSize: 16, color: colores.texto },
  compartidoBajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 13,
    color: colores.textoSuave,
    marginTop: 2,
  },

  reparto: { marginTop: 14, gap: 8 },
  repartoEtiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
  // Terracota profunda y no un boton lleno: borrar tiene que ser encontrable y
  // no invitar. Un segundo boton solido al lado del de guardar compite con el.
  borrar: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.terracotaProfunda,
    textAlign: 'center',
    paddingVertical: 12,
  },

  pie: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colores.borde,
    backgroundColor: colores.fondo,
  },
});
