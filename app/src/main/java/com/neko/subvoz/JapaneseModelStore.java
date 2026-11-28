package com.neko.subvoz;
import android.content.Context;
import java.io.File;
/** Backward-compatible facade for the original Japanese model API. */
public final class JapaneseModelStore {
 public interface Progress { void update(int percent); void done(File modelDir,Exception error); }
 private JapaneseModelStore(){}
 public static File modelDir(Context c){return OfflineSpeechModelStore.modelDir(c,"ja");}
 public static boolean isInstalled(Context c){return OfflineSpeechModelStore.isInstalled(c,"ja");}
 public static void download(Context c,Progress callback){OfflineSpeechModelStore.download(c,"ja",new OfflineSpeechModelStore.Progress(){public void update(int p){callback.update(p);}public void done(File f,Exception e){callback.done(f,e);}});}
}
