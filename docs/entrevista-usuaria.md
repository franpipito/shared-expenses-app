# Entrevista con la usuaria

Relevamiento de necesidades con la usuaria real de la app. El objetivo es que
las decisiones de producto salgan de como ella maneja su plata, y no de lo que
nosotros suponemos.

Motivacion que expreso espontaneamente: **tiene muchos gastos hormiga y quiere
saber en que se le va la plata.** Nunca uso una app de finanzas.

---

## Respuestas

### Como es su plata hoy

**1. Cuando le entra la plata**
> En momentos distintos: es freelance, no tiene sueldo fijo mensual.

**2. Con que paga**
> Mercado Pago, casi excluyentemente. Tambien efectivo (ver respuesta 7).

**3. Cuotas**
> No tiene nada en cuotas.

**4. Gastos fijos mensuales**
> _(no se pregunto; ella menciono al pasar que "tiene algunos gastos fijos")_

### Gastos hormiga

**5. Tres gastos hormiga de esta semana**
> - gomitas en la facu 2500
> - desayuno en la facu 3008
> - merienda entre la facu y el evento de networking 4400
>
> "Si bien el desayuno y la merienda los necesitaba, me podria haber organizado
> mejor y sumar tuppers aparte del almuerzo para evitar ese gasto."

**6. Como se da cuenta de que se le fue la plata**
> "Mas a fin de mes, cuando me quede sin y todavia falta para cobrar."

**7. Intentos previos de anotar gastos, y por que los dejo**
> "Intente una vez en excel, pero no soy buena con las formulas y eso entonces
> era medio un quilombo; aparte excel es poco practico para abrir en el celu,
> anotar en las celdas monto item, etc."

**8. Que haria distinto si supiera en que se le va**
> "Generalmente se en que se me va. Uber (la mayoria los tomo por seguridad asi
> que es dificil suprimirlos salvo que deje de hacer planes), podria organizarme
> distinto cuando estoy mil horas en capital y llevarme mas tuppers porque soy
> muy hambrienta."

### Si tuviera que anotarlos

**9. Cuando anotaria un cafe**
> "Lo anotaria en el momento, una vez que lo pague, mientras espero que lo hagan."

**10. Las tres unicas cosas que anotaria de cada gasto**
> "Anotaria en lo que gaste ej: cafe, uber, comida, ropa, regalos; el monto de
> plata y algo que me recuerde el momento; ej: desayuno facultad 3000. Esto me
> permite saber el monto obviamente, en que lo gaste, y lo que me recuerda el
> momento me permite saber si fue un gasto hormiga o no. Si pongo 4000 Uber
> cumple guada, se que no habia otra opcion, pero si me tomo un uber a las 15hs
> para ir de lo de mi viejo a lo de mi vieja es hormiga."

**11. Si anotaria el efectivo**
> "Efectivo y plata digital, todo es plata."

**12. Que le daria mas fiaca: olvidarse, o que anotar sea largo**
> "Que anotar sea largo."

### Que le gustaria ver

**13. El primer numero que querria ver a fin de mes**
> "Me gustaria poder etiquetar mis gastos, cuando cargo uno que es hormiga, asi
> a fin de mes lo primero que me aparece es un total de esos gastos y puedo
> replantearme porque los hice e intentar reducirlos."

**14. Limite de gasto mensual**
> "Creo que es dificil porque no tengo un ingreso fijo, y si bien tengo algunos
> gastos fijos hay veces que gasto plata sobre todo en ubers que si fuera por mi
> no la gastaria pero no me queda opcion."

**15. Un gasto que paga Franco: nuestro o de Franco**
> "Si lo veo desde algo general es un gasto nuestro, porque algunas veces pago yo
> y otras vos, pero al tener ingresos diferentes y separados creo que impacta
> como un gasto individual."

### Para preguntar en persona, no por escrito

**16. Hay gastos suyos que preferiria que Franco no vea**
> "Si, mas que nada cuando te hago regalitos."

**17. Le resultaria incomodo compartir esto**
> "No, pero me gustaria que tenga dos secciones: una personal y una de pareja."

**18. Le serviria igual si la app fuera solo suya**
> "No entiendo bien como es, porque es como economia compartida, pero nosotros no
> tenemos eso."

---

## Hallazgos

### "Gasto hormiga" no es un monto chico ni una categoria: es un juicio de evitabilidad

De la respuesta 10: el mismo Uber, por el mismo monto, es necesario si era por
seguridad y es hormiga si era por comodidad. Y de la 5: el desayuno lo
*necesitaba*, pero era evitable organizandose con tuppers.

