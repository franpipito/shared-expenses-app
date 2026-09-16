# Apuntes de aprendizaje

Notas de estudio de lo que voy usando en este proyecto. La idea no es copiar
documentación oficial —para eso está la documentación oficial— sino dejar
asentado **por qué** se usa cada cosa y **qué decisión resolvía**, con los
ejemplos concretos de este repo.

Cada nota tiene al final una sección de **"lo que tengo que poder explicar"**,
que es el filtro real: si no puedo explicar eso en voz alta, no lo entendí.

## Notas

| Tema | Estado |
|---|---|
| [Docker](docker.md) | ✅ |
| [`BigDecimal` y el dinero en Java](bigdecimal.md) | ✅ |
| [JPA e Hibernate](jpa-hibernate.md) | 📦 histórica — el backend dejó Postgres en la 6.5 |
| MongoDB y Spring Data: documentos, snapshots y lo que se perdió | pendiente |
| Spring Boot: cómo arranca y qué es la autoconfiguración | pendiente |
| Maven y el wrapper | pendiente |
| Testing: JUnit, y cuándo un mock prueba algo | pendiente |
| React web y TypeScript | pendiente (etapa web) |

## Cómo leer esto

Los apuntes van creciendo junto con el proyecto: cada sesión que suma un tema
nuevo, suma o amplía una nota. No están escritos para ser leídos de corrido,
sino para volver a ellos cuando algo no cierra.

Referencias cruzadas: `CLAUDE.md` en la raíz tiene las **decisiones** de diseño
del proyecto; estos apuntes tienen los **conceptos**. Si buscás "por qué
elegimos guardar el reparto resuelto", eso está en `CLAUDE.md`. Si buscás "qué
es un `RoundingMode`", está acá.

Las notas marcadas 📦 describen algo que el proyecto ya no usa. Se conservan
porque entender qué se dejó atrás es parte de poder defender la decisión de
haberlo dejado.
