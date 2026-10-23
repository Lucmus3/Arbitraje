# Arbitraje

App Android para dirigir partidos: equipos con escudo, marcador en pantalla dividida, reloj con pausa que calcula el tiempo a agregar, tarjetas que pintan la pantalla 5 segundos, estadísticas y ligas con tabla de posiciones.

## Instalar

Bajá el APK del [último release](../../releases/latest) e instalalo. Después la app avisa sola cuando hay una versión nueva y se actualiza desde el inicio.

## Cómo se compila

Cada push a `main` corre `.github/workflows/build.yml`, que compila el APK firmado y publica un release `v1.0.N`. La app compara ese número con su `versionCode`.

Configuración del repo:

- Secreto `KEYSTORE_BASE64`: la clave de firma en base64. Siempre la misma, si cambia el teléfono rechaza las actualizaciones.
- Secreto `KEYSTORE_PASSWORD`: contraseña de la clave (alias `arbitraje`).
- Variable `GOOGLE_WEB_CLIENT_ID` (opcional): ID de cliente web de Google Cloud para "Acceder con Google". Sin ella, la app usa el selector de cuentas de Google del teléfono.

## Publicar una actualización sin GitHub Actions

Compilar con `./gradlew assembleRelease -PbuildNumber=N` (N mayor que la versión instalada), copiar el APK a
`updates/arbitraje.apk` y subir `versionCode`/`versionName` en `updates/update.json`. Al llegar a `main`, la app
lo detecta sola. Si existe un release `v1.0.N` en GitHub, también lo usa (gana la versión más alta).
