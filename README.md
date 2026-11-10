# LEXI 0.3

Base Android nativa en Java y vistas Android, API mínima 26. Compilación y pruebas en tablet pendientes.

## Compilación e instalación

Instala Android Studio, JDK 17, Android SDK Platform 35, Build-Tools 35.0.0 y Gradle 8.9. Abre esta carpeta como proyecto, sincroniza y ejecuta en la raíz:

    gradle wrapper --gradle-version 8.9 --distribution-type bin
    .\gradlew.bat :app:assembleDebug

Abre app/build/outputs/apk/debug/app-debug.apk desde Archivos en la Lenovo y concede temporalmente el permiso de instalación que solicite Android. También puedes usar adb install -r con esa ruta. Crea el PIN antes de entregar la tablet.

## Uso

Pulsa Hablar y di «LEXI, ¿qué hora es?». Android puede enviar audio a su proveedor de reconocimiento. La voz y el acento dependen de las voces instaladas. Hora y fecha usan el reloj del dispositivo.

En el panel parental puedes activar el historial local, YouTube normal, la IA y el recordatorio para lavarse los dientes a las 21:00. Para este último concede los permisos de notificaciones y alarmas exactas cuando corresponda. El sistema intenta repetir el aviso cada cinco minutos hasta marcar Listo, incluso sin Wi-Fi. Android puede demorar avisos por ahorro de batería. El horario y el intervalo son fijos en esta versión. YouTube controla sus propios resultados.

## Backend de IA

Instala Node.js 22. Configura estas variables de entorno en el servidor:

- LEXI_TOKEN: token aleatorio de la tablet, de al menos 32 caracteres.
- AI_KEY: credencial privada del proveedor.
- AI_BASE_URL: URL HTTPS base del proveedor, incluida su ruta de versión.
- AI_MODEL: modelo que admita POST /chat/completions, instrucciones del sistema y max_tokens.
- MODERATION_MODEL: modelo que admita POST /moderations y results[0].flagged.
- DAILY_LIMIT: solicitudes diarias, 40 por defecto. Se reinicia al reiniciar el proceso.
- PORT: puerto local, 8080 por defecto.

Ejecuta node server/server.mjs. El servidor escucha en localhost. Publícalo mediante un proxy HTTPS con certificado válido, límite de cuerpo de 16 KiB y sin registro de credenciales ni contenido. Guarda en el panel parental la URL pública terminada en /chat y el token de la tablet. Activa IA en línea. La clave del proveedor queda en el servidor y el token se cifra con Android Keystore.

El backend modera entrada y salida, limita solicitudes y no guarda conversaciones. La app puede conservar hasta 100 mensajes en su historial opcional y envía hasta nueve mensajes recientes al backend. Los filtros son parciales. Antes de activar para Marina, valida las condiciones del proveedor para menores, la retención y el presupuesto. Prueba la moderación con datos ficticios. Educación y entretenimiento son conversaciones guiadas que requieren el backend.

## Pendientes

Probar compilación, permisos, voz y orientación vertical/horizontal en Lenovo. Verificar reinicio, repetición y cancelación de alarmas. Probar la IA contra un servicio real. Quedan pendientes activación local por nombre, recuerdos independientes y varios chats, juegos independientes, límites de tiempo de uso y medición de consumo en el panel.


## Pantalla interactiva en 0.2

El avatar 2D está dibujado con Android Canvas. La boca se anima entre los eventos de inicio y fin de TextToSpeech. Es una aproximación sin datos de fonemas. Usa el volumen multimedia de la tablet. En el panel puedes desactivar lectura automática y reducir animaciones.

Los comandos «Enséñame los planetas» y «¿Cuánto es 8 por 4?» muestran dos tarjetas locales que funcionan sin IA. La tarjeta del sistema solar permite elegir un planeta; el diagrama no está a escala. El modelo devuelve un objeto JSON con answer, emotion y resource. El texto se utiliza en pantalla y voz. El servidor valida las claves y solo admite recursos empaquetados. Los comandos Android se resuelven por separado.

Ejecuta node --test server/reply.test.mjs para validar el contrato de respuesta.


## Lenovo Tab M8 (4th Gen) · 0.3

Conserva Java, Canvas y las vistas Android. El diseño en horizontal muestra el avatar al lado de la conversación y las tarjetas. El teclado redimensiona la ventana y la barra inferior conserva Hablar, Detener, Repetir y Enviar. Detener cancela dictado, consulta y lectura. La sesión se conserva durante un giro sin escribir a disco si el historial está desactivado. El avatar dibuja a menor frecuencia en espera y se pausa fuera del área visible o cuando la ventana pierde el foco.

### Generar la APK con GitHub Actions

Si prefieres evitar la instalación local del SDK, coloca el contenido del ZIP en la raíz de un repositorio GitHub con Actions habilitado. Conserva la carpeta .github. En Actions, ejecuta **LEXI debug APK** con **Run workflow**. Al terminar, descarga el artefacto **LEXI-debug**, extrae app-debug.apk e instálalo siguiendo las instrucciones anteriores. El workflow no requiere claves de IA. Si Android rechaza una actualización por firma distinta, usa el mismo entorno de firma para ambas versiones. Desinstalar la versión anterior borra los datos locales.

### Prueba física de aceptación

