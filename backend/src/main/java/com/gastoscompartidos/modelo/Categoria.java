package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Una categoria de gasto. ES DE UNA PERSONA, no de un grupo.
 *
 * Tercer alcance que tiene este documento, y cada cambio salio de usar la app:
 *
 *  1. GLOBALES (hasta la seccion 2.5): seis documentos para toda la base.
 *  2. POR GRUPO (2.5 a 2.8): cada grupo con su lista. Resolvio que un
 *     desconocido heredara las categorias de Franco y Viole, pero dejo un
 *     problema adentro de la pareja: lo que creaba Viole le aparecia a Franco
 *     en su formulario, y borrar una se la borraba a los dos.
 *  3. POR USUARIO (seccion 2.9, esta): cada persona arma su lista y la edita
 *     sin tocarle nada a la otra, aunque compartan grupo.
 *
 * Por que funciona sin romper lo compartido: un gasto NO referencia a la
 * categoria, lleva un snapshot embebido (`ReferenciaCategoria`). Un COMPARTIDO
 * que cargo Viole con su "super" lo ve Franco con ese nombre e icono aunque el
 * no tenga ninguna categoria "super". Y "Por categoria" del resumen agrupa por
 * nombre normalizado (seccion 2.8), asi que el "cafe" de cada uno cae en la
 * misma fila.
 *
 * Como ya no cuelga del grupo, la categoria sigue a la persona cuando se suma a
 * un grupo o sale de uno: `GrupoServicio` no tiene nada que mover.
 *
 * EL INDICE ES PARCIAL, y no por prolijidad. Los documentos anteriores a esta
 * seccion no tienen `usuario_id` hasta que corre `MigracionDeCategorias`, y
 * Spring crea los indices ANTES de que corra cualquier `ApplicationRunner`. Con
 * un unico comun, el "cafe" sin duenio de un grupo y el "cafe" sin duenio de
 * otro serian dos veces la clave {null, "cafe"}: la creacion del indice
 * fallaria y la app no arrancaria. El filtro deja afuera del indice a los que
 * todavia no tienen duenio.
 */
@CompoundIndex(name = "idx_categoria_usuario_nombre", def = "{'usuario_id': 1, 'nombre': 1}",
        unique = true, partialFilter = "{'usuario_id': {$exists: true}}")
@Document(collection = "categoria")
public class Categoria {

    @Id
    private String id;

    @Field("usuario_id")
    private String usuarioId;

    /**
     * Unico POR PERSONA (ver el indice de la clase): Franco y Viole pueden tener
     * cada uno su "cafe" sin chocar.
     */
    private String nombre;

    /**
     * El nombre de un icono de Lucide ("coffee", "car", "utensils") para las
     * seis categorias historicas de Franco y Viole, o un emoji suelto ("🍕")
     * para cualquier categoria creada desde la seccion 2.7 en adelante -- ver
     * `IconoCategoria.tsx`, que intenta Lucide primero y cae a texto/emoji si
     * el nombre no es uno conocido. El backend no valida el contenido: lo que
     * el cliente manda es lo que se guarda.
     */
    private String icono;

    protected Categoria() {
    }

    public Categoria(String usuarioId, String nombre, String icono) {
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.icono = icono;
    }

    public String getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getIcono() {
        return icono;
    }

    public void setIcono(String icono) {
        this.icono = icono;
    }
}
