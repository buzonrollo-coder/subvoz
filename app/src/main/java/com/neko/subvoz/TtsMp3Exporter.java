package com.neko.subvoz;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Streams individual Android TTS WAVs through LAME; never accumulates a session in RAM. */
final class TtsMp3Exporter extends UtteranceProgressListener {
    interface Listener { void finished(boolean ok, String message); }
    private final Context context;
    private final TextToSpeech tts;
    private final List<String> phrases;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private String waitingId;
    private boolean signalled;
    private boolean synthesisOk;
    private String synthesisError;
    private File wavFile;

    TtsMp3Exporter(Context c, TextToSpeech engine, List<String> snapshot, Listener done) {
        context = c.getApplicationContext(); tts = engine;
        phrases = new ArrayList<>(snapshot); listener = done;
    }
    void start() { worker.execute(this::run); }
    void cancel() {
        cancelled = true;
        synchronized (lock) { signalled = true; synthesisOk = false; synthesisError = "Exportación cancelada"; lock.notifyAll(); }
        try { tts.stop(); } catch (Exception ignored) { }
        worker.shutdownNow();
    }
    @Override public void onStart(String id) { }
    @Override public void onDone(String id) { signal(id, true, null); }
    @Override public void onError(String id) { signal(id, false, "El motor de voz no pudo sintetizar una frase."); }
    @Override public void onError(String id, int code) { onError(id); }
    @Override public void onStop(String id, boolean interrupted) { if (interrupted) signal(id, false, "Síntesis interrumpida."); }
    private void signal(String id, boolean ok, String message) {
        synchronized (lock) { if (id != null && id.equals(waitingId)) { signalled = true; synthesisOk = ok; synthesisError = message; lock.notifyAll(); } }
    }
    private void run() {
        File mp3 = null;
        AndroidLame lame = null;
        boolean success = false;
        String message = "No se pudo exportar el MP3.";
        try {
            if (phrases.isEmpty()) throw new IOException("No hay frases españolas capturadas.");
            File dir = new File(context.getCacheDir(), "subvoz-export");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo preparar el espacio temporal.");
            mp3 = File.createTempFile("subvoz_", ".mp3", dir);
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(mp3))) {
                int index = 0;
                for (String phrase : phrases) {
                    checkCancelled();
                    if (phrase == null || phrase.trim().isEmpty()) continue;
                    wavFile = File.createTempFile("speech_", ".wav", dir);
                    String id = "subvoz-export-" + index++ + "-" + SystemClock.elapsedRealtime();
                    synchronized (lock) { waitingId = id; signalled = false; synthesisOk = false; synthesisError = null; }
                    int result = tts.synthesizeToFile(phrase, null, wavFile, id);
                    if (result == TextToSpeech.ERROR) throw new IOException("El motor de voz rechazó la frase.");
                    synchronized (lock) {
                        long deadline = SystemClock.elapsedRealtime() + 30000;
                        while (!signalled && !cancelled) {
                            long left = deadline - SystemClock.elapsedRealtime();
                            if (left <= 0) throw new IOException("La síntesis tardó más de 30 segundos.");
                            lock.wait(left);
                        }
                        if (cancelled) throw new IOException("Exportación cancelada.");
                        if (!synthesisOk) throw new IOException(synthesisError == null ? "Falló la síntesis de voz." : synthesisError);
                    }
                    if (wavFile == null || !wavFile.isFile() || wavFile.length() < 44) throw new IOException("El motor no generó audio WAV utilizable.");
                    WavInfo info = encodeWav(wavFile, out, lame);
                    if (lame == null) {
                        LameBuilder builder = new LameBuilder().setInSampleRate(info.sampleRate).setOutSampleRate(info.sampleRate)
                                .setOutChannels(1).setOutBitrate(64).setQuality(5);
                        lame = builder.build();
                        // The first WAV is streamed again below, after the encoder has been initialized.
                        encodeWav(wavFile, out, lame);
                    } else if (info.sampleRate != currentSampleRate) {
                        throw new IOException("La voz cambió de frecuencia de muestreo durante la exportación.");
                    }
                    if (wavFile.exists()) wavFile.delete();
                    wavFile = null;
                }
                if (lame == null) throw new IOException("No se generó voz española.");
                byte[] tail = new byte[8192]; int n = lame.flush(tail); if (n > 0) out.write(tail, 0, n);
                out.flush();
            } finally { if (lame != null) try { lame.close(); } catch (Throwable ignored) { } }
            checkCancelled();
            if (mp3.length() < 100) throw new IOException("El codificador no produjo un MP3 válido.");
            saveToDownloads(mp3);
            success = true; message = "MP3 guardado en Descargas/SubVoz. Se generó de nuevo desde el texto con la voz española de Android.";
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); message = "Exportación cancelada."; }
          catch (Throwable e) { message = e.getMessage() == null ? "No se pudo generar el MP3." : e.getMessage(); }
        finally {
            if (wavFile != null) wavFile.delete();
            if (mp3 != null) mp3.delete();
            worker.shutdown();
            final boolean ok = success; final String resultMessage = message;
            main.post(() -> listener.finished(ok, resultMessage));
        }
    }
    // Used only to preserve a constant source rate across short per-phrase WAVs.
    private int currentSampleRate = -1;
    private void checkCancelled() throws IOException { if (cancelled) throw new IOException("Exportación cancelada."); }

    private WavInfo encodeWav(File file, OutputStream out, AndroidLame encoder) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] head = new byte[12]; raf.readFully(head);
            if (!ascii(head, 0, "RIFF") || !ascii(head, 8, "WAVE")) throw new IOException("El motor de voz no entregó WAV PCM; MP3 no disponible con este motor.");
            int channels = 0, rate = 0, bits = 0, format = 0; long dataAt = -1, dataLen = 0;
            while (raf.getFilePointer() + 8 <= raf.length()) {
                byte[] chunk = new byte[8]; raf.readFully(chunk);
                String name = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
                long len = u32(chunk, 4), start = raf.getFilePointer();
                if (len < 0 || start + len > raf.length()) throw new IOException("WAV truncado.");
                if ("fmt ".equals(name)) {
                    if (len < 16) throw new IOException("Formato WAV no válido.");
                    byte[] fmt = new byte[16]; raf.readFully(fmt);
                    format = u16(fmt, 0); channels = u16(fmt, 2); rate = (int)u32(fmt, 4); bits = u16(fmt, 14);
                } else if ("data".equals(name)) { dataAt = start; dataLen = len; }
                long next = start + len + (len & 1);
                if (next > raf.length()) next = raf.length();
                raf.seek(next);
                if (dataAt >= 0 && channels > 0 && rate > 0) break;
            }
            if (format != 1 || bits != 16 || (channels != 1 && channels != 2) || dataAt < 0 || dataLen <= 0)
                throw new IOException("Este motor generó WAV no compatible (se requiere PCM 16-bit mono/estéreo).");
            if (currentSampleRate < 0) currentSampleRate = rate;
            if (rate != currentSampleRate) throw new IOException("La voz cambió de frecuencia de muestreo durante la exportación.");
            long bytesLeft = dataLen; raf.seek(dataAt);
            byte[] raw = new byte[8192]; short[] mono = new short[4096]; short[] right = new short[4096]; byte[] encoded = new byte[12400];
            while (bytesLeft >= channels * 2L) {
                checkCancelled();
                int bytes = (int)Math.min(raw.length, bytesLeft); bytes -= bytes % (channels * 2);
                if (bytes <= 0) break;
                raf.readFully(raw, 0, bytes); bytesLeft -= bytes;
                int frames = bytes / (channels * 2);
                for (int i = 0; i < frames; i++) {
                    int off = i * channels * 2;
                    short left = (short)((raw[off] & 255) | (raw[off + 1] << 8));
                    if (channels == 2) { short r = (short)((raw[off + 2] & 255) | (raw[off + 3] << 8)); mono[i] = (short)(((int)left + r) / 2); }
                    else mono[i] = left;
                }
                if (encoder != null) { int n = encoder.encodeBufferInterLeaved(mono, frames, encoded); if (n > 0) out.write(encoded, 0, n); }
            }
            // Encoder is initialized only after discovering the WAV's format. The first pass must only inspect its header.
            return new WavInfo(rate, channels);
        }
    }
    private static int currentSampleRateStatic;
    private static boolean ascii(byte[] b, int at, String s) { for (int i=0;i<s.length();i++) if (b[at+i] != (byte)s.charAt(i)) return false; return true; }
    private static int u16(byte[] b, int p) { return (b[p]&255) | ((b[p+1]&255)<<8); }
    private static long u32(byte[] b, int p) { return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24); }
    private static final class WavInfo { final int sampleRate, channels; WavInfo(int r,int c){sampleRate=r;channels=c;} }
    private void saveToDownloads(File mp3) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "SubVoz_" + stamp + ".mp3";
        ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SubVoz");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("No se pudo crear el archivo en Descargas/SubVoz.");
        try {
            try (InputStream in = new BufferedInputStream(new FileInputStream(mp3)); OutputStream out = new BufferedOutputStream(context.getContentResolver().openOutputStream(uri))) {
                if (out == null) throw new IOException("No se pudo abrir el archivo de destino.");
                byte[] b = new byte[16384]; int n; while ((n=in.read(b))!=-1) { checkCancelled(); out.write(b,0,n); }
            }
            ContentValues ready = new ContentValues(); ready.put(MediaStore.MediaColumns.IS_PENDING, 0); context.getContentResolver().update(uri, ready, null, null);
        } catch (Exception e) { context.getContentResolver().delete(uri,null,null); if(e instanceof IOException) throw (IOException)e; throw new IOException("No se pudo guardar el MP3.",e); }
    }
}