1. En Ajustes → Acerca de la tablet, registra la versión de Android y la RAM. Abre LEXI en vertical y horizontal. Con el teclado abierto, confirma que Hablar, Detener, Repetir y Enviar sigan accesibles. Gira durante una conversación y verifica que se conserve el texto.
2. Prueba conceder, denegar y revocar el micrófono. Prueba con y sin motor de dictado y con el micrófono ocupado. Comprueba que escribir siga disponible. Si el motor no admite es-CL, instala un motor o paquete de voz compatible desde los ajustes de Android.
3. Reproduce una respuesta con TTS, detenla y repítela. Comprueba que la boca se detenga al cortar el audio. Prueba también con la voz española ausente.
4. Prueba una consulta con Wi-Fi lento, una cancelación y otra consulta inmediata. Sin IA configurada o sin red, comprueba que el texto sea claro y que hora, fecha y las dos tarjetas locales funcionen.
5. Verifica YouTube bloqueado por defecto, búsqueda autorizada en la aplicación normal y alternativa si falta YouTube. Verifica el recordatorio tras reiniciar, con pantalla apagada y al marcar Listo. Android Go y el ahorro de energía pueden retrasar los reintentos.
6. Para medir rendimiento, prueba diez minutos de conversación en la variante de 2 GB. Registra fluidez, cierres o pausas, y compara memoria al abrir y al terminar con adb shell dumpsys meminfo cl.lexi. Las frecuencias de redibujado del código son objetivos, no mediciones del dispositivo.

Compatibilidad revisada por código. El motor de dictado, los paquetes de idioma, la síntesis y la puntualidad de alarmas dependen de la variante y la configuración. La compilación y las mediciones de memoria, batería y fluidez quedan pendientes de ejecutar.

## Revisión para la variante de 4 GB

Se mantiene minSdk 26, sin seleccionar un procesador concreto ni agregar dependencias de ejecución. Para animaciones activas, el avatar solicita redibujado cada 33 ms. En reposo solicita cada 80 ms y se pausa fuera del área visible. El movimiento de boca comienza con el callback de inicio de TTS y se detiene al finalizar, fallar o cancelar el audio. Se conservan los diagramas Canvas, de carga bajo demanda, sin bitmaps ni caché de imágenes en memoria. Desde la tarjeta solar puedes elegir un planeta y tocar «Escuchar sobre este planeta».

La app conserva hasta 100 mensajes y envía los últimos nueve como máximo, con un tope adicional de 10 000 bytes para el contexto. No se generan resúmenes ni se altera el permiso de memoria. El APK no requiere credenciales en la compilación.

El Wrapper se genera durante GitHub Actions y se verifica contra el SHA-256 oficial de Gradle 8.9. El ZIP fuente no incluye el JAR ni los scripts generados. Para obtenerlos localmente con Gradle 8.9 instalado:

    gradle wrapper --gradle-version 8.9 --distribution-type bin --gradle-distribution-sha256-sum d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
    Get-FileHash gradle/wrapper/gradle-wrapper.jar -Algorithm SHA256
    .\gradlew.bat :app:assembleDebug :app:lintDebug

El hash esperado del JAR es 498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17.

### Prueba de 20 interacciones por voz

1. Registra número de modelo, versión Android, motor de dictado y voz instalada. Tras abrir LEXI, registra memoria con adb shell dumpsys meminfo cl.lexi si ADB está disponible.
2. Completa cinco turnos de voz con hora, fecha, planetas, multiplicación y una pregunta abierta. En planetas selecciona otro planeta y reproduce su explicación.
3. Haz cinco preguntas de seguimiento con el proveedor configurado. Interrumpe dos respuestas y usa Repetir. Comprueba que la boca se detenga al interrumpir y vuelva a moverse al reproducir.
4. Haz otros cinco turnos. Gira la pantalla después de una respuesta, abre el teclado y alterna entre dictado y texto. Comprueba que el texto previo siga visible y que los controles permanezcan accesibles.
5. Haz cinco turnos más. Corta Wi-Fi antes de uno, recupéralo y vuelve a preguntar. Cancela una consulta y empieza otra. Comprueba que una respuesta cancelada no se reproduzca después.
6. Registra tiempo aproximado, incidencias y memoria tras los 20 turnos y de nuevo tras un minuto de reposo. Completa la revisión del PIN, permisos, alertas, bloqueo de YouTube y navegación indicada en la sección anterior. Si el reconocimiento falla, registra el error y conserva la prueba de texto como diagnóstico separado.

Verificación local realizada: sintaxis del backend, tres pruebas del contrato JSON, lectura XML del manifiesto y revisión de contenido del ZIP. Pendientes: generación del Wrapper en CI, compilación/lint Android, inspección de un APK, prueba con proveedor real y todas las pruebas físicas.


## Entrega revisada para GitHub Actions (10-11-2026)

En esta revisión se incorpora `.github/workflows/android.yml` actualizado, `.gitignore`, `gradle.properties` e `INFORME_REVISION.md`. No se han cambiado los archivos Java originales.

**Subir el ZIP no basta:** descomprime el proyecto y sube **su contenido** a la raíz del repositorio privado. Para conservar el workflow `.github`, activa la visualización de archivos ocultos en Windows o sube mediante Git. En GitHub entra a Actions → LEXI debug APK → Run workflow. Después, en la ejecución, descarga el artifact `LEXI-debug` para obtener `app-debug.apk`.

El workflow publica la APK tras compilar y ejecuta después Android Lint. Si el análisis detecta errores, puede marcar la ejecución como fallida aunque exista el artefacto: revisa igualmente los resultados antes de instalarla a una menor.

**Limitación**: revisión estática y pruebas del backend; la APK NO se ha compilado aquí, y la integración con IA y la tablet real siguen pendientes.
