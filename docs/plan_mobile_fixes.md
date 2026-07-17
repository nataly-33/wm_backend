# Plan de Correcciones - wm_mobile (Flutter)
Fecha: 2026-06-05
Alcance: 5 problemas criticos identificados en analisis previo

## PROBLEMA 1: Notificaciones Push del Cliente
Causa raiz: AgenteService.notificarClienteDecision no llama a pushNotificacionService para FCM cliente.
Archivos: AgenteService.java (backend), notification_service.dart (frontend)

## PROBLEMA 2: Campos tipados en chat del agente  
Causa raiz: ChatAgenteScreen no procesa campoMeta del WS para tipado de campos.
Archivos: chat_agente_screen.dart

## PROBLEMA 3: Foto cierra la app
Causa raiz: Falta permiso CAMERA en AndroidManifest, sin menu de 3 opciones.
Archivos: AndroidManifest.xml, chat_agente_screen.dart

## PROBLEMA 4: Microfono
Causa raiz: Falta permiso RECORD_AUDIO en AndroidManifest.
Archivos: AndroidManifest.xml, chat_agente_screen.dart

## PROBLEMA 5: Vista Funcionario
Estado: Ya implementado. Mejorar _buildCampoCliente.
Archivos: ejecutar_tarea_screen.dart
