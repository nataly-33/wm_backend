# Guía de Despliegue en AWS EC2 (Todo en Uno)

Esta guía te mostrará paso a paso cómo desplegar los 4 componentes principales de tu proyecto (Frontend, Backend, AI, y OnlyOffice) en una sola instancia de **AWS EC2** utilizando Docker Compose.

---

## PASO 1: Preparar la Instancia EC2 en AWS

1. Ingresa a la consola de [AWS (Amazon Web Services)](https://aws.amazon.com/es/console/).
2. Busca el servicio **EC2** y haz clic en **Launch Instance** (Lanzar Instancia).
3. **Nombre:** Ponle un nombre (ej. `WorkflowManager-Server`).
4. **AMI (Sistema Operativo):** Selecciona **Ubuntu Server 22.04 LTS** (o 24.04).
5. **Tipo de instancia:** Te recomiendo mínimo una **t3.medium** o **t3.large** (la IA, OnlyOffice y el backend en Java consumen bastante memoria RAM. Una `t2.micro` gratuita se quedará sin memoria y fallará).
6. **Key Pair (Par de claves):** Crea uno nuevo si no tienes (ej. `workflow-key`). Guárdalo bien, lo usarás para conectarte.
7. **Network Settings (Configuración de red):**
   - Habilita **Permitir tráfico SSH** (puerto 22).
   - Habilita **Permitir tráfico HTTP y HTTPS** (puertos 80 y 443).
   - Haz clic en **Edit** para añadir reglas adicionales en el **Security Group**. Añade **Custom TCP** para los puertos:
     - `8080` (Para tu Backend).
     - `8001` (Para tu AI Microservice).
     - `8088` (Para OnlyOffice).
8. **Almacenamiento (Storage):** Incrementa a **20 GB o 30 GB** (las imágenes de docker son pesadas).
9. Haz clic en **Launch Instance**.

---

## PASO 2: Conectarse e Instalar Docker

Una vez que el estado de tu instancia sea *Running*, busca su **Dirección IPv4 Pública** (Ej: `54.123.45.67`).

1. Abre tu terminal (PowerShell o Git Bash) en la carpeta donde guardaste tu `workflow-key.pem`.
2. Conéctate a tu EC2:
   ```bash
   ssh -i "workflow-key.pem" ubuntu@3.85.237.101 
   ```
3. Instala Docker y Docker Compose corriendo estos comandos uno a uno:
   ```bash
   sudo apt update
   sudo apt install -y docker.io docker-compose-v2 git
   sudo usermod -aG docker ubuntu
   # Aplica el cambio de grupo
   newgrp docker
   ```

---

## PASO 3: Subir tu Código a la Instancia

Dado que tienes tu código en repositorios locales/privados, tienes dos opciones:

**Opción A (Recomendada): Usar Git**
Si tienes tus repositorios en GitHub/GitLab, simplemente clónalos en el servidor:
```bash
git clone https://github.com/tu-usuario/tu-repo.git workflow
cd workflow
```

**Opción B: Subir mediante SCP/SFTP desde tu PC**
Desde tu terminal de Windows (fuera del servidor):
```bash
scp -i "workflow-key.pem" -r C:\Users\Invitado\UAGRM\proyecto\workflow ubuntu@TU_IP_PUBLICA:/home/ubuntu/
```

---

## PASO 4: Configurar Variables y Entornos

> **¡IMPORTANTE!** Modificamos el proyecto para soportar compilación multi-etapa y Nginx. Ya no necesitas preocuparte por Azure, el Frontend se desplegará aquí mismo.

1. **Frontend (`wm_frontend/src/environments/environment.prod.ts`):**
   Cambia las URLs apuntando a tu nueva IP pública de EC2:
   ```typescript
   export const environment = {
     production: true,
     apiUrl: 'http://TU_IP_PUBLICA:8080/api/v1',
     wsUrl: 'ws://TU_IP_PUBLICA:8080/ws',
     onlyofficeUrl: 'http://TU_IP_PUBLICA:8088'
   };
   ```

2. **Mobile (`wm_mobile/.env.production`):**
   Actualiza para cuando compiles la app móvil de tus clientes:
   ```env
   API_BASE_URL=http://TU_IP_PUBLICA:8080/api/v1
   WS_URL=ws://TU_IP_PUBLICA:8080/ws-native
   ```

3. **Backend (`.env` raíz):**
   En tu instancia EC2, dentro de la carpeta `workflow`, crea un archivo llamado `.env` y coloca las variables de entorno reales:
   ```bash
   nano .env
   ```
   **Contenido:**
   ```env
   MONGODB_URI=tu_cadena_de_conexion_de_mongo_atlas
   JWT_SECRET=tu_clave_secreta_muy_segura_para_el_backend
   ONLYOFFICE_JWT_SECRET=tu_clave_secreta_de_onlyoffice_mayor_a_256_bits
   AWS_ACCESS_KEY_ID=tu_access_key
   AWS_SECRET_ACCESS_KEY=tu_secret_key
   AWS_REGION=us-east-1
   AWS_S3_BUCKET=tu-bucket-nombre
   ```
   *(Guarda con `Ctrl+O`, `Enter`, y sal con `Ctrl+X`)*.
---

## PASO 5: Desplegar Todo (La Magia)

En el directorio raíz del proyecto (`/home/ubuntu/workflow`), donde está el archivo `docker-compose.prod.yml` que creé para ti, ejecuta:

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

### ¿Qué hará esto?
1. **wm-backend:** Descargará Java/Maven, compilará tu código en un `.jar` y lo levantará en `http://TU_IP_PUBLICA:8080`.
2. **wm-frontend:** Compilará Angular para producción y servirá los archivos estáticos usando un servidor ultrarrápido **Nginx** en `http://TU_IP_PUBLICA:80`.
3. **wm-ai:** Instalará las dependencias de Python y dejará el microservicio escuchando en `http://TU_IP_PUBLICA:8001`.
4. **onlyoffice:** Lanzará el document server colaborativo en `http://TU_IP_PUBLICA:8088`.

docker compose -f docker-compose.prod.yml up -d                                                                        

Puedes ver el estado de los contenedores con:
```bash
docker ps
```
Para ver los logs si algo falla (ejemplo, backend):
```bash
docker logs wm-backend -f
```

---

## PASO 6: Compilar Mobile y Probar

1. En tu máquina local, navega a `wm_mobile`.
2. Ejecuta `flutter build apk --release`.
3. Pasa el archivo `.apk` (`build/app/outputs/flutter-apk/app-release.apk`) a tu celular.
4. Tu móvil ahora consumirá directamente la API alojada en EC2.

**¡Listo! Con esto tienes una arquitectura robusta y unificada en un solo servidor sin complicaciones externas.**


# Guía de Despliegue con Docker Hub (CI/CD Básico)

Esta estrategia es perfecta para servidores con almacenamiento limitado (como la capa gratuita de AWS de 8GB - 30GB). En lugar de enviar tu código fuente al servidor y asfixiarlo construyendo las imágenes allí, usaremos tu potente computadora local para construir las imágenes, las subiremos a Docker Hub, y el servidor simplemente las descargará y ejecutará.

---

## FASE 1: EN TU COMPUTADORA LOCAL (Construir y Subir)

### 1. Prepara tu cuenta de Docker Hub
1. Entra a [hub.docker.com](https://hub.docker.com/) y regístrate (es gratis).
2. Abre tu terminal local (Git Bash o PowerShell) e inicia sesión:
   ```bash
   docker login
   ```
   *(Ingresa tu usuario y contraseña de Docker Hub).*

    nvmartinezm

### 2. Construir y etiquetar las imágenes
En tu terminal local, colócate en la raíz del proyecto (`C:\Users\Invitado\UAGRM\proyecto\workflow`). Reemplaza `tu_usuario_docker` por el nombre de usuario exacto que creaste en Docker Hub.

**Frontend:**
```bash
docker build -t nvmartinezm/wm-frontend:latest ./wm_frontend
docker push nvmartinezm/wm-frontend:latest
```

**Backend:**
```bash
docker build -t nvmartinezm/wm-backend:latest ./wm_backend
docker push nvmartinezm/wm-backend:latest
```

**Microservicio de IA:**
```bash
docker build -t nvmartinezm/wm-ai:latest ./wm_ai
docker push nvmartinezm/wm-ai:latest
```

### 3. Actualizar `docker-compose.prod.yml`
Yo ya he preparado este archivo en tu máquina, pero **necesitas cambiar `tu_usuario_docker`** por tu usuario real.
Abre el archivo `docker-compose.prod.yml` en tu editor de código y edita estas tres líneas:
```yaml
    image: tu_usuario_docker/wm-frontend:latest
```
```yaml
    image: tu_usuario_docker/wm-backend:latest
```
```yaml
    image: tu_usuario_docker/wm-ai:latest
```

---

## FASE 2: EN TU SERVIDOR EC2 (Descargar y Ejecutar)

### 1. Conéctate a EC2 y libera espacio
Como la instancia se llenó intentando compilar anteriormente, primero debes borrar toda la basura generada:
```bash
# Limpiar todo el cache, contenedores caídos y capas sobrantes
docker system prune -a --volumes
```
*(Escribe `y` y presiona Enter).*

### 2. Sube el `docker-compose.prod.yml` modificado al servidor
Desde otra pestaña de tu terminal local (NO dentro de EC2), envía el archivo actualizado al servidor:
```bash
scp -i "workflow-key.pem" C:\Users\Invitado\UAGRM\proyecto\workflow\docker-compose.prod.yml ubuntu@3.85.237.101:/home/ubuntu/
```

### 3. ¡Despliega en un parpadeo!
En tu servidor EC2, asegúrate de estar en la carpeta `/home/ubuntu/workflow` (donde está tu `.env` que configuramos antes) y ejecuta:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### ¿Qué sucederá?
El servidor **no va a compilar nada**. Simplemente descargará tus tres imágenes directamente de la nube de Docker Hub y las iniciará en cuestión de segundos, sin saturar la memoria RAM ni el disco duro.

Para verificar que estén corriendo, escribe:
```bash
docker ps
```
¡Y entra a tu IP pública en el navegador!

  2. El problema del Base64 de Firebase:                                                                                         
  Ese texto que tienes en tu  .env  ( EJECUTAR: [Convert]::ToBase64String... ) NO es la credencial. Es literalmente las          
  instrucciones que copiaste de algún foro diciéndote "Ejecuta esto en PowerShell para obtener tu clave".                        
  Como eso te está confundiendo mucho, olvidémonos del Base64. Acabo de modificar nuevamente tu  docker-compose.prod.yml  para   
  que lea tu archivo  .json  físico, que es mucho más seguro y fácil para ti.                                                    
                                                                                                                                 
  ### Lo único que necesita tu instancia EC2 son estas 3 cosas:                                                                  
                                                                                                                                 
  Abre la consola en tu máquina local (PowerShell) y ejecuta estos comandos uno por uno (cambia  TU_IP_PUBLICA ). Esto enviará   
  los archivos mágicamente al servidor:                                                                                          
                                                                                                                                 
  Paso 1: Enviar el  docker-compose.prod.yml  actualizado                                                                        
                                                                                                                                 
  scp -i "workflow-key.pem" C:\Users\Invitado\UAGRM\proyecto\workflow\docker-compose.prod.yml ubuntu@3.85.237.101:/home/ubuntu/                                                                                        
                                                                                                                                 
  Paso 2: Enviar tu archivo de variables y llamarlo  .env  en el servidor                                                        
  (Este comando toma tu  .env.production  local del backend y lo pega en el servidor renombrado directamente a  .env )           
                                                                                                                                 
 scp -i "workflow-key.pem" C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend\.env.production ubuntu@3.85.237.101:/home/ubuntu/.env                                                                                                                       
  Paso 3: Crear la carpeta secrets en el servidor y enviar tu archivo JSON de Firebase                                           
                                                                                                                
ssh -i "workflow-key.pem" ubuntu@3.85.237.101 "mkdir -p /home/ubuntu/secrets"      
s                                          
scp -i "workflow-key.pem" C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend\secrets\firebase-service-account.json         
  ubuntu@3.85.237.101:/home/ubuntu/secrets/                                                                                     
  ──────                                                                                                                         
¡Eso es absolutamente todo! Tu servidor ahora tiene:                                                                           
                                                                                                                                 
1. Las instrucciones ( docker-compose.prod.yml )                                                                               
2. Las contraseñas ( .env )                                                                                                    
3. El archivo JSON de Firebase ( secrets/ )                                                                                    
                                                                                                                               
Ahora, entra a tu instancia EC2 por SSH y ejecuta:                                                                             
                                                                                                                               
docker compose -f docker-compose.prod.yml pull                                                                               
docker compose -f docker-compose.prod.yml up -d                                                                              
                                                                                                                               
