# secrets/

Este directorio contiene credenciales sensibles que NO se suben a git.

## firebase-service-account.json

El backend necesita este archivo para enviar push notifications via FCM.

### Cómo obtenerlo:
1. Ir a https://console.firebase.google.com/
2. Seleccionar el proyecto `wm-mobile-2bae8`
3. Ir a: Configuración del proyecto (icono engranaje) > Cuentas de servicio
4. Clic en "Generar nueva clave privada"
5. Guardar el JSON descargado en esta carpeta como: `firebase-service-account.json`

### Alternativa con .env:
Si el archivo está en otra ruta, crear `wm_backend/.env` con:
```
FIREBASE_CREDENTIALS=C:/ruta/completa/al/archivo.json
```

El backend carga `.env` automáticamente al iniciar.
