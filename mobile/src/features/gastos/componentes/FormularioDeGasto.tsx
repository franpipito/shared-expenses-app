import { useEffect, useRef, useState } from 'react';
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


import { ErrorDeApi } from '../../../api/cliente';
import type {
  CategoriaRespuesta,
  GastoRespuesta,
  GuardarGastoRequest,
  PozoRespuesta,
  UsuarioRespuesta,
} from '../../../api/tipos';
import { Boton } from '../../../componentes/Boton';
import { IconoCategoria } from '../../../componentes/IconoCategoria';
import { useSesion } from '../../auth/sesion';
import { hoyLocal, traerCategorias, traerGrupo } from '../api';
import { traerPozoActivo } from '../../vaquita/api';
import { colores } from '../../../tema/colores';
import { fuentes, numerosTabulares } from '../../../tema/tipografia';

/**
 * El formulario de un gasto, que sirve para cargarlo y para editarlo.
 *
 * ESTA EXTRAIDO Y NO DUPLICADO por una razon concreta: adentro vive la logica
 * del reparto, que incluye la inversion del porcentaje cuando pago la otra
 * persona. Tener esa cuenta escrita en dos lugares es exactamente como el saldo
 * termina saliendo al reves en una de las dos pantallas y nadie se entera.
 *
 * Quien lo usa decide que hacer al guardar: `app/gasto/nuevo.tsx` lo encola, y
 * `app/gasto/[id].tsx` lo manda con PUT. El formulario no sabe cual de las dos
 * cosas pasa.
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

/**
 * De donde sale el gasto.
 *
 * Reemplaza al booleano `esCompartido` que habia antes. El motivo es que con la
 * vaquita ya son tres opciones y no dos, y **un booleano que crece a tres
 * estados es como se ensucian los formularios**: aparece un segundo booleano,
 * y con el las combinaciones imposibles.
 *
 * Fijate que VAQUITA no es un `tipo` del backend: alla el gasto sigue siendo
 * COMPARTIDO y lo unico que lo distingue es tener `pozoId`. Este tipo existe
 * solo en la pantalla, que es donde la pregunta se hace una sola vez.
 */
type Destino = 'PERSONAL' | 'COMPARTIDO' | 'VAQUITA';

/** De donde salio un gasto que ya existe, para preseleccionar el chip al editar. */
function destinoDe(gasto: GastoRespuesta | undefined): Destino {
  if (!gasto) return 'PERSONAL';
  if (gasto.pozoId) return 'VAQUITA';
  return gasto.tipo === 'PERSONAL' ? 'PERSONAL' : 'COMPARTIDO';
}

/**
 * Que porcentaje le tocaba a QUIEN ABRE la pantalla, para preseleccionar el chip.
 *
 * Los chips preguntan por "tu parte"; el backend habla de la parte de quien
 * pago. Cuando pago la otra persona hay que dar vuelta el numero, que es la
 * misma inversion que hace `guardar()` en el otro sentido. Estan a diez lineas
 * una de la otra a proposito: si alguien toca una, tiene la otra a la vista.
 *
 * El porcentaje viene YA CALCULADO del backend. No se deduce de montoPagador
 * dividido monto, que seria aritmetica de plata en punto flotante.
 */
function parteMia(gasto: GastoRespuesta | undefined, miId: string | undefined): number {
  if (!gasto || gasto.porcentajePagador === null) return REPARTO_POR_DEFECTO;
  return gasto.pagadoPor.id === miId
    ? gasto.porcentajePagador
    : 100 - gasto.porcentajePagador;
}

type Props = {
  /** El gasto que se esta editando. Ausente = se esta cargando uno nuevo. */
  inicial?: GastoRespuesta;
  titulo: string;
  textoDeAccion: string;
  onGuardar: (datos: GuardarGastoRequest) => Promise<void>;
  onCancelar: () => void;
  /** Solo en edicion. Se dibuja abajo de todo, separado de la accion principal. */
  pieExtra?: React.ReactNode;
};

