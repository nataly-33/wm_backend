package com.workflow.formulario.model;

public enum TipoCampo {
    TEXTO_CORTO,
    AREA_TEXTO,
    ETIQUETA,        // solo texto informativo, sin input
    NUMERO,
    FECHA,
    SELECTOR,        // desplegable
    RADIO,           // botones de opción
    CHECKBOX,        // casillas múltiples
    ARCHIVO,
    IMAGEN,
    TABLA_GRID       // grilla de datos tabulares
}
