# Las nutrias

Cuatro PNG con fondo transparente, una por estado de animo mas la de la pareja.
Los consumen `mobile/` y, mas adelante, `web/`.

| Archivo | Pose | Cuando se muestra |
|---|---|---|
| `contenta.png` | Flotando de espaldas | `animo = CONTENTA` |
| `tranquila.png` | Sentada, manitos juntas | `animo = TRANQUILA` |
| `preocupada.png` | Sentada, cejas caidas | `animo = PREOCUPADA` |
| `nosotros.png` | Las dos de la mano en el agua | Pantalla "Nosotros" |

El animo lo decide el backend en `GET /gastos/resumen`. El cliente solo elige
el archivo: ver `CalculadorDeAnimo` y la seccion del animo en `CLAUDE.md`.

Guardarlas exportadas a 1024x1024, PNG con transparencia. No versionar los
originales de 4K aca; si hacen falta, van en Drive.
