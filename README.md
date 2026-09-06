# Gastos compartidos

App de gastos compartidos entre dos personas. Cada gasto es personal o
compartido, y el sistema calcula en todo momento quien le debe a quien.

## Stack

- **Backend:** Java 21 + Spring Boot 4.1.1 + PostgreSQL 17
- **Mobile:** Expo + React Native + TypeScript *(pendiente)*
- **Web:** React + Vite + TypeScript + Tailwind *(pendiente)*

## Requisitos

- JDK 21 (Temurin) con `JAVA_HOME` configurado
- Docker Desktop

Maven no hace falta instalarlo: el repo trae el wrapper (`mvnw` / `mvnw.cmd`).

## Levantar en local

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. La base en `localhost:5432`
(db `gastos`, usuario `gastos`, password `gastos_local`).

## Configuracion

`backend/src/main/resources/application.properties` usa la sintaxis
`${VARIABLE:default}`: en local toma los defaults, y en el entorno de deploy
las variables de entorno tienen prioridad. Variables relevantes:

| Variable      | Default                                     |
|---------------|---------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/gastos`   |
| `DB_USER`     | `gastos`                                    |
| `DB_PASSWORD` | `gastos_local`                              |
| `DDL_AUTO`    | `update`                                    |

## Modelo de datos

```
Grupo 1---N Usuario
Grupo 1---N Gasto      N---1 Usuario (pagado_por)
                       N---1 Categoria
```

`Gasto` guarda `monto` y `montoPagador` (la parte que le corresponde a quien
pago). La deuda que ese gasto genera a favor del pagador es
`monto - montoPagador`. Ver `CLAUDE.md` para el razonamiento detras de esa
decision.