Consecuencia dura: **la marca de hormiga no puede vivir en la categoria ni
deducirse del monto.** Es una propiedad de cada gasto individual, que solo ella
puede determinar, y en el momento de cargarlo.

### El valor de la app no es descubrir en que se le va, es cuantificar lo evitable

Respuesta 8: "generalmente se en que se me va". No tiene un problema de
visibilidad. Tiene un problema de **magnitud**: no sabe cuanto suma la parte que
podria no haber gastado. Eso es lo que le permitiria "replantearse" (respuesta 13).

### La feature central la pidio ella, sin que se la sugirieramos

Respuesta 13: etiquetar el gasto como hormiga al cargarlo, y ver el **total
mensual de hormigas** como primer numero de la app. Eso es el producto.

### La velocidad de carga es el requisito duro

Respuesta 9: carga parada en el mostrador, mientras esperan que le preparen el
pedido. Respuesta 12: prefiere olvidarse un gasto antes que anotar lento.
Respuesta 7: abandono Excel en parte porque era incomodo en el celular.

Tres campos y nada mas: **categoria, monto, descripcion.**

### La usuaria dicto la arquitectura de la app: dos secciones

Respuesta 17: "una personal y una de pareja". Eso mapea exactamente sobre el enum
`TipoGasto` que ya teniamos. La decision de guardar `tipo` explicito (en lugar de
deducirlo de los montos) pasa de ser una prolijidad a ser el eje del producto:
**el mismo campo define en que seccion aparece el gasto y quien puede verlo.**

### La privacidad que pide es concreta y acotada

Respuesta 16: quiere ocultar los regalos que le compra a Franco. No es un pedido
de privacidad financiera general -- dijo explicitamente que compartir no le
incomoda. Es un pedido de que las sorpresas sigan siendo sorpresas.

La regla mas simple que lo cubre: **un gasto PERSONAL lo ve solo su dueno; un
gasto COMPARTIDO lo ven los dos.** Sin excepciones ni flags extra.

### No tienen economia compartida, y eso cambia el encuadre

Respuesta 18: "es como economia compartida, pero nosotros no tenemos eso". Sumado
a la 15 ("ingresos diferentes y separados"), el encuadre correcto de la seccion
de pareja **no** es "nuestra plata" sino "quien le debe a quien" -- que es
justamente para lo que sirve el saldo cuando las finanzas estan separadas.

Ojo con el peso de esta respuesta: nunca vio la app, asi que es evidencia debil
sobre si la usaria, pero evidencia fuerte sobre como piensa sus finanzas.

### La descripcion no es opcional

Respuesta 10: "algo que me recuerde el momento". Es uno de los tres campos que
ella misma eligio, y es lo que le da sentido al gasto despues.

---

## Consecuencias de diseno

### Cerradas

- **Sin cuotas -> el modelo de `Gasto` se sostiene tal cual esta.** Un gasto es
  una fecha y un monto. Era el mayor riesgo abierto y quedo descartado.
- **No hace falta modelar cuentas ni medios de pago.** Respuesta 11: "todo es
  plata". No distingue efectivo de digital.
- **El recorte mensual se sostiene.** Aunque el ingreso sea irregular, ella
  misma piensa la revision "a fin de mes" (respuesta 13). `?mes=` se queda.
- **Presupuestos quedan descartados, y no solo por alcance.** Respuesta 14: un
  tope fijo mensual no le sirve con ingreso variable y con gastos que no puede
  evitar.
- **La app es individual primero, de pareja despues.** Respuesta 15: conceptualmente
  el gasto es "nuestro", pero al tener ingresos separados lo vive como individual.

### Abiertas

- **Como modelar la marca de hormiga.** Booleano en `Gasto` vs. sistema de
  etiquetas generico. Ver discusion con Franco.
- **Categorias.** Las que nombro ella son: cafe, uber, comida, ropa, regalos.
  No coinciden con la lista que veniamos asumiendo (comida, transporte,
  servicios, ocio, salud, otros). Ajustar el seed en la sesion 2 usando sus
  palabras.
- **Visibilidad resuelta.** Un gasto PERSONAL lo ve solo su dueno; uno COMPARTIDO
  lo ven los dos. `GET /gastos` filtra en consecuencia (respuestas 16 y 17).
- **La app tiene dos secciones**, personal y pareja, gobernadas por `tipo`
  (respuesta 17). Abre en la personal.
- **El reparto 50/50 por defecto puede no ser lo justo para ellos.** Respuesta 15
  menciona "ingresos diferentes y separados". Es una conversacion entre ellos,
  no una decision tecnica, pero conviene tenerla antes de fijar el default.
