package com.neko.subvoz;

import android.app.*;
import android.content.*;
import android.content.ContentValues;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioPlaybackCaptureConfiguration;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;
import android.media.projection.*;
import android.provider.MediaStore;
import android.os.*;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
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
 private TextRecognizer recognizer; private Translator translator,audioTranslator; private TextToSpeech tts;
 private volatile boolean audioRunning=false; private AudioRecord playbackRecord; private Thread audioThread; private Model voskModel; private int mode=0,audioModelIndex=2,audioSource=2;
 private WindowManager wm; private LinearLayout panel; private TextView status;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<String> speechQueue=new ArrayDeque<>();
 private final HashSet<String> audioQueueKeys=new HashSet<>();
 private final ArrayList<String> savedCaptions=new ArrayList<>();
 private final ArrayList<CaptionRow> srtRows=new ArrayList<>();
 private final ArrayList<String> spokenSpanish=new ArrayList<>();
 private final LinkedHashMap<String,Long> captureTimes=new LinkedHashMap<>();
 private long sessionStarted=0,lastSrtStart=0;
 private boolean exporting=false;
 private TtsMp3Exporter activeExporter;
 private String currentOriginal="";
 private final LinkedHashMap<String,Long> recent=new LinkedHashMap<>();
 private boolean ready=false,voice=false,ocrPending=false,paused=false,speaking=false;
 private long activeOperation=0;
 private volatile boolean dead=false;
 private int region=0,source=0,zone=30,style=0,textSize=16; private float speechRate=1f,speechPitch=1f; private String requestedVoice=""; private long lastFrame=0; private String candidate=""; private int stable=0;
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","SubVoz en funcionamiento",NotificationManager.IMPORTANCE_LOW));
  Intent stop=new Intent(this,CaptureService.class).setAction("STOP");PendingIntent pi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(1,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("SubVoz está procesando la sesión").setContentText("Detén la captura cuando termines").addAction(new Notification.Action.Builder(null,"Detener",pi).build()).build());
  tts=new TextToSpeech(this,result->{if(dead)return;if(result==TextToSpeech.SUCCESS){int supported=tts.setLanguage(new Locale("es"));voice=supported!=TextToSpeech.LANG_MISSING_DATA && supported!=TextToSpeech.LANG_NOT_SUPPORTED;if(voice){if(!requestedVoice.isEmpty()&&tts.getVoices()!=null){for(android.speech.tts.Voice v:tts.getVoices())if(v.getName().equals(requestedVoice)){tts.setVoice(v);break;}}tts.setSpeechRate(speechRate);tts.setPitch(speechPitch);}if(!voice)show("Instala una voz española en Ajustes → Síntesis de voz.");else processSpeechQueue();}else{voice=false;show("No se pudo iniciar la voz en español; la traducción seguirá sin audio.");processSpeechQueue();}});
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   @Override public void onStart(String id){}
   @Override public void onDone(String id){handler.post(()->{if(!id.equals("caption-"+activeOperation))return;speaking=false;activeOperation++;processSpeechQueue();});}
   @Override public void onError(String id){handler.post(()->{if(!id.equals("caption-"+activeOperation))return;speaking=false;activeOperation++;show("La voz no pudo leer la frase.");processSpeechQueue();});}
  });
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(projection!=null){show("Ya hay una sesión activa. Detén antes de cambiar ajustes.");return START_NOT_STICKY;}
  sessionStarted=SystemClock.elapsedRealtime();
  mode=intent.getIntExtra("mode",0);source=Math.max(0,Math.min(6,intent.getIntExtra("source",1)));audioModelIndex=Math.max(0,Math.min(4,intent.getIntExtra("audioLang",2)));int[] sourceForAudio={0,1,2,5,6};audioSource=sourceForAudio[audioModelIndex];region=intent.getIntExtra("region",0);zone=intent.getIntExtra("zone",30);style=intent.getIntExtra("style",0);textSize=intent.getIntExtra("textSize",16);speechRate=.55f+intent.getIntExtra("rate",50)/100f*1.05f;speechPitch=.65f+intent.getIntExtra("pitch",50)/100f*.7f;requestedVoice=intent.getStringExtra("voice")==null?"":intent.getStringExtra("voice");overlay();
  createRecognizer();
  int needed=(source>0?1:0)+(mode==1&&audioSource>0?1:0);pendingTranslatorModels=needed;ready=needed==0;
  if(source>0){translator=makeTranslator(source);translator.downloadModelIfNeeded().addOnSuccessListener(v->translationModelFinished(true)).addOnFailureListener(e->translationModelFinished(false));}
  if(mode==1&&audioSource>0){audioTranslator=makeTranslator(audioSource);audioTranslator.downloadModelIfNeeded().addOnSuccessListener(v->translationModelFinished(true)).addOnFailureListener(e->translationModelFinished(false));}
  if(needed==0){show(source==0?"Listo: español en pantalla → voz en español.":"Listo. Abre tu video con subtítulos en "+LANG_NAMES[source]+".");}
  else show("Preparando traducción local necesaria para la sesión…");
  try{
   MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   projection=manager.getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),(Intent)intent.getParcelableExtra("data"));
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},handler);
   // OCR is active in both modes: in audio mode it is a fallback and shares
   // deduplication with speech recognition so the same caption is not read twice.
   android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);
   reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,3);
   reader.setOnImageAvailableListener(r->frame(),handler);
   display=projection.createVirtualDisplay("SubVoz",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
   if(mode==1)startAudioCapture();
  }catch(Exception e){show("No se pudo iniciar captura. Vuelve a abrir SubVoz.");stopSelf();}
  return START_NOT_STICKY;
 }
 private int pendingTranslatorModels=0; private boolean translatorModelFailed=false;
 private Translator makeTranslator(int lang){return Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(ML_LANG[lang]).setTargetLanguage(TranslateLanguage.SPANISH).build());}
 private void translationModelFinished(boolean success){if(dead)return;if(!success)translatorModelFailed=true;pendingTranslatorModels--;if(pendingTranslatorModels<=0){if(translatorModelFailed){ready=false;show("No se pudo descargar un modelo de traducción. Conéctate a internet y reinicia.");}else{ready=true;show(mode==1?"Listo. Audio → "+LANG_NAMES[audioSource]+"; subtítulos → "+LANG_NAMES[source]+".":"Listo. Abre tu video con subtítulos en "+LANG_NAMES[source]+".");processSpeechQueue();}}}
 private void startAudioCapture(){
  if(android.os.Build.VERSION.SDK_INT<29){show("La captura de audio interno requiere Android 10 o posterior.");stopSelf();return;}
  audioRunning=true;show("Cargando reconocimiento de "+OfflineSpeechModelStore.NAMES[audioModelIndex]+" sin conexión…");
  audioThread=new Thread(()->{
   Recognizer asr=null;
   try{
    String modelId=OfflineSpeechModelStore.IDS[audioModelIndex];if(!OfflineSpeechModelStore.isInstalled(this,modelId))throw new IllegalStateException("Descarga primero el modelo de audio seleccionado en Ajustes");voskModel=new Model(OfflineSpeechModelStore.modelDir(this,modelId).getAbsolutePath());
    asr=new Recognizer(voskModel,16000.0f);
    asr.setEndpointerMode(Recognizer.EndpointerMode.SHORT);
    AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
      .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
      .addMatchingUsage(AudioAttributes.USAGE_GAME)
      .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
      .excludeUid(getApplicationInfo().uid)
      .build();
    int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    int bufferSize=Math.max(min,16000*2);
    AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    playbackRecord=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufferSize).setAudioPlaybackCaptureConfig(config).build();
    if(playbackRecord.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord no se pudo inicializar");
    playbackRecord.startRecording();show("Escuchando audio "+LANG_NAMES[source]+" localmente…");byte[] data=new byte[4096];
    while(audioRunning&&!dead){int n=playbackRecord.read(data,0,data.length);if(n>0){if(asr.acceptWaveForm(data,n)){String json=asr.getResult();String recognized=new JSONObject(json).optString("text","").trim();if(!recognized.isEmpty())postRecognizedAudio(recognized);}}else if(n<0){throw new IllegalStateException("La captura de reproducción dejó de estar disponible");}}
    if(audioRunning){String json=asr.getFinalResult();String recognized=new JSONObject(json).optString("text","").trim();if(!recognized.isEmpty())postRecognizedAudio(recognized);}
   }catch(Exception e){if(!dead){String msg=e.getMessage()==null?"":e.getMessage();show("No se pudo reconocer el audio. El reproductor podría bloquear la captura. "+msg);}}
   finally{if(asr!=null)asr.close();audioRunning=false;if(playbackRecord!=null){try{playbackRecord.stop();}catch(Exception ignored){}playbackRecord.release();playbackRecord=null;} }
  },"subvoz-audio-"+OfflineSpeechModelStore.IDS[audioModelIndex]);audioThread.start();
 }
 private void postRecognizedAudio(String recognized){handler.post(()->{if(dead||paused)return;String key=normalize(recognized);if(key.length()<2)return;if(rememberUnique(key))enqueueAudioResult(recognized);});}
 private void enqueueAudioResult(String value){if(speechQueue.size()>=MAX_PENDING){show("La cola está llena; pulsa Vaciar y retoma la reproducción.");return;}speechQueue.add(value);audioQueueKeys.add(normalize(value));if(!speaking)show(LANG_NAMES[audioSource]+" reconocido · en cola: "+speechQueue.size()+" · "+value);processSpeechQueue();}
 private void stopAudioCapture(){audioRunning=false;if(playbackRecord!=null){try{playbackRecord.stop();}catch(Exception ignored){}}if(audioThread!=null)audioThread.interrupt();}
 private void createRecognizer(){
  switch(source){
   case 2:recognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());break;
   case 3:recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());break;
   case 4:recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());break;
   default:recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  }
 }
 private void overlay(){
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  int navy=Color.rgb(13,28,43),surface=Color.rgb(24,45,59),teal=Color.rgb(20,184,166),muted=Color.rgb(174,198,210);
  panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(10),dp(12),dp(12));panel.setBackground(rounded(navy,18));panel.setElevation(dp(10));
  LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(12),dp(9),dp(12),dp(9));header.setBackground(rounded(surface,13));
  TextView handle=new TextView(this);handle.setText("SUBVOZ  ·  MANTÉN AQUÍ PARA MOVER");handle.setTextColor(teal);handle.setTextSize(10);handle.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(handle);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(Math.max(13,Math.min(textSize,18)));status.setMaxLines(3);status.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(4);header.addView(status,slp);panel.addView(header,new LinearLayout.LayoutParams(-1,-2));
  TextView actionsLabel=overlayLabel("LECTURA",muted);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2);alp.topMargin=dp(9);panel.addView(actionsLabel,alp);
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.topMargin=dp(4);panel.addView(controls,clp);
  TextView pause=overlayAction("Pausar",surface,Color.WHITE);addOverlayItem(controls,pause,true);pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Seguir":"Pausar");if(paused){activeOperation++;tts.stop();speaking=false;}else processSpeechQueue();});
  TextView clear=overlayAction("Vaciar",surface,Color.WHITE);addOverlayItem(controls,clear,false);clear.setOnClickListener(v->{speechQueue.clear();audioQueueKeys.clear();captureTimes.clear();candidate="";stable=0;activeOperation++;tts.stop();speaking=false;show("Cola vaciada. Sigue escuchando subtítulos.");});
  TextView close=overlayAction("Cerrar",0xFF783C4A,Color.WHITE);addOverlayItem(controls,close,false);close.setOnClickListener(v->stopSelf());
  TextView exportLabel=overlayLabel("GUARDAR ARCHIVO",muted);LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-1,-2);elp.topMargin=dp(9);panel.addView(exportLabel,elp);
  LinearLayout exports=new LinearLayout(this);exports.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams exp=new LinearLayout.LayoutParams(-1,-2);exp.topMargin=dp(4);panel.addView(exports,exp);
  LinearLayout exportRow=new LinearLayout(this);exports.addView(exportRow,new LinearLayout.LayoutParams(-1,-2));
  TextView saveSrt=overlayAction("Guardar SRT",0xFF176E70,Color.WHITE);addOverlayItem(exportRow,saveSrt,true);saveSrt.setOnClickListener(v->saveSrt());
  TextView saveMp3=overlayAction("Guardar MP3",0xFF176E70,Color.WHITE);addOverlayItem(exportRow,saveMp3,false);saveMp3.setOnClickListener(v->exportMp3());
  TextView save=overlayAction("Guardar TXT",0xFF176E70,Color.WHITE);LinearLayout.LayoutParams txp=new LinearLayout.LayoutParams(-1,dp(48));txp.topMargin=dp(5);exports.addView(save,txp);save.setOnClickListener(v->saveTranscript());
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(dp(380),getResources().getDisplayMetrics().widthPixels-dp(24)),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=dp(12);p.y=dp(48);
  header.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=p.x;py=p.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){int maxX=Math.max(0,getResources().getDisplayMetrics().widthPixels-panel.getWidth());int maxY=Math.max(0,getResources().getDisplayMetrics().heightPixels-panel.getHeight());p.x=Math.max(0,Math.min(maxX,px+(int)(e.getRawX()-x)));p.y=Math.max(0,Math.min(maxY,py+(int)(e.getRawY()-y)));wm.updateViewLayout(panel,p);return true;}return e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL;}});
  wm.addView(panel,p);
 }
 private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
 private android.graphics.drawable.GradientDrawable rounded(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView overlayLabel(String text,int color){TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(10);v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
 private TextView overlayAction(String text,int color,int textColor){TextView v=new TextView(this);v.setText(text);v.setTextColor(textColor);v.setTextSize(12);v.setTypeface(null,android.graphics.Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setPadding(dp(3),0,dp(3),0);v.setMinHeight(dp(48));v.setBackground(rounded(color,12));v.setClickable(true);v.setFocusable(true);v.setContentDescription(text);return v;}
 private void addOverlayItem(LinearLayout row,View item,boolean first){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(!first)lp.leftMargin=dp(6);row.addView(item,lp);}
 private void show(String text){handler.post(()->{if(!dead && status!=null)status.setText(text);});}
 private void frame(){
  Image image=null;
  try{
   image=reader.acquireLatestImage();if(image==null)return;
   long now=SystemClock.elapsedRealtime();if(dead||paused||!ready||ocrPending||now-lastFrame<FRAME_INTERVAL_MS)return;
   lastFrame=now;Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int w=image.getWidth(),h=image.getHeight();int padded=plane.getRowStride()/plane.getPixelStride();
   Bitmap full=Bitmap.createBitmap(padded,h,Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(buffer);
   int height=(int)(h*.35);int y=region==0?(int)(h*.65):region==1?(int)(h*.325):region==2?0:Math.max(0,Math.min(h-height,(int)(h*((zone+20)/100f)-height/2f)));
   int[] loc=new int[2];panel.getLocationOnScreen(loc);Canvas canvas=new Canvas(full);Paint paint=new Paint();paint.setColor(Color.BLACK);canvas.drawRect(loc[0],loc[1],loc[0]+panel.getWidth(),loc[1]+panel.getHeight(),paint);
   Bitmap crop=Bitmap.createBitmap(full,0,y,w,height);full.recycle();
   ocrPending=true;
   recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener(text->{if(dead)return;String value=selectSubtitleText(text,w,height);String key=normalize(value);
    if(key.length()<2||looksLikePlayerUi(value)){candidate="";stable=0;return;}
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
   if(box==null||value.length()<2||looksLikePlayerUi(value))continue;
   if(box.width()<imageWidth*.08f||box.height()<cropHeight*.012f)continue;
   float centerX=box.centerX();if(centerX<imageWidth*.05f||centerX>imageWidth*.95f)continue;
   lines.add(line);
  }
  if(lines.isEmpty())return "";
  int targetY=cropHeight/2;
  Collections.sort(lines,(a,b)->Integer.compare(Math.abs(a.getBoundingBox().centerY()-targetY),Math.abs(b.getBoundingBox().centerY()-targetY)));
  Text.Line first=lines.get(0);ArrayList<String> chosen=new ArrayList<>();chosen.add(first.getText().trim());
  if(lines.size()>1){Text.Line second=lines.get(1);if(Math.abs(second.getBoundingBox().centerY()-first.getBoundingBox().centerY())<cropHeight*.20f)chosen.add(second.getText().trim());}
  return String.join(" ",chosen).replaceAll("\\s+"," ").trim();
 }
 private boolean looksLikePlayerUi(String value){
  String compact=value.trim();
  // Playback counters such as 03:27 / 07:49 are not subtitle dialogue.
  if(compact.matches("(?i)^[\\d\\s:：./\\-]+$"))return true;
  if(compact.matches("(?i)^\\d{1,2}[:：]\\d{2}(\\s*[/／]\\s*\\d{1,2}[:：]\\d{2})?$"))return true;
  return false;
 }
 private String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]","").trim().replaceAll("\\s+"," ");}
 private boolean rememberUnique(String key){
  long now=SystemClock.elapsedRealtime();
  Iterator<Map.Entry<String,Long>> it=recent.entrySet().iterator();
  while(it.hasNext())if(now-it.next().getValue()>12000)it.remove();
  if(recent.containsKey(key))return false;
  recent.put(key,now);
  while(recent.size()>32)recent.remove(recent.keySet().iterator().next());
  return true;
 }
 private void enqueueDetected(String value,String key){
  // Audio and OCR call the same deduplicator. This prevents a visible subtitle
  // from being spoken again when the recognizer hears the same dialogue.
  if(!rememberUnique(key))return;
  if(speechQueue.size()>=MAX_PENDING){show("La cola está llena; se omitió un texto. Pulsa Vaciar para reiniciar.");return;}
  captureTimes.put(value,SystemClock.elapsedRealtime());
  speechQueue.add(value);
  show("Detectado · en cola: "+speechQueue.size()+" · "+value);
  processSpeechQueue();
 }
 private void processSpeechQueue(){
  if(dead||paused||!ready||speaking||speechQueue.isEmpty())return;
  String original=speechQueue.poll();boolean fromAudio=audioQueueKeys.remove(normalize(original));int itemSource=fromAudio?audioSource:source;Translator itemTranslator=fromAudio?audioTranslator:translator;speaking=true;currentOriginal=original;
  final long operation=++activeOperation;
  handler.postDelayed(()->{
   if(dead||paused||!speaking||activeOperation!=operation)return;
   activeOperation++;speaking=false;if(tts!=null)tts.stop();
   show("Una frase tardó demasiado; continúo con la siguiente.");processSpeechQueue();
  },30000);
  if(itemSource==0){recordCaption(original,original,fromAudio);speak(original,operation);return;}
  show("Traduciendo "+LANG_NAMES[itemSource]+" a español…");
  itemTranslator.translate(original).addOnSuccessListener(translated->{
   if(dead||paused||activeOperation!=operation)return;
   recordCaption(original,translated,fromAudio);speak(translated,operation);
  }).addOnFailureListener(e->{
   if(dead||paused||activeOperation!=operation)return;
   activeOperation++;speaking=false;String reason=e.getLocalizedMessage();
   show("No se tradujo esta frase"+(reason==null?".":": "+reason));processSpeechQueue();
  });
 }
 private static final class CaptionRow { final String text; final long startMs; CaptionRow(String t,long s){text=t;startMs=s;} }
 private void recordCaption(String original,String translated,boolean fromAudio){
  int originalSource=fromAudio?audioSource:source;String item=originalSource==0?translated:original+"\n→ "+translated;
  if(savedCaptions.isEmpty())savedCaptions.add("SubVoz · Transcripción de subtítulos");
  savedCaptions.add(item);
  long when=captureTimes.containsKey(original)?captureTimes.remove(original):SystemClock.elapsedRealtime();
  long rel=Math.max(0,when-sessionStarted); rel=Math.max(rel,lastSrtStart); lastSrtStart=rel;
  srtRows.add(new CaptionRow(translated,rel));
  if(translated!=null&&!translated.trim().isEmpty())spokenSpanish.add(translated.trim());
 }
 private void saveTranscript(){
  if(savedCaptions.isEmpty()){Toast.makeText(this,"Todavía no hay subtítulos para guardar.",Toast.LENGTH_SHORT).show();return;}
  String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
  String filename="SubVoz_"+stamp+".txt";
  ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SubVoz");values.put(MediaStore.MediaColumns.IS_PENDING,1);
  android.net.Uri uri=null;
  try{
   uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(uri==null)throw new java.io.IOException("No se pudo crear el archivo");
   try(OutputStream out=getContentResolver().openOutputStream(uri);BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8))){
    writer.write("SubVoz · subtítulos guardados\nIdioma original: "+LANG_NAMES[source]+"\nFecha: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date())+"\n\n");
    for(String caption:savedCaptions){writer.write(caption);writer.write("\n\n");}
   }
   ContentValues readyValues=new ContentValues();readyValues.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,readyValues,null,null);
   Toast.makeText(this,"Guardado en Descargas/SubVoz/"+filename,Toast.LENGTH_LONG).show();
  }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);Toast.makeText(this,"No se pudo guardar. Comprueba el espacio disponible.",Toast.LENGTH_LONG).show();}
 }
 private void saveSrt(){
  if(srtRows.isEmpty()){Toast.makeText(this,"Todavía no hay subtítulos capturados para SRT.",Toast.LENGTH_SHORT).show();return;}
  ArrayList<CaptionRow> snapshot=new ArrayList<>(srtRows); long base=sessionStarted;
  String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()); String name="SubVoz_"+stamp+".srt";
  android.net.Uri uri=null;
  try{
   ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/x-subrip");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SubVoz");values.put(MediaStore.MediaColumns.IS_PENDING,1);
   uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new java.io.IOException("No se pudo crear el archivo");
   try(OutputStream out=getContentResolver().openOutputStream(uri);BufferedWriter w=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8))){
    
    for(int i=0;i<snapshot.size();i++){CaptionRow r=snapshot.get(i);long start=r.startMs;long duration=Math.max(1200,Math.min(5000,r.text.length()*55L));long end=start+duration;if(i+1<snapshot.size())end=Math.min(end,Math.max(start+500,snapshot.get(i+1).startMs));if(end<=start)end=start+1000;
     w.write(Integer.toString(i+1));w.write("\r\n");w.write(srtTime(start));w.write(" --> ");w.write(srtTime(end));w.write("\r\n");w.write(r.text.replace("\n"," ").replace("\r"," "));w.write("\r\n\r\n");}
   }
   ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,ready,null,null);Toast.makeText(this,"SRT guardado en Descargas/SubVoz/"+name,Toast.LENGTH_LONG).show();
  }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);Toast.makeText(this,"No se pudo guardar el SRT.",Toast.LENGTH_LONG).show();}
 }
 private String srtTime(long ms){long h=ms/3600000;long m=(ms%3600000)/60000;long s=(ms%60000)/1000;long x=ms%1000;return String.format(Locale.US,"%02d:%02d:%02d,%03d",h,m,s,x);}
 private void exportMp3(){
  if(exporting){Toast.makeText(this,"Ya se está generando el MP3.",Toast.LENGTH_SHORT).show();return;}
  if(spokenSpanish.isEmpty()){Toast.makeText(this,"Todavía no hay frases españolas capturadas.",Toast.LENGTH_SHORT).show();return;}
  final ArrayList<String> snapshot=new ArrayList<>(spokenSpanish);final long started=SystemClock.elapsedRealtime();
  exporting=true;show("Generando MP3 español desde las frases capturadas…");
  final TextToSpeech[] engine={null};
  engine[0]=new TextToSpeech(this,result->{
   if(dead){if(engine[0]!=null)engine[0].shutdown();return;}
   if(result!=TextToSpeech.SUCCESS){exporting=false;show("No se pudo iniciar TTS para MP3.");if(engine[0]!=null)engine[0].shutdown();return;}
   try{int support=engine[0].setLanguage(new Locale("es"));if(support==TextToSpeech.LANG_MISSING_DATA||support==TextToSpeech.LANG_NOT_SUPPORTED)throw new IllegalStateException("Voz española no disponible.");
    if(!requestedVoice.isEmpty()&&engine[0].getVoices()!=null)for(android.speech.tts.Voice v:engine[0].getVoices())if(v.getName().equals(requestedVoice)){engine[0].setVoice(v);break;}
    engine[0].setSpeechRate(speechRate);engine[0].setPitch(speechPitch);
    TtsMp3Exporter exporter=new TtsMp3Exporter(CaptureService.this,engine[0],snapshot,(ok,msg)->{exporting=false;activeExporter=null;engine[0].shutdown();if(!dead)show(msg);});
    activeExporter=exporter;engine[0].setOnUtteranceProgressListener(exporter);exporter.start();
   }catch(Throwable e){exporting=false;engine[0].shutdown();show(e.getMessage()==null?"No se pudo crear el MP3.":e.getMessage());}
  });
 }
 private void speak(String value,long operation){
  if(dead||paused||activeOperation!=operation)return;
  if(!voice){show(value+"\nVoz española no disponible · sin audio");speaking=false;activeOperation++;handler.postDelayed(()->processSpeechQueue(),2500);return;}
  show(value+"\nCola: "+speechQueue.size());
  int result=tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"caption-"+operation);
  if(result==TextToSpeech.ERROR){speaking=false;activeOperation++;show("El motor de voz no pudo leer esta frase.");processSpeechQueue();}
 }

 @Override public void onDestroy(){dead=true;handler.removeCallbacksAndMessages(null);stopAudioCapture();if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(recognizer!=null)recognizer.close();if(translator!=null)translator.close();if(audioTranslator!=null)audioTranslator.close();if(voskModel!=null)voskModel.close();if(activeExporter!=null)activeExporter.cancel();if(tts!=null){tts.stop();tts.shutdown();}speechQueue.clear();super.onDestroy();}
}
