# Casos de Prueba - Reportes Dinámicos con IA

El objetivo de este documento es detallar múltiples escenarios ("edge cases") de prueba para la funcionalidad de Reportes con IA. Estos casos evaluarán cómo el backend maneja consultas complejas o incompletas, exigiendo que la IA solicite la información faltante o reconozca correctamente los filtros implícitos.

## 1. Casos de Información Incompleta (Para probar `preguntas_faltantes`)

### Prueba 1.1: Consulta extremadamente vaga
**Prompt:** "Quiero un reporte de los tramites"
**Comportamiento Esperado:** La IA NO debe asumir ningún tipo de reporte, rango de fechas, ni estado. Debe detectar el intent más general y generar un JSON con la lista `preguntas_faltantes` solicitando:
- ¿Qué tipo de trámites deseas ver? (ej. por estado, completados, por departamento)
- ¿Para qué rango de fechas?

### Prueba 1.2: Consulta ambigua sobre fechas
**Prompt:** "Muestrame los cuellos de botella"
**Comportamiento Esperado:** La IA detecta `CUELLOS_BOTELLA`. Sin embargo, falta una fecha. En lugar de alucinar "este mes", debe preguntar:
- ¿De qué período te gustaría analizar los cuellos de botella?

### Prueba 1.3: Faltan múltiples parámetros
**Prompt:** "Quiero saber el rendimiento exportado a pdf"
**Comportamiento Esperado:**
- `tipo_reporte`: `RENDIMIENTO_DEPARTAMENTO`
- `formato_exportacion`: `PDF`
- `preguntas_faltantes`: ["¿Sobre qué departamento deseas el reporte?", "¿En qué rango de fechas?"]

## 2. Casos de Múltiples Filtros e Instrucciones Combinadas

### Prueba 2.1: Consulta muy específica y completa
**Prompt:** "Por favor genérame un reporte de los trámites que han sido rechazados entre el 1 de enero y el 31 de marzo de este año en el departamento de Finanzas. Agrúpalos por semana y ordénalos por cantidad de forma descendente. Quiero descargar esto en excel."
**Comportamiento Esperado:**
- `tipo_reporte`: `TRAMITES_POR_ESTADO` (o `TASA_RECHAZO`)
- `filtros`: `{ "estado": "RECHAZADO", "fecha_inicio": "2026-01-01", "fecha_fin": "2026-03-31", "departamentoNombre": "Finanzas" }`
- `agrupacion`: `SEMANA`
- `ordenamiento`: `DESCENDENTE`
- `formato_exportacion`: `EXCEL`
- `preguntas_faltantes`: `[]`

### Prueba 2.2: Redirección de formato al final
**Prompt:** "Dame los clientes mas activos del ultimo semestre y agrupalos por mes. Espera, mejor ponlo en formato WORD."
**Comportamiento Esperado:**
- `tipo_reporte`: `CLIENTES_MAS_ACTIVOS`
- `agrupacion`: `MES`
- `formato_exportacion`: `WORD`

## 3. Casos "Edge Cases" o Engañosos

### Prueba 3.1: Vocabulario coloquial o inusual
**Prompt:** "Che, sácame la data de quiénes son los que más trámites hacen, o sea los top usuarios en lo que va del año."
**Comportamiento Esperado:**
- `tipo_reporte`: `CLIENTES_MAS_ACTIVOS`
- `filtros`: `{ "fecha_inicio": "2026-01-01", "fecha_fin": "..." }`
- `preguntas_faltantes`: `[]`

### Prueba 3.2: Contradicciones en el mismo prompt
**Prompt:** "Quiero el reporte de trámites completados, no perdón, mejor de los rechazados de ayer."
**Comportamiento Esperado:** La IA debería captar la corrección ("rechazados de ayer") y no procesar "completados".
- `filtros`: `{ "estado": "RECHAZADO", "fecha_inicio": "[fecha de ayer]", "fecha_fin": "[fecha de ayer]" }`

### Prueba 3.3: Solicitud fuera del dominio (Out-of-domain)
**Prompt:** "Dime la receta para hacer una torta de chocolate y exportalo a PDF"
**Comportamiento Esperado:** El sistema puede fallar con confianza baja o la IA debe responder que no entiende la consulta en el contexto de reportes, generando preguntas o detectándolo como inválido.

## 4. Pruebas de Exportación y Visualización (Frontend)

- **Voz y Dictado:** Hacer pausas al hablar (ej. "Quiero los tramites... [pausa 3s]... completados"). La interfaz de voz debería añadir texto sin cortar abruptamente.
- **Visualización Previa:** Verificar que antes de exportar, la tabla aparezca bajo el título "Vista Previa del Reporte" en la pantalla de Resultados.
- **Exportación Excel:** Al presionar "Exportar Excel", debe generar un archivo `.xlsx` (real, no CSV) que pueda abrirse nativamente sin problemas de delimitadores.
- **Exportación PDF:** Al presionar "Exportar PDF", debe generar un documento PDF formal usando `jspdf-autotable` con una tabla delineada y un título descriptivo en lugar de abrir la ventana de impresión del navegador.
