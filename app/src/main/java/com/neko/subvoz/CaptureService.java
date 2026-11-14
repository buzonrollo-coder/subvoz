package com.neko.subvoz;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.nl.translate.*;
import java.nio.ByteBuffer;
import java.util.*;

public class CaptureService extends Service {
 private static final String[] ML_LANG={null,TranslateLanguage.ENGLISH,TranslateLanguage.JAPANESE,TranslateLanguage.CHINESE,TranslateLanguage.KOREAN,TranslateLanguage.FRENCH,TranslateLanguage.PORTUGUESE};
 private static final String[] LANG_NAMES={"español","inglés","japonés","chino","coreano","francés","portugués"};
 private static final long FRAME_INTERVAL_MS=280;
 private static final int MAX_PENDING=30;
 private MediaProjection projection; private VirtualDisplay display; private ImageReader reader;
 private TextRecognizer recognizer; private Translator translator; private TextToSpeech tts;
 private WindowManager wm; private LinearLayout panel; private TextView status;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<String> speechQueue=new ArrayDeque<>();
 private final LinkedHashMap<String,Long> recent=new LinkedHashMap<>();
 private boolean ready=false,voice=false,ocrPending=false,paused=false,dead=false,speaking=false;
 private int region=0,source=0,zone=30,style=0,textSize=16; private float speechRate=1f,speechPitch=1f; private String requestedVoice=""; private long lastFrame=0; private String candidate=""; private int stable=0;
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","Lectura de subtítulos",NotificationManager.IMPORTANCE_LOW));
  Intent stop=new Intent(this,CaptureService.class).setAction("STOP");PendingIntent pi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(1,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("SubVoz está leyendo la pantalla").setContentText("Detén la captura cuando termines").addAction(new Notification.Action.Builder(null,"Detener",pi).build()).build());
  tts=new TextToSpeech(this,result->{if(dead)return;if(result==TextToSpeech.SUCCESS){int supported=tts.setLanguage(new Locale("es"));voice=supported!=TextToSpeech.LANG_MISSING_DATA && supported!=TextToSpeech.LANG_NOT_SUPPORTED;if(voice){if(!requestedVoice.isEmpty()&&tts.getVoices()!=null){for(android.speech.tts.Voice v:tts.getVoices())if(v.getName().equals(requestedVoice)){tts.setVoice(v);break;}}tts.setSpeechRate(speechRate);tts.setPitch(speechPitch);}if(!voice)show("Instala una voz española en Ajustes → Síntesis de voz.");else processSpeechQueue();}});
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   @Override public void onStart(String id){}
   @Override public void onDone(String id){handler.post(()->{speaking=false;processSpeechQueue();});}
   @Override public void onError(String id){handler.post(()->{speaking=false;processSpeechQueue();});}
  });
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(projection!=null){show("Ya hay una sesión activa. Detén antes de cambiar ajustes.");return START_NOT_STICKY;}
  source=Math.max(0,Math.min(6,intent.getIntExtra("source",1)));region=intent.getIntExtra("region",0);zone=intent.getIntExtra("zone",30);style=intent.getIntExtra("style",0);textSize=intent.getIntExtra("textSize",16);speechRate=.55f+intent.getIntExtra("rate",50)/100f*1.05f;speechPitch=.65f+intent.getIntExtra("pitch",50)/100f*.7f;requestedVoice=intent.getStringExtra("voice")==null?"":intent.getStringExtra("voice");overlay();
  createRecognizer();
  if(source==0){ready=true;show("Listo: subtítulos en español → voz en español.");}
  else {translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(ML_LANG[source]).setTargetLanguage(TranslateLanguage.SPANISH).build());show("Descargando modelo " + LANG_NAMES[source] + " → español…");translator.downloadModelIfNeeded().addOnSuccessListener(v->{if(!dead){ready=true;show("Listo. Abre tu video con subtítulos en "+LANG_NAMES[source]+".");}}).addOnFailureListener(e->show("No se pudo descargar el modelo de traducción. Conéctate a internet y reinicia."));}
  try{
   MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   projection=manager.getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),(Intent)intent.getParcelableExtra("data"));
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},handler);
   android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);
   reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,3);
   reader.setOnImageAvailableListener(r->frame(),handler);
   display=projection.createVirtualDisplay("SubVoz",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
  }catch(Exception e){show("No se pudo iniciar captura. Vuelve a abrir SubVoz.");stopSelf();}
  return START_NOT_STICKY;
 }
 private void createRecognizer(){
  switch(source){
   case 2:recognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());break;
   case 3:recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());break;
   case 4:recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());break;
   default:recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  }
 }
 private void overlay(){
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,8,12,8);int bg=style==1?0xF5FFFFFF:style==2?0xF5103741:0xEE20242A;panel.setBackgroundColor(bg);
  status=new TextView(this);status.setTextColor(style==1?Color.rgb(24,32,51):Color.WHITE);status.setTextSize(textSize);panel.addView(status);
  LinearLayout controls=new LinearLayout(this);Button pause=new Button(this);pause.setText("Pausar");controls.addView(pause);pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Seguir":"Pausar");if(paused){tts.stop();speaking=false;}else processSpeechQueue();});
  Button clear=new Button(this);clear.setText("Vaciar");controls.addView(clear);clear.setOnClickListener(v->{speechQueue.clear();candidate="";stable=0;tts.stop();speaking=false;show("Cola vaciada. Sigue escuchando subtítulos.");});
  Button close=new Button(this);close.setText("Cerrar");controls.addView(close);close.setOnClickListener(v->stopSelf());panel.addView(controls);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(620,getResources().getDisplayMetrics().widthPixels-24),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=12;p.y=45;
  status.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=p.x;py=p.y;return true;}if(e.getAction()==2){p.x=px+(int)(e.getRawX()-x);p.y=py+(int)(e.getRawY()-y);wm.updateViewLayout(panel,p);return true;}return true;}});
  wm.addView(panel,p);
 }
 private void show(String text){handler.post(()->{if(!dead && status!=null)status.setText(text);});}
 private void frame(){
  Image image=null;
  try{
   image=reader.acquireLatestImage();if(image==null)return;
   long now=SystemClock.elapsedRealtime();if(dead||paused||!ready||!voice||ocrPending||now-lastFrame<FRAME_INTERVAL_MS)return;
   lastFrame=now;Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int w=image.getWidth(),h=image.getHeight();int padded=plane.getRowStride()/plane.getPixelStride();
   Bitmap full=Bitmap.createBitmap(padded,h,Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(buffer);
   int height=(int)(h*.35);int y=region==0?(int)(h*.65):region==1?(int)(h*.325):region==2?0:Math.max(0,Math.min(h-height,(int)(h*(zone/100f)-height/2f)));
   int[] loc=new int[2];panel.getLocationOnScreen(loc);Canvas canvas=new Canvas(full);Paint paint=new Paint();paint.setColor(Color.BLACK);canvas.drawRect(loc[0],loc[1],loc[0]+panel.getWidth(),loc[1]+panel.getHeight(),paint);
   Bitmap crop=Bitmap.createBitmap(full,0,y,w,height);full.recycle();
   ocrPending=true;
   recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener(text->{if(dead)return;String value=selectSubtitleText(text,w,height);String key=normalize(value);
    if(key.length()<2){candidate="";stable=0;return;}
    if(key.equals(candidate))stable++;else{candidate=key;stable=1;}
    // Two matching OCR frames suppress transient interface labels. The queue runs separately,
    // so screen reading continues while previous captions are being translated/spoken.
    if(stable>=2){enqueueDetected(value,key);stable=0;candidate="";}
   }).addOnFailureListener(e->{if(!dead)show("No se pudo leer la franja de subtítulos.");}).addOnCompleteListener(t->{crop.recycle();ocrPending=false;});
  }catch(Exception e){ocrPending=false;}finally{if(image!=null)image.close();}
 }
 private String selectSubtitleText(Text text,int imageWidth,int cropHeight){
  ArrayList<Text.Line> lines=new ArrayList<>();
  for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
   String value=line.getText().replaceAll("\\s+"," ").trim();android.graphics.Rect box=line.getBoundingBox();
   if(box==null||value.length()<2)continue;
   if(box.width()<imageWidth*.16f||box.height()<cropHeight*.018f)continue;
   float centerX=box.centerX();if(centerX<imageWidth*.12f||centerX>imageWidth*.88f)continue;
   lines.add(line);
  }
  if(lines.isEmpty())return "";
  int targetY=cropHeight/2;
  Collections.sort(lines,(a,b)->Integer.compare(Math.abs(a.getBoundingBox().centerY()-targetY),Math.abs(b.getBoundingBox().centerY()-targetY)));
  Text.Line first=lines.get(0);ArrayList<String> chosen=new ArrayList<>();chosen.add(first.getText().trim());
  if(lines.size()>1){Text.Line second=lines.get(1);if(Math.abs(second.getBoundingBox().centerY()-first.getBoundingBox().centerY())<cropHeight*.20f)chosen.add(second.getText().trim());}
  return String.join(" ",chosen).replaceAll("\\s+"," ").trim();
 }
 private String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]","").trim().replaceAll("\\s+"," ");}
 private void enqueueDetected(String value,String key){
  long now=SystemClock.elapsedRealtime();Iterator<Map.Entry<String,Long>> it=recent.entrySet().iterator();while(it.hasNext())if(now-it.next().getValue()>8000)it.remove();
  if(recent.containsKey(key))return;
  recent.put(key,now);while(recent.size()>16)recent.remove(recent.keySet().iterator().next());
  if(speechQueue.size()>=MAX_PENDING){show("La cola está llena; se omitió un texto. Pulsa Vaciar para reiniciar.");return;}
  speechQueue.add(value);
  show("Detectado · en cola: "+speechQueue.size()+" · "+value);
  processSpeechQueue();
 }
 private void processSpeechQueue(){
  if(dead||paused||!voice||speaking||speechQueue.isEmpty())return;
  String original=speechQueue.poll();speaking=true;
  if(source==0){speak(original);return;}
  translator.translate(original).addOnSuccessListener(this::speak).addOnFailureListener(e->{speaking=false;show("Falló una traducción; sigo con la siguiente frase.");processSpeechQueue();});
 }
 private void speak(String value){if(dead||paused){speaking=false;return;}show(value+"\nCola: "+speechQueue.size());int result=tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"caption-"+SystemClock.elapsedRealtime());if(result==TextToSpeech.ERROR){speaking=false;show("El motor de voz no pudo leer esta frase.");processSpeechQueue();}}
 @Override public void onDestroy(){dead=true;handler.removeCallbacksAndMessages(null);if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(recognizer!=null)recognizer.close();if(translator!=null)translator.close();if(tts!=null){tts.stop();tts.shutdown();}speechQueue.clear();super.onDestroy();}
}
