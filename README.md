# Gastos compartidos

App de gastos compartidos entre dos personas. Cada gasto es personal o
compartido, y el sistema calcula en todo momento quien le debe a quien.

## Stack

- **Backend:** Java 21 + Spring Boot 4.1.1 + MongoDB 8
- **Mobile:** Expo + React Native + TypeScript
- **Web:** React + Vite + TypeScript + Tailwind *(pendiente)*
- **Deploy:** Render (Docker) + MongoDB Atlas. Ver `docs/deploy.md`

## Requisitos

- JDK 21 (Temurin) con `JAVA_HOME` configurado
- Docker Desktop

Maven no hace falta instalarlo: el repo trae el wrapper (`mvnw` / `mvnw.cmd`).

## Levantar en local

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. La base en `localhost:27017`
(db `gastos`, usuario `gastos`, password `gastos_local`).

Las categorias las siembra `SembradorDeCategorias` al arrancar; los usuarios
se crean con `POST /auth/registro`. No hay script de seed que correr.

## Configuracion

`backend/src/main/resources/application.properties` usa la sintaxis
`${VARIABLE:default}`: en local toma los defaults, y en el entorno de deploy
las variables de entorno tienen prioridad. Variables relevantes:

| Variable             | Default                                            |
|----------------------|----------------------------------------------------|
| `MONGO_URI`          | `mongodb://gastos:gastos_local@localhost:27017/...` |
| `JWT_SECRETO`        | uno de desarrollo, publicado en el repo            |
| `CODIGO_INVITACION`  | `nutrias`                                          |
| `ZONA_HORARIA`       | `America/Argentina/Buenos_Aires`                   |
| `JWT_DURACION_DIAS`  | `30`                                               |

Con el perfil `produccion` activo, `ValidacionDeConfiguracion` **impide que la
app arranque** si `JWT_SECRETO`, `CODIGO_INVITACION` o `MONGO_URI` quedaron en
sus valores de desarrollo.

## Modelo de datos

```
grupo      { _id, nombre }
usuario    { _id, grupo_id, nombre, email, password_hash, token_version }
categoria  { _id, nombre, icono }
gasto      { _id, grupo_id, monto, monto_pagador, tipo, fecha, es_hormiga,
             pagadoPor: { usuarioId, nombre },        <- snapshot embebido
             categoria: { categoriaId, nombre, icono } }
```

`pagadoPor` y `categoria` van **embebidos** y no como referencias: listar los
gastos del mes es una sola lectura, sin `$lookup` y sin N+1 posible.

`Gasto` guarda `monto` y `montoPagador` (la parte que le corresponde a quien
pago). La deuda que ese gasto genera a favor del pagador es
`monto - montoPagador`. Ver `CLAUDE.md` para el razonamiento detras de esa
decision.
