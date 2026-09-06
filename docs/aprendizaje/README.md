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
| [JPA e Hibernate](jpa-hibernate.md) | ✅ |
| [`BigDecimal` y el dinero en Java](bigdecimal.md) | ✅ |
| Spring Boot: cómo arranca y qué es la autoconfiguración | pendiente |
| Maven y el wrapper | pendiente |
| PostgreSQL: lo que usamos | pendiente |
| Testing con JUnit | pendiente (después de la sesión 3) |
| React web y TypeScript | pendiente (etapa web) |

## Cómo leer esto

Los apuntes van creciendo junto con el proyecto: cada sesión que suma un tema
nuevo, suma o amplía una nota. No están escritos para ser leídos de corrido,
sino para volver a ellos cuando algo no cierra.

Referencias cruzadas: `CLAUDE.md` en la raíz tiene las **decisiones** de diseño
del proyecto; estos apuntes tienen los **conceptos**. Si buscás "por qué
elegimos guardar el reparto resuelto", eso está en `CLAUDE.md`. Si buscás "qué
es `@ManyToOne`", está acá.
