# INFORME DE REVISIÓN — LEXI 0.3 (Lenovo Tab M8 4 GB)

Fecha: 10 de noviembre de 2026. Código evaluado desde `LEXI-0.3-Lenovo-4GB.zip`.

## Resultado

- Se compararon los tres ZIP recibidos y se eligió la versión más nueva (Lenovo 4 GB).
- 13 archivos originales: Java Android (actividad, avatar, recursos educativos, recordatorios, almacenamiento de secretos), servidor Node, pruebas, manifiesto, Gradle y GitHub Actions.
- Las 3 pruebas del contrato de respuesta de IA (`node --test server/reply.test.mjs`) pasan.
- Sintaxis JavaScript verificada con Node; manifiesto XML válido.
- Dependencias declaradas: Android Gradle Plugin 8.7.3, Gradle 8.9, compileSdk 35, minSdk 26, targetSdk 34. La combinación AGP/Gradle y JDK 17 coincide con la documentación de Android.
- Verificadas las huellas SHA-256 indicadas para Gradle 8.9 en la web oficial de Gradle.

## Cambios en esta entrega

- Workflow GitHub Actions más claro: fija Node 22, compila primero, publica APK y después ejecuta Android Lint para que un error de lint no oculte una APK ya construida.
- Añadido `gradle.properties` con límite de heap de 2 GB y codificación UTF-8.
- Añadido `.gitignore` para no publicar salidas ni credenciales locales.
- Añadida guía sencilla para generar e instalar la APK y un resumen de limitaciones.
- No se reescribió código Java ni el servidor para evitar introducir cambios no comprobados en runtime.

## NO verificado y riesgos

**No se ha generado una APK**: el entorno de revisión tiene Java/Node pero carece de Android SDK y Gradle y no permite descargar sus distribuciones. El workflow se ha preparado, pero NO se ha ejecutado en GitHub Actions.

**No se ha probado en la Lenovo**: pueden aparecer errores de compilación, permisos, interfaz, animación, voz y notificaciones. Corrige los errores informados por Actions antes de instalar.

**IA real no conectada**: para responder preguntas abiertas, se debe configurar y desplegar un backend HTTPS con credenciales del proveedor, moderación y límites. La APK sola no habilita IA.

**Privacidad**: el historial opcional se almacena en las preferencias locales de la app sin cifrado específico; no actives esta opción con contenido sensible sin revisar el diseño. El token del servidor usa Android Keystore; nunca se debe incluir la clave privada del proveedor en la APK.

**YouTube normal**: abrir YouTube permite contenido que LEXI no puede moderar. Debe autorizarlo un adulto y configurar las protecciones correspondientes en YouTube/Google.

**Recordatorios**: el horario de cepillado 21:00 y la repetición a intervalos de 5 minutos son valores fijos de esta versión y necesitan prueba física antes de confiarlos a Marina.

**Seguridad infantil**: los filtros del backend son parciales. Revisa la elegibilidad y condiciones del servicio de IA para una menor y prueba casos adversariales antes de su uso.

## Primeros pasos

1. Repositorio GitHub **privado**: crear uno vacío e incorporar los archivos y carpetas de este ZIP (NO subir ZIP como archivo único).
2. Abrir **Actions** y habilitar workflows si GitHub lo solicita.
3. Abrir **LEXI debug APK** > **Run workflow**.
4. Abrir la ejecución recién creada y revisar **Compilar APK de prueba** y **Ejecutar Android Lint**.
5. Si la compilación terminó, ir a **Artifacts** > `LEXI-debug`, descargar, descomprimir y obtener `app-debug.apk`.
6. Instalar manualmente en la Lenovo con permiso temporal de instalar apps de esa fuente. La prueba no habilita aún IA conversacional sin backend.

**Si fallan los pasos del workflow**: conservar el log completo de la primera tarea fallida, especialmente `Compilar APK de prueba`, para corregir causas reales. No declarar éxito hasta que GitHub publique el artefacto.
