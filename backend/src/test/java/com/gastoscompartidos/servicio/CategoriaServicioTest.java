package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.CrearCategoriaRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las categorias por grupo (seccion 2.5), sin base de datos.
 *
 * Hermano de LiquidacionServicioTest: lo que un mock NO prueba es que el
 * indice compuesto {grupoId, nombre} exista de verdad, o que dos grupos
 * puedan tener cada uno su propio "cafe" sin chocar -- eso lo cubre el
 * smoke test contra Mongo real.
 */
class CategoriaServicioTest {

    private static final String GRUPO = "grupo-1";

    private CategoriaRepositorio categorias;
    private UsuarioActual usuarioActual;
    private CategoriaServicio servicio;

    @BeforeEach
    void preparar() {
        categorias = mock(CategoriaRepositorio.class);
        usuarioActual = mock(UsuarioActual.class);
        servicio = new CategoriaServicio(categorias, usuarioActual);

        Usuario franco = usuario("u-franco", GRUPO);
        when(usuarioActual.requerido()).thenReturn(franco);
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("solo las del grupo de quien pregunta")
        void soloLasDelGrupo() {
            when(categorias.findByGrupoIdOrderByNombreAsc(GRUPO)).thenReturn(List.of(
                    categoria("cat-1", GRUPO, "cafe", "coffee")));

            List<CategoriaRespuesta> r = servicio.listar();

            assertThat(r).hasSize(1);
            assertThat(r.get(0).nombre()).isEqualTo("cafe");
        }
    }

    @Nested
    @DisplayName("Crear")
    class Crear {

        @Test
        @DisplayName("guarda con el grupo de quien la crea")
        void guardaConElGrupo() {
            when(categorias.findByGrupoIdOrderByNombreAsc(GRUPO)).thenReturn(List.of());
            when(categorias.save(any(Categoria.class))).thenAnswer(inv -> {
                Categoria c = inv.getArgument(0);
                escribirCampo(c, "id", "cat-nueva");
                return c;
            });

            CategoriaRespuesta r = servicio.crear(new CrearCategoriaRequest("Netflix", "smartphone"));

            assertThat(r.nombre()).isEqualTo("Netflix");
            assertThat(r.icono()).isEqualTo("smartphone");

            ArgumentCaptor<Categoria> captura = ArgumentCaptor.forClass(Categoria.class);
            verify(categorias).save(captura.capture());
            assertThat(captura.getValue().getGrupoId()).isEqualTo(GRUPO);
        }

        @Test
        @DisplayName("un nombre repetido en el mismo grupo se rechaza, sin importar mayusculas")
        void nombreRepetidoSeRechaza() {
            when(categorias.findByGrupoIdOrderByNombreAsc(GRUPO)).thenReturn(List.of(
                    categoria("cat-1", GRUPO, "Netflix", "smartphone")));

            assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("netflix", "tag")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya tenés");

            verify(categorias, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Borrar")
    class Borrar {

        @Test
        @DisplayName("cualquiera se puede borrar, inclusive si parece una default")
        void cualquieraSePuedeBorrar() {
            when(categorias.countByGrupoId(GRUPO)).thenReturn(2L);
            when(categorias.deleteByIdAndGrupoId("cat-1", GRUPO)).thenReturn(1L);

            servicio.borrar("cat-1");

            verify(categorias).deleteByIdAndGrupoId("cat-1", GRUPO);
        }

        @Test
        @DisplayName("no deja borrar la ultima: el formulario de alta se quedaria sin chip")
        void noDejaBorrarLaUltima() {
            when(categorias.countByGrupoId(GRUPO)).thenReturn(1L);

            assertThatThrownBy(() -> servicio.borrar("cat-1"))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("última categoría");

            verify(categorias, never()).deleteByIdAndGrupoId(any(), any());
        }

        @Test
        @DisplayName("una categoria ajena o inexistente da 404, sin distinguir cual")
        void ajenaOInexistenteDa404() {
            when(categorias.countByGrupoId(GRUPO)).thenReturn(2L);
            when(categorias.deleteByIdAndGrupoId("cat-ajena", GRUPO)).thenReturn(0L);

            assertThatThrownBy(() -> servicio.borrar("cat-ajena"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    private static Usuario usuario(String id, String grupoId) {
        Usuario u = new Usuario("Franco", "franco@local", "hash", grupoId);
        escribirCampo(u, "id", id);
        return u;
    }

    private static Categoria categoria(String id, String grupoId, String nombre, String icono) {
        Categoria c = new Categoria(grupoId, nombre, icono);
        escribirCampo(c, "id", id);
        return c;
    }

    private static void escribirCampo(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
