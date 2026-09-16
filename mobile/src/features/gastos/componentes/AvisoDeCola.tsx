import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatearMonto } from '../../../componentes/Monto';
import { colores } from '../../../tema/colores';
import { fuentes } from '../../../tema/tipografia';
import { useCola } from '../hooks/useCola';

/**
 * Avisa que hay gastos esperando a que haya red.
 *
 * NO ES DECORACION: es lo que evita el peor bug de una cola offline, que no es
 * tecnico sino de confianza. Si la app dice "guardado" y el gasto no aparece en
 * la lista, la persona no puede distinguir "esta en camino" de "se perdio", y lo
 * unico que se le ocurre es cargarlo de nuevo. Resultado: un duplicado, que es
 * peor que el problema original porque no se ve como un error sino como un total
 * del mes equivocado.
 *
 * Cuando no hay nada pendiente no dibuja nada. Un cartel permanente diciendo
 * "todo sincronizado" es ruido en la pantalla principal de una app cuyo unico
 * requisito duro es no estorbar.
 *
 * SIN AMBAR. El ambar es del gasto hormiga y de nada mas (`docs/diseno.md`). Lo
 * pendiente va en arena, que es neutro; lo rechazado en terracota, que es el
 * color de "mira esto".
 */
export function AvisoDeCola() {
  const { enCamino, rechazados, enviar, reintentarUno, descartarUno } = useCola();

  if (enCamino.length === 0 && rechazados.length === 0) return null;

  return (
    <View style={estilos.contenedor}>
      {enCamino.length > 0 ? (
        <Pressable
          onPress={() => void enviar()}
          accessibilityRole="button"
          accessibilityLabel={`${enCamino.length} gastos sin enviar. Tocar para reintentar.`}
          style={({ pressed }) => [estilos.enCamino, pressed && estilos.presionado]}
        >
          <Text style={estilos.titulo}>
            {enCamino.length === 1
              ? 'Hay 1 gasto sin enviar'
              : `Hay ${enCamino.length} gastos sin enviar`}
          </Text>
          <Text style={estilos.bajada}>
            Estan guardados en el telefono. Se mandan solos cuando haya senial, y
            no se pierden aunque cierres la app. Toca para reintentar ahora.
          </Text>
        </Pressable>
      ) : null}

      {/*
        Un gasto rechazado NO se reintenta y NO se borra solo. El backend ya dijo
        que no lo va a aceptar -- por ejemplo, la vaquita se cerro mientras el
        gasto estaba en la cola -- asi que reintentarlo seria dar el mismo error
        para siempre y tapar a los que si pueden entrar. Pero descartarlo por
        decision de la app seria exactamente la perdida de datos que la cola
        viene a evitar. Decide la persona.
      */}
      {rechazados.map((p) => (
        <View key={p.clienteId} style={estilos.rechazado}>
          <Text style={estilos.tituloRechazado}>No se pudo guardar un gasto</Text>
          <Text style={estilos.bajada}>
            {formatearMonto(p.gasto.monto)} · {p.gasto.descripcion}
          </Text>
          <Text style={estilos.motivo}>{p.error}</Text>
          {/*
            DOS SALIDAS, no una. Antes la unica accion era descartar, o sea
            tirar el gasto -- y un 4xx puede dejar de serlo (la vaquita se
            cerro y la reabrieron, o el token vencio y volviste a entrar).
            Ofrecer solo "descartar" convertia un problema temporal en perdida
            de datos.
          */}
          <View style={estilos.acciones}>
            <Pressable
              onPress={() => void reintentarUno(p.clienteId)}
              accessibilityRole="button"
              hitSlop={12}
            >
              <Text style={estilos.reintentar}>Reintentar</Text>
            </Pressable>
            <Pressable
              onPress={() => void descartarUno(p.clienteId)}
              accessibilityRole="button"
              hitSlop={12}
            >
              <Text style={estilos.descartar}>Descartarlo</Text>
            </Pressable>
          </View>
        </View>
      ))}
    </View>
  );
}

const estilos = StyleSheet.create({
  contenedor: { gap: 10 },

  enCamino: {
    backgroundColor: colores.arena,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  presionado: { opacity: 0.7 },
  titulo: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.corteza },

  rechazado: {
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.terracota,
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 4,
  },
  tituloRechazado: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.terracotaProfunda,
  },
  motivo: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.texto },
  // 12 de padding + hitSlop 12 para llegar comodo a los 44pt de area tactil.
  acciones: { flexDirection: 'row', gap: 20, paddingTop: 4 },
  reintentar: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo, paddingVertical: 12 },
  descartar: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 14,
    color: colores.terracotaProfunda,
    paddingVertical: 12,
  },

  bajada: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave, marginTop: 2 },
});
