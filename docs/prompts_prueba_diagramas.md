# 🎤 Prompts de Prueba — Generación de Diagramas con IA/NLP

> Estos prompts están escritos como se dirían por voz con Web Speech API.
> Los primeros 4 replican las políticas del seeder. Los últimos 4 son nuevos.

---

## Prompt 1 — Instalación de Nuevo Medidor
**Patrón:** Lineal + Decisión con 2 ramas (aprobado/rechazado)
**Departamentos:** Atención al Cliente, Técnico, Facturación, Legal
**Nodos esperados:** ~11 (INICIO, 7 TAREA, 1 DECISION, 2 FIN)

```
Atención al cliente recibe la solicitud del cliente, luego verifica la documentación,
si la documentación está completa se aprueba y el departamento técnico realiza la
inspección técnica, después el técnico genera el presupuesto, luego facturación
registra el pago, y finalmente legal firma el contrato. Si la documentación no está
completa, atención al cliente notifica al cliente el rechazo.
```

---

## Prompt 2 — Reconexión de Servicio
**Patrón:** Fork/Join paralelo + Decisión final
**Departamentos:** Atención al Cliente, Técnico, Facturación
**Nodos esperados:** ~11 (INICIO, 4 TAREA, 2 PARALELO, 1 DECISION, 2 FIN)

```
Atención al cliente recibe la solicitud de reconexión, luego simultáneamente
facturación verifica la deuda pendiente y el departamento técnico verifica el
estado técnico de la instalación. Cuando ambas verificaciones terminan, el
técnico decide si todo está aprobado. Si es aprobado, el técnico ejecuta la
reconexión del servicio. Si es rechazado, atención al cliente informa el
impedimento al socio.
```

---

## Prompt 3 — Baja de Servicio
**Patrón:** Lineal simple multi-departamento (sin decisiones)
**Departamentos:** Atención al Cliente, Facturación, Técnico, Legal
**Nodos esperados:** ~6 (INICIO, 4 TAREA, 1 FIN)

```
Atención al cliente recibe la solicitud de baja del servicio, luego facturación
verifica que el saldo esté en cero, después el departamento técnico retira el
medidor, y finalmente el departamento legal liquida el contrato.
```

---

## Prompt 4 — Reclamo por Facturación
**Patrón:** Decisión doble con ciclo de revisión
**Departamentos:** Atención al Cliente, Facturación, Recursos Humanos
**Nodos esperados:** ~10 (INICIO, 4 TAREA, 2 DECISION, 2 FIN)

```
Atención al cliente registra el reclamo del socio, luego facturación analiza el
consumo histórico y decide si se confirma el error. Si se confirma el error,
facturación emite una nota de crédito. Si no se confirma, atención al cliente
explica la situación al cliente, y el cliente decide si acepta la explicación.
Si el cliente acepta, se cierra el caso. Si el cliente no acepta, recursos
humanos eleva el caso al supervisor para una nueva revisión.
```

---

## Prompt 5 — Cambio de Titularidad del Medidor ⭐ NUEVA
**Patrón:** Lineal + Decisión documental
**Departamentos:** Atención al Cliente, Legal, Facturación
**Nodos esperados:** ~9 (INICIO, 5 TAREA, 1 DECISION, 2 FIN)

```
El nuevo titular presenta la solicitud de cambio de titularidad en atención al
cliente con todos sus documentos, luego atención al cliente verifica los documentos
de identidad y el poder notarial. Si la documentación es válida, el departamento
legal elabora el nuevo contrato de servicio, después facturación actualiza los
datos de facturación del socio, y atención al cliente entrega la documentación
final al nuevo titular. Si la documentación no es válida, atención al cliente
notifica los documentos faltantes al solicitante.
```

---

## Prompt 6 — Reporte de Corte de Luz por Emergencia ⭐ NUEVA
**Patrón:** Lineal con decisión técnica
**Departamentos:** Atención al Cliente, Técnico, Recursos Humanos
**Nodos esperados:** ~8 (INICIO, 4-5 TAREA, 1 DECISION, 2 FIN)

```
Atención al cliente recibe el reporte de corte de luz de emergencia y registra la
dirección afectada, luego el departamento técnico realiza el diagnóstico en campo
para identificar la causa del corte. El técnico decide si la reparación es viable
de forma inmediata. Si es viable, el técnico ejecuta la reparación y restaura el
servicio eléctrico. Si no es viable, recursos humanos coordina una cuadrilla
especializada para la intervención mayor.
```

---

## Prompt 7 — Solicitud de Medidor Prepago ⭐ NUEVA
**Patrón:** Fork/Join paralelo + Lineal
**Departamentos:** Atención al Cliente, Técnico, Facturación
**Nodos esperados:** ~9 (INICIO, 4 TAREA, 2 PARALELO, 1 FIN)

```
Atención al cliente recibe la solicitud de cambio a medidor prepago del socio,
luego en paralelo el departamento técnico evalúa la compatibilidad técnica de
la instalación y facturación calcula el costo del cambio de medidor. Cuando
ambas evaluaciones terminan, el técnico realiza la instalación del nuevo medidor
prepago en el domicilio del cliente, y finalmente facturación activa el sistema
de recarga prepago en la cuenta del socio.
```

---

## Prompt 8 — Denuncia por Robo de Cable Eléctrico ⭐ NUEVA
**Patrón:** Lineal + Decisión con rama legal
**Departamentos:** Atención al Cliente, Técnico, Legal, Recursos Humanos
**Nodos esperados:** ~10 (INICIO, 6 TAREA, 1 DECISION, 2 FIN)

```
Atención al cliente recibe la denuncia por robo de cable eléctrico y registra la
ubicación del incidente, luego el departamento técnico realiza la inspección del
área afectada y documenta los daños con fotografías. El técnico decide si el daño
afecta el suministro de los vecinos. Si afecta el suministro, el técnico ejecuta
la reparación de emergencia del cableado, luego legal elabora el informe policial
para la denuncia formal, y recursos humanos coordina seguridad para patrullaje en
la zona. Si no afecta el suministro, el técnico programa la reparación para la
próxima semana.
```

---

## 📋 Resumen de patrones cubiertos

| # | Política | Patrón | Complejidad |
|---|---|---|---|
| 1 | Instalación de Medidor | Lineal + Decisión | ⭐⭐ |
| 2 | Reconexión de Servicio | Fork/Join + Decisión | ⭐⭐⭐ |
| 3 | Baja de Servicio | Lineal simple | ⭐ |
| 4 | Reclamo Facturación | Doble decisión + ciclo | ⭐⭐⭐ |
| 5 | Cambio Titularidad | Lineal + Decisión documental | ⭐⭐ |
| 6 | Corte de Emergencia | Lineal + Decisión técnica | ⭐⭐ |
| 7 | Medidor Prepago | Fork/Join sin decisión | ⭐⭐⭐ |
| 8 | Robo de Cable | Lineal + Decisión + multi-depto | ⭐⭐⭐ |

> [!TIP]
> Para probar, crea una política nueva con cada nombre, abre el editor de diagrama, haz clic en **"Generar con IA"** y dicta o pega el prompt correspondiente.
