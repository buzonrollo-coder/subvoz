# SubVoz — prototipo Android
Lee subtítulos visibles en español o traduce inglés al español y los lee con voz local. Pensado como prueba inicial para Android 12, no como producto terminado.

## Compilar desde un teléfono con GitHub
1. Crea un repositorio en GitHub.
2. Extrae el ZIP. Sube **el contenido** de la carpeta subvoz a la raíz del repositorio (no el ZIP). Deben quedar allí settings.gradle, build.gradle, app y .github.
3. Comprueba que exista `.github/workflows/android.yml`. Algunos gestores ocultan la carpeta `.github`; si falta, crea ese archivo desde la web de GitHub y pega su contenido.
4. Abre Actions → Crear APK. Si no se ejecuta automáticamente, pulsa Run workflow. GitHub puede requerir habilitar Actions.
5. Cuando termine correctamente, descarga el artefacto SubVoz-APK, extrae su ZIP e instala app-debug.apk. Android pedirá permitir instalaciones desde tu navegador o gestor de archivos. Es una compilación de prueba, no una publicación en Play Store.

GitHub Actions depende de las cuotas y condiciones de tu cuenta. No se ha verificado una compilación de este proyecto en Actions todavía.

## Uso
- Instala/descarga una voz **española sin conexión** en los ajustes de síntesis de voz de Android.
- Abre SubVoz, permite mostrarse sobre otras apps, elige idioma y franja e inicia.
- La primera traducción inglés → español necesita internet para descargar modelos de ML Kit; después el procesamiento de texto es local.
- Acepta compartir pantalla completa y abre un video no privado con subtítulos visibles.
- Mueve la ventana arrastrando su texto; déjala fuera de los subtítulos.
- Cierra y vuelve a iniciar al cambiar entre horizontal y vertical: este prototipo fija el tamaño de captura al iniciar.
- Si los subtítulos quedan en las bandas negras o el video ocupa el centro, elige otra franja al reiniciar.

## Privacidad y permisos
La app solicita captura de pantalla y superposición; no micrófono, contactos ni almacenamiento. Procesa las imágenes en memoria, no las guarda ni incorpora código para subirlas. El SDK de Google puede acceder a internet para modelos y operaciones propias; no se promete ausencia total de telemetría del proveedor. No leas pantallas privadas durante la sesión. La notificación permite detener la captura. El motor de voz debe tener una voz local instalada; la app no leerá sin ella.

## Límites y estado
- Código inicial, **sin prueba en un dispositivo ni compilación verificada**.
- Captura cada 1,2 segundos y exige dos lecturas iguales; frases breves pueden perderse. No ofrece sincronización exacta.
- Mientras habla no acumula una cola; prioriza evitar retrasos largos y puede omitir frases.
- Deduplicación básica: una frase idéntica a la última no vuelve a leerse hasta que se lea otra diferente.
- No distingue automáticamente subtítulos de otros textos dentro de la franja.
- Pantallas protegidas con FLAG_SECURE/DRM pueden verse negras. No se eluden esas protecciones.
- No reconoce audio ni traduce manga en esta versión.
- No usa claves de Google Cloud ni APIs de pago por llamada. ML Kit y el motor de voz tienen sus propias condiciones de uso.
- targetSdk 32 para este prototipo privado de Android 12; **no cumple los requisitos actuales de publicación en Google Play**. Adaptar permisos, proyección y servicios antes de elevar el target o distribuir ampliamente.

## Componentes
Android Java, ML Kit Text Recognition (latino), ML Kit Translation inglés → español, Android TextToSpeech y MediaProjection.
