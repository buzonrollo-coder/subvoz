# SubVoz — prototipo Android

SubVoz lee texto de subtítulos que se ve en pantalla, opcionalmente lo traduce al español y lo lee con Text-to-Speech (TTS) de Android. No extrae una pista sincronizada del reproductor y no es una app de doblaje en tiempo real.

## Funciones

- OCR de subtítulos visibles en español, inglés, japonés, chino, coreano, francés o portugués. Para idiomas distintos de español intenta traducir al español con ML Kit.
- El texto detectado se confirma en capturas consecutivas y se pone en una cola limitada. El modo beta de audio sigue usando Vosk para reconocimiento japonés local, si el reproductor permite captura.
- La ventana flotante conserva Pausar, Vaciar y Cerrar. También incluye exportación TXT, SRT y MP3.
- Perfiles de voz: **Natural**, **Tranquila**, **Enérgica**, **Grave/seria** y **Personalizada**. Son combinaciones de velocidad y tono de Android TTS, no promesas de emoción. Se conserva el selector de voz española instalada y el ajuste manual de velocidad y tono. El perfil/voz elegido se utiliza en lectura en vivo y en la generación de MP3.
- SRT usa el texto español que se leyó (directo o traducido), índices secuenciales y tiempos estimados monotónicos relativos al inicio de la sesión.
- El MP3 es audio codificado realmente como MP3 a partir de una nueva síntesis TTS de las frases españolas capturadas; no es una grabación del audio que ya se reprodujo.

## Uso e instalación

1. Instala una voz española en Ajustes de Android → Síntesis de voz. Si quieres que lectura y exportación no dependan de red, selecciona una voz marcada «sin conexión» y comprueba que funcione con «Escuchar voz de prueba». La disponibilidad depende del motor y las voces del teléfono.
2. En SubVoz, concede el permiso de ventana flotante. Elige idioma, zona y voz/perfil; inicia la captura y acepta la pantalla compartida.
3. Abre un video con subtítulos visibles y coloca la ventana flotante lejos de ellos. Exporta desde esa ventana y guarda los archivos antes de cerrar la sesión.
4. Busca tus archivos en **Descargas/SubVoz**. Android puede mostrar esa carpeta con otro nombre localizado.
5. Para instalar la APK de GitHub Actions, descarga `SubVoz-APK` de una ejecución verde. En Android puede ser necesario permitir temporalmente instalar aplicaciones desde el navegador/gestor de archivos. Si Android avisa de firma incompatible, desinstala primero la versión anterior.

Los modelos de traducción para idiomas distintos del español pueden requerir una descarga inicial de Internet. ML Kit realiza la traducción local después de descargar el modelo. El modo de audio japonés requiere además descargar su modelo local.

## Qué guardan las exportaciones

- **TXT:** transcripción acumulada en memoria de la sesión.
- **SRT:** snapshot de las frases capturadas y procesadas hasta el momento de exportar. Sus tiempos son aproximaciones basadas en el reloj monotónico de la sesión y la captura de frases, **no están sincronizados con el video**. Pausar, reanudar, buscar/seeking y las colas de traducción/voz no se reflejan con exactitud; los subtítulos pueden aparecer desfasados o con duraciones aproximadas. No se incluyen frases que OCR no llegó a capturar.
- **MP3:** snapshot de frases españolas capturadas hasta el momento de exportar, sintetizado otra vez, frase por frase, mediante la voz/perfil elegidos. No contiene la grabación del audio original ni necesariamente conserva sus pausas, pronunciación, tiempos o sincronía. Para evitar cargar sesiones grandes enteras en RAM, se procesan archivos temporales de voz en streaming y se eliminan al terminar. Cada frase tiene un límite de espera de 30 segundos; si el motor no entrega WAV PCM compatible o no está disponible el encoder nativo en el dispositivo, se informa el fallo en lugar de afirmar que se creó un MP3.
- Pausar detiene el procesamiento OCR/lectura de nuevas frases; las frases ya reconocidas pueden conservarse en la cola. Vaciar descarta la cola pendiente pero no borra los archivos/frases ya capturados para exportar. Al cerrar/detener, la sesión y sus datos en memoria se pierden; exporta primero lo que quieras conservar. La exportación usa una copia del contenido existente al pulsar el botón.

## Privacidad, compatibilidad y límites

- SubVoz no incluye código que suba o guarde capturas de texto o audio. OCR, archivos temporales de MP3 y codificación se procesan en el dispositivo. Sin embargo, un motor/voz TTS de Android elegido por el usuario podría usar un servicio de red según su propia configuración; para más privacidad, usa una voz descargada sin conexión. Las descargas iniciales de modelos requieren red. No se garantiza el comportamiento o la telemetría de aplicaciones de terceros.
- El lector usa OCR de una franja de pantalla, no la pista de subtítulos del video. Texto borroso, breve, animado, tapado o fuera de la franja puede perderse; la ventana puede tapar texto si se superpone. Contenido DRM/protegido puede aparecer negro. Pausas y búsquedas del reproductor no controlan ni sincronizan SubVoz.
- La captura de audio interno depende de Android y del reproductor, y solo funciona con audio compartible; no usa el micrófono para esa función.
- Perfiles son controles sencillos de rate/pitch. No crean actuación emocional real y la sensación de voz grave/natural depende de la voz española elegida y del motor TTS instalado. El motor podría no admitir todas las tasas o pitches igual.
- Objetivo de prueba: Xiaomi Redmi 9 con Android 12 (arm64-v8a o armeabi-v7a). La APK usa la biblioteca nativa LAME para codificar MP3 y contiene soporte para esas arquitecturas; se requiere comprobarla en el dispositivo real. Este prototipo no es una versión lista para Play Store.
- El encoder AndroidLame/TAndroidLame 1.1 se publica bajo GPLv3. Consulta la licencia del proyecto fuente de la dependencia: https://github.com/naman14/TAndroidLame
