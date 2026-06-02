package com.workflow.ejecucion.model;

public enum FaseNodo {
    ESPERANDO_CLIENTE,      // el agente está recopilando datos del cliente
    ESPERANDO_FUNCIONARIO,  // cliente terminó, el funcionario debe revisar y aprobar
    COMPLETADA,             // funcionario aprobó — nodo terminado
    RECHAZADA               // funcionario rechazó
}