export function FormularioDeGasto({
  inicial,
  titulo,
  textoDeAccion,
  onGuardar,
  onCancelar,
  pieExtra,
}: Props) {
  const insets = useSafeAreaInsets();
  const { usuario } = useSesion();

  const [categorias, setCategorias] = useState<CategoriaRespuesta[]>([]);
  const [categoriaId, setCategoriaId] = useState<string | null>(
    inicial?.categoria.id ?? null,
  );
  // El monto vuelve a la coma con la que se escribe en Argentina. Es la inversa
  // exacta del replace de guardar(), y sigue sin ser aritmetica: es texto.
  const [monto, setMonto] = useState(
    inicial ? String(inicial.monto).replace('.', ',') : '',
  );
  const [descripcion, setDescripcion] = useState(inicial?.descripcion ?? '');
  const [esHormiga, setEsHormiga] = useState(inicial?.esHormiga ?? false);
  const [destino, setDestino] = useState<Destino>(destinoDe(inicial));
  const [pozo, setPozo] = useState<PozoRespuesta | null>(null);
  // Para que el default de la vaquita, que llega tarde porque es una request,
  // no pise una eleccion que la persona ya hizo. Es el clasico de setear estado
  // desde un efecto asincrono.
  // Editando ya hay una eleccion hecha, asi que el default de la vaquita no
  // tiene que pisarla: arranca en true.
  /**
   * Si la persona ya eligio el destino a mano.
   *
   * ES UN REF Y NO ESTADO, y el motivo es un bug real que tenia el codigo
   * anterior: el efecto que trae la vaquita corre con `[]`, asi que capturaba
   * el valor del PRIMER render y lo conservaba para siempre. Tocar el switch
   * mientras la request estaba en vuelo no cambiaba lo que el efecto veia, y
   * cuando contestaba pisaba la eleccion con VAQUITA.
   *
   * El comentario viejo decia que sacarlo de las dependencias evitaba pisar la
   * eleccion. Hacia exactamente lo contrario.
   *
   * Un ref siempre lee el valor actual, sin re-ejecutar el efecto. Es
   * justamente para lo que sirve.
   */
  const eligioAMano = useRef(inicial !== undefined);
  const [porcentaje, setPorcentaje] = useState(parteMia(inicial, usuario?.id));
  const [otro, setOtro] = useState<UsuarioRespuesta | null>(null);
  const [pagueYo, setPagueYo] = useState(
    inicial ? inicial.pagadoPor.id === usuario?.id : true,
  );
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { dato } = await traerCategorias();
        setCategorias(dato ?? []);
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
        const { dato: grupo } = await traerGrupo();
        setOtro(grupo?.integrantes.find((u) => u.id !== usuario?.id) ?? null);
      } catch {
        setOtro(null);
      }
    })();
  }, [usuario?.id]);

  /**
   * La vaquita abierta, si hay una. Su fallo tampoco se muestra: sin ella el
   * formulario sigue siendo el de siempre.
   *
   * EL DEFAULT ES LA PARTE INTERESANTE. Si hay una vaquita abierta **y hoy cae
   * dentro de las fechas del viaje**, el formulario abre con Vaquita puesta: en
   * Bariloche el 90% de lo que carguen sale del pozo, asi que el camino rapido
   * queda mas corto que hoy (ni reparto ni quien pago: pago el pozo).
   *
   * Se ata a `vigente` y no a que la vaquita exista para que el cafe que se
   * compra Viole sola en octubre no se cargue sin querer al viaje. Y `vigente`
   * lo decide el backend, porque "hoy" depende de la zona horaria.
   */
  useEffect(() => {
    (async () => {
      try {
        const { dato: activo } = await traerPozoActivo();
        setPozo(activo);

        // ACA HABIA UN DEFAULT AUTOMATICO A VAQUITA Y SE SACO. Vale contar por
        // que, porque parecia una buena optimizacion.
        //
        // La idea era que en Bariloche el 90% de los gastos salen del pozo, asi
        // que abrir el formulario ya en Vaquita ahorraba un tap. El diseño lo
        // ataba a las fechas del viaje (`vigente`) justamente para que no
        // aplicara fuera de el.
        //
        // El problema: la pantalla que abre la vaquita **no pide fechas**, y sin
        // fechas `Pozo.vigenteEl()` devuelve true siempre mientras este abierta.
        // O sea que desde que abren la vaquita -- dos semanas antes del viaje --
        // hasta que la cierren, CADA alta abria en VAQUITA. Y en esta app
        // VAQUITA significa COMPARTIDO, o sea **visible para los dos**.
        //
        // Cruzalo con la respuesta 16 de la entrevista ("si, mas que nada cuando
        // te hago regalitos"), con la categoria "regalos" que existe, y con el
        // requisito de velocidad que empuja a guardar sin revisar: un regalo
        // sorpresa cargado rapido se publicaba solo en la lista de la vaquita.
        //
        // Era una regresion contra el UNICO pedido de privacidad de toda la
        // entrevista, y la que menos se iba a notar hasta que pasara.
        //
        // El default vuelve a ser PERSONAL, que es el seguro: lo compartido se
        // elige, nunca se asume. Cuesta un tap por gasto durante los cinco dias
        // del viaje. Si algun dia la vaquita pide fechas de verdad, se puede
        // reconsiderar -- pero recien ahi.
        void activo;
      } catch {
        setPozo(null);
      }
    })();
    // Corre una sola vez, al abrir. El ref de arriba es lo que hace que eso sea
    // seguro: lee el valor actual sin necesidad de estar en las dependencias.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function elegirDestino(elegido: Destino) {
    eligioAMano.current = true;
    setDestino(elegido);
  }

  /**
   * El monto se escribe con coma (es como se escribe en Argentina) y viaja con
   * punto, que es lo unico que entiende JSON. Es la unica transformacion de
   * plata que hace la app: la aritmetica sigue siendo toda del backend.
   */
  // Hay vaquita en juego si hay una abierta, o si este gasto ya salio de una.
  const hayVaquita = pozo !== null || inicial?.pozoId != null;
  const nombreDeLaVaquita = pozo?.nombre ?? 'la vaquita';

  const montoNumero = Number(monto.replace(',', '.'));
  const montoValido = monto.trim() !== '' && Number.isFinite(montoNumero) && montoNumero > 0;
  /**
   * Quien es "la otra persona", sin depender de que haya contestado /grupo.
   *
   * ARREGLA UN BUG QUE DABA VUELTA EL SALDO. `otro` sale de una request async, y
   * en modo edicion el boton Guardar esta activo desde el primer render, porque
   * monto, categoria y descripcion vienen prellenados. Guardar en esa ventana
   * mandaba `pagadoPorId: undefined`, y el backend interpreta eso como "pago
   * quien carga" (`resolverPagador`). Como el porcentaje SI se invertia, el
   * gasto quedaba exactamente al reves: una cena de $20.000 al 60% de Viole
   * pasaba a ser 60% de Franco, un swing de $16.000 sin un solo error.
   *
   * Con Render dormido esa ventana son los 40-60 segundos del arranque en frio,
   * o sea el formulario entero. Y si /grupo fallaba de verdad, era permanente.
   *
   * La salida es que editando NO hace falta /grupo para saber quien pago: ya
   * esta en el gasto. /grupo solo hace falta para CAMBIAR el pagador.
   */
  const idDelOtro =
    otro?.id ??
    (inicial && inicial.pagadoPor.id !== usuario?.id ? inicial.pagadoPor.id : undefined);

  const listo =
    montoValido &&
    categoriaId !== null &&
    descripcion.trim() !== '' &&
    // Y nunca se guarda un compartido que pago el otro sin saber quien es el
    // otro. Es el cinturon ademas de los tirantes: si por algun camino que no
    // previmos `idDelOtro` sigue sin resolverse, preferimos un boton
    // deshabilitado antes que un gasto invertido.
    !(destino === 'COMPARTIDO' && !pagueYo && !idDelOtro);

  async function guardar() {
    setError(null);
    setEnviando(true);
    try {
      await onGuardar({
        monto: montoNumero,
        categoriaId: categoriaId!,
        // Editando se conserva la fecha original: corregir un monto no deberia
        // mover el gasto de dia, y menos de mes.
        fecha: inicial?.fecha ?? hoyLocal(),
        descripcion: descripcion.trim(),
        // VAQUITA no es un tipo del backend: alla es un COMPARTIDO con pozoId.
        // El backend RECHAZA un PERSONAL con pozoId en vez de corregirlo, asi
        // que esta linea y la de pozoId tienen que moverse juntas.
        tipo: destino === 'PERSONAL' ? 'PERSONAL' : 'COMPARTIDO',
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
        //
        // En un gasto de la VAQUITA no viaja ninguno de los dos: el backend
        // ignora el porcentaje (es mitad y mitad por construccion, porque el
        // pozo se financio entre los dos) y toma como pagador a quien carga.
        // No hay reparto que decidir al gastar plata que ya es de los dos.
        porcentajePagador:
          destino === 'COMPARTIDO' ? (pagueYo ? porcentaje : 100 - porcentaje) : undefined,
        // Ausente significa "lo pague yo", que es lo que el backend asume.
        pagadoPorId: destino === 'COMPARTIDO' && !pagueYo ? idDelOtro : undefined,
        // EL POZO DEL GASTO GANA SOBRE EL ACTIVO, y es el arreglo de un bug
        // concreto: si el viaje ya se cerro, `traerPozoActivo()` devuelve null,
        // y mandar el id del activo sacaria el gasto de su vaquita en silencio
        // al corregirle el monto. Editando se conserva el pozo que ya tenia; el
        // activo solo se usa cuando el gasto es nuevo, o cuando se lo esta
        // moviendo A la vaquita.
        pozoId: destino === 'VAQUITA' ? (inicial?.pozoId ?? pozo?.id) : undefined,
        // Un gasto del viaje SI se puede marcar como hormiga: un souvenir
        // carisimo que no hacia falta lo es. Lo que no hace es contar para el
        // total hormiga del mes ni para el animo de la nutria -- eso lo filtra
        // el backend, no esta pantalla.
        esHormiga,
        // Bloqueo optimista: se devuelve la version que se leyo. Si la otra
        // persona edito el mismo gasto en el medio, el backend responde 409 en
        // vez de pisar su cambio en silencio. Al crear no hay version que mandar.
        version: inicial?.version,
      });
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo guardar el gasto.');
      // Si fallo NO se cierra la pantalla: lo tipeado sigue ahi para reintentar.
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
          <Text style={estilos.titulo}>{titulo}</Text>
          <Pressable onPress={onCancelar} hitSlop={12} accessibilityRole="button">
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
        <View style={[estilos.compartido, destino !== 'PERSONAL' && estilos.compartidoActivo]}>
          {/*
            DOS FORMAS DISTINTAS PARA EL MISMO CAMPO, y es deliberado.

            Sin vaquita abierta -- o sea casi todo el anio -- el control es el
            switch de siempre y esta pantalla no cambio en nada. Con una vaquita
            abierta pasan a ser tres chips, porque un switch no tiene tres
            estados y meter un segundo switch traeria combinaciones imposibles.

            Lo importante es que el tercer chip **no agrega un campo, reemplaza
            uno**: elegir Vaquita es mas rapido que elegir Compartido, porque no
            hay que decidir reparto ni quien pago. Un tap en vez de tres.
          */}
          {/*
            Los tres chips aparecen si hay una vaquita abierta O si el gasto que
            se esta editando ya pertenece a una. Sin la segunda condicion, editar
            un gasto de un viaje cerrado mostraria el switch de compartido, que
            no tiene un estado para "vaquita": se veria como personal y guardar
            lo sacaria del pozo.
          */}
          {hayVaquita ? (
            <>
              <Text style={estilos.repartoEtiqueta}>De donde sale</Text>
              <View style={estilos.chips}>
                {(['PERSONAL', 'COMPARTIDO', 'VAQUITA'] as const).map((d) => {
                  const elegido = d === destino;
                  return (
                    <Pressable
                      key={d}
                      onPress={() => elegirDestino(d)}
                      accessibilityRole="button"
                      accessibilityState={{ selected: elegido }}
                      style={[estilos.chip, elegido && estilos.chipElegido]}
                    >
                      <Text style={[estilos.chipTexto, elegido && estilos.chipTextoElegido]}>
                        {d === 'PERSONAL' ? 'Personal' : d === 'COMPARTIDO' ? 'Compartido' : 'Vaquita'}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
              <Text style={estilos.compartidoBajada}>
                {destino === 'PERSONAL'
                  ? 'Un gasto personal lo ves solo vos'
                  : destino === 'COMPARTIDO'
                    ? 'Lo van a ver los dos y entra en el saldo'
                    : `Sale de ${nombreDeLaVaquita}. No genera deuda entre ustedes.`}
              </Text>
            </>
          ) : (
            <View style={estilos.filaSwitch}>
              <View style={estilos.compartidoTexto}>
                <Text style={estilos.compartidoTitulo}>Es un gasto compartido</Text>
                <Text style={estilos.compartidoBajada}>
                  {destino === 'COMPARTIDO'
                    ? 'Lo van a ver los dos y entra en el saldo'
                    : 'Un gasto personal lo ves solo vos'}
                </Text>
              </View>
              <Switch
                value={destino === 'COMPARTIDO'}
                onValueChange={(v) => elegirDestino(v ? 'COMPARTIDO' : 'PERSONAL')}
                trackColor={{ false: colores.borde, true: colores.rio }}
                thumbColor={colores.tarjeta}
                ios_backgroundColor={colores.borde}
              />
            </View>
          )}

          {/*
            El reparto aparece recien al prender el switch. Es "revelacion
            progresiva": el 90% de las veces el formulario no lo muestra, y quien
            lo necesita lo tiene a un tap. Mostrarlo siempre seria un campo mas
            en la pantalla que tiene que ser la mas rapida de la app.
          */}
          {destino === 'COMPARTIDO' ? (
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
          titulo={textoDeAccion}
          onPress={guardar}
          cargando={enviando}
          deshabilitado={!listo}
        />
        {pieExtra}
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

  pie: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colores.borde,
    backgroundColor: colores.fondo,
  },
});
