# SubVoz — prototipo Android
SubVoz lee subtítulos visibles en español, inglés, japonés, chino, coreano, francés o portugués. Traduce al español si el idioma elegido no es español y los lee con Text-to-Speech de Android. Esta es una prueba inicial, no una app de doblaje sincronizado.

## Actualizar el repositorio de GitHub desde el teléfono
Reemplaza el contenido de estos cuatro archivos en el repositorio; conserva sus rutas:
- `app/build.gradle`
- `app/src/main/java/com/neko/subvoz/MainActivity.java`
- `app/src/main/java/com/neko/subvoz/CaptureService.java`
- `README.md`
Cada cambio en `main` inicia GitHub Actions automáticamente. Descarga el nuevo artefacto `SubVoz-APK` solo de la ejecución que termine en verde. Desinstala la versión anterior antes de instalar la nueva si Android indica conflicto.

## Funciones de esta actualización
- Selector de idioma original: español, inglés, japonés, chino, coreano, francés y portugués.
- OCR latino para inglés, francés, portugués y español; modelos de escritura japonesa, china y coreana para los otros idiomas.
- Cada texto debe aparecer en dos capturas cercanas para filtrar lecturas accidentales.
- OCR sigue capturando mientras las frases anteriores se traducen y se leen. Las frases se procesan por orden, una a la vez.
- La ventana muestra el texto actual, el tamaño de la cola y ofrece Pausar, Vaciar y Cerrar.
- Deduplicación aproximada: no repite un texto idéntico detectado en los últimos 8 segundos.

## Uso
- Instala una voz española sin conexión en Ajustes → Síntesis de voz. La aplicación se detiene si no encuentra una voz local compatible.
- En SubVoz, permite superposición, elige idioma y franja, pulsa Iniciar y acepta captura de pantalla completa.
- Abre un video de prueba con subtítulos claros. Coloca la ventana lejos del texto.
- Inglés, japonés, chino, coreano, francés y portugués requieren internet la primera vez para descargar el modelo de traducción. ML Kit Translate ejecuta la traducción del texto en el dispositivo una vez instalado el modelo.
- La configuración de idioma OCR/traducción se fija al iniciar la sesión; detén y vuelve a iniciar para cambiarla.

## Límites y privacidad
- Sigue usando OCR periódico de una franja, no una pista real de subtítulos: texto muy breve, animado, borroso o parcialmente tapado puede perderse. La voz puede ir retrasada si se acumulan muchas frases; pulsa Vaciar si quieres saltar lo pendiente.
- Solo OCR de texto latino/japonés/chino/coreano; no árabe, cirílico u otros scripts en esta versión.
- No detecta audio, no traduce manga y no elude pantallas protegidas/DRM (que pueden verse negras).
- Solicita permiso de captura de pantalla y superposición; no usa micrófono. Las imágenes se procesan en memoria y no se guardan ni hay código para enviarlas. Google ML Kit puede tener sus propios servicios/condiciones; no se promete ausencia total de telemetría.
- Proyecto privado de prueba: probar en Android 12; no está preparado para publicar en Play Store.
