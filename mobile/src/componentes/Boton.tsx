import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

import { colores } from '../tema/colores';
import { fuentes } from '../tema/tipografia';

/**
 * El boton de accion principal. Terracota, ancho, abajo.
 *
 * `docs/diseno.md`: un solo boton terracota por pantalla. Si hay dos, ninguno es
 * el principal. Y nunca ambar: el ambar es del gasto hormiga.
 *
 * Se usa `Pressable` y no `TouchableOpacity`: el segundo esta en camino a
 * deprecarse y `Pressable` permite estilos en funcion del estado, que es como se
 * hace el color de "presionado" sin animaciones.
 */
type Props = {
  titulo: string;
  onPress: () => void;
  cargando?: boolean;
  deshabilitado?: boolean;
};

export function Boton({ titulo, onPress, cargando = false, deshabilitado = false }: Props) {
  const inactivo = deshabilitado || cargando;

  return (
    <Pressable
      onPress={onPress}
      disabled={inactivo}
      accessibilityRole="button"
      accessibilityState={{ disabled: inactivo, busy: cargando }}
      style={({ pressed }) => [
        estilos.base,
        pressed && !inactivo && { backgroundColor: colores.terracotaProfunda },
        inactivo && { opacity: 0.5 },
      ]}
    >
      {cargando ? (
        <ActivityIndicator color={colores.tarjeta} />
      ) : (
        <Text style={estilos.texto}>{titulo}</Text>
      )}
    </Pressable>
  );
}

const estilos = StyleSheet.create({
  base: {
    backgroundColor: colores.terracota,
    borderRadius: 14,
    // 52 de alto no es estetico: iOS pide 44pt minimo de area tactil, y esta app
    // se usa parada en un mostrador, con una mano.
    minHeight: 52,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  texto: {
    fontFamily: fuentes.cuerpoBold,
    fontSize: 17,
    color: colores.tarjeta,
  },
});
