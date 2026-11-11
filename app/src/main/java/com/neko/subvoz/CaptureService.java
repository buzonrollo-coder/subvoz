package com.neko.subvoz;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.nl.translate.*;
import java.nio.ByteBuffer;
import java.util.Locale;

public class CaptureService extends Service {
 private MediaProjection projection; private VirtualDisplay display; private ImageReader reader;
 private TextRecognizer recognizer; private Translator translator; private TextToSpeech tts;
 private WindowManager wm; private LinearLayout panel; private TextView status;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean ready=false,voice=false,busy=false,paused=false,english=true,dead=false;
 private int region=0; private long lastFrame=0; private String last="",candidate=""; private int stable=0;
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","Lectura de subtítulos",NotificationManager.IMPORTANCE_LOW));
  Intent stop=new Intent(this,CaptureService.class).setAction("STOP");PendingIntent pi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(1,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("SubVoz está leyendo la pantalla").setContentText("Detén la captura cuando termines").addAction(new Notification.Action.Builder(null,"Detener",pi).build()).build());
  recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  tts=new TextToSpeech(this,result->{if(dead)return;if(result==TextToSpeech.SUCCESS){int supported=tts.setLanguage(new Locale("es"));voice=supported!=TextToSpeech.LANG_MISSING_DATA && supported!=TextToSpeech.LANG_NOT_SUPPORTED; if(voice && tts.getVoices()!=null){boolean found=false;for(android.speech.tts.Voice v:tts.getVoices())if(v.getLocale().getLanguage().equals("es")&&!v.isNetworkConnectionRequired()){tts.setVoice(v);found=true;break;}voice=found;}if(!voice)show("Instala una voz española sin conexión en los ajustes de Android.");}});
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(projection!=null){show("Ya hay una sesión activa. Detén antes de cambiar ajustes.");return START_NOT_STICKY;}
  english=intent.getBooleanExtra("english",true);region=intent.getIntExtra("region",0); overlay();
  if(english){translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build());show("Preparando modelo inglés → español…");translator.downloadModelIfNeeded().addOnSuccessListener(v->{if(!dead){ready=true;show("Listo. Abre tu video con subtítulos.");}}).addOnFailureListener(e->show("No se pudo descargar el modelo. Detén y reintenta con internet."));}else{ready=true;show("Listo: lectura en español.");}
  try{
   MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   projection=manager.getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),(Intent)intent.getParcelableExtra("data"));
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},handler);
   android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);
   reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,2);
   reader.setOnImageAvailableListener(r->frame(),handler);
   display=projection.createVirtualDisplay("SubVoz",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
  }catch(Exception e){show("No se pudo iniciar captura. Vuelve a abrir SubVoz.");stopSelf();}
  return START_NOT_STICKY;
 }
 private void overlay(){
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,8,12,8);panel.setBackgroundColor(0xEE20242A);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(13);panel.addView(status);
  LinearLayout controls=new LinearLayout(this);Button pause=new Button(this);pause.setText("Pausar");controls.addView(pause);pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Seguir":"Pausar");if(paused)tts.stop();});
  Button close=new Button(this);close.setText("Cerrar");controls.addView(close);close.setOnClickListener(v->stopSelf());panel.addView(controls);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(620,getResources().getDisplayMetrics().widthPixels-24),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=12;p.y=45;
  status.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=p.x;py=p.y;return true;}if(e.getAction()==2){p.x=px+(int)(e.getRawX()-x);p.y=py+(int)(e.getRawY()-y);wm.updateViewLayout(panel,p);return true;}return true;}});
  wm.addView(panel,p);
 }
 private void show(String text){handler.post(()->{if(!dead && status!=null)status.setText(text);});}
 private void frame(){
  Image image=null;Bitmap bitmap=null;
  try{
   image=reader.acquireLatestImage();if(image==null)return;
   long now=SystemClock.elapsedRealtime();if(dead||paused||!ready||!voice||busy||now-lastFrame<1200)return;
   lastFrame=now;Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int w=image.getWidth(),h=image.getHeight();int padded=plane.getRowStride()/plane.getPixelStride();
   Bitmap full=Bitmap.createBitmap(padded,h,Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(buffer);
   int y=region==0?(int)(h*.65):region==1?(int)(h*.35):0;int height=Math.min((int)(h*.35),h-y);
   // Exclude the floating panel from OCR, even when moved into the selected strip.
   int[] loc=new int[2];panel.getLocationOnScreen(loc);Canvas canvas=new Canvas(full);Paint paint=new Paint();paint.setColor(Color.BLACK);canvas.drawRect(loc[0],loc[1],loc[0]+panel.getWidth(),loc[1]+panel.getHeight(),paint);
   bitmap=Bitmap.createBitmap(full,0,y,w,height);if(bitmap!=full)full.recycle();
   final Bitmap input=bitmap;busy=true;
   recognizer.process(InputImage.fromBitmap(input,0)).addOnSuccessListener(text->{if(dead){busy=false;return;}String value=text.getText().replaceAll("\\s+"," ").trim();String key=value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]","").trim();
    if(key.length()<2){candidate="";stable=0;busy=false;return;}
    if(key.equals(candidate))stable++;else{candidate=key;stable=1;}
    if(stable<2||key.equals(last)||tts.isSpeaking()){busy=false;return;}
    last=key;
    if(english)translator.translate(value).addOnSuccessListener(this::speak).addOnFailureListener(e->{last="";busy=false;show("Error de traducción");});else speak(value);
   }).addOnFailureListener(e->{busy=false;show("No se pudo leer la imagen");}).addOnCompleteListener(t->input.recycle());
  }catch(Exception e){busy=false;}finally{if(image!=null)image.close();}
 }
 private void speak(String value){busy=false;if(dead||paused)return;show(value);tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"subtitle");}
 @Override public void onDestroy(){dead=true;handler.removeCallbacksAndMessages(null);if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(recognizer!=null)recognizer.close();if(translator!=null)translator.close();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
