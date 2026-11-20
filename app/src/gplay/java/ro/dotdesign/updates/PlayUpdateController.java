package ro.dotdesign.updates;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import java.util.function.BooleanSupplier;

/** UI only. Captures/calls have priority and remain outside this controller. */
public final class PlayUpdateController implements UpdateHandle {
    private static final int REQUEST=6142;
    private final Activity activity;
    private final UpdateRuntime runtime;
    private final BooleanSupplier busy;
    private final Runnable criticalAccess;
    private final int title,body,update,later,close,ready,restart,optional,accent,newsLabel;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed,destroyed,inFlow,downloaded,launching,resumeImmediate;
    private AlertDialog dialog;
    private final InstallStateUpdatedListener listener=this::installState;
    private void installState(com.google.android.play.core.install.InstallState state) {
        if(state.installStatus()==InstallStatus.DOWNLOADED)downloaded=true;
        if(state.installStatus()==InstallStatus.FAILED){inFlow=false;runtime.unavailable();}
        if(state.installStatus()==InstallStatus.CANCELED)inFlow=false;
    }
    public PlayUpdateController(Activity activity,UpdateRuntime runtime,BooleanSupplier busy,Runnable criticalAccess,
            int title,int body,int update,int later,int close,int ready,int restart,int optional,int accent,int newsLabel) {
        this.activity=activity;this.runtime=runtime;this.busy=busy;this.criticalAccess=criticalAccess;
        this.title=title;this.body=body;this.update=update;this.later=later;this.close=close;this.ready=ready;this.restart=restart;this.optional=optional;this.accent=accent;this.newsLabel=newsLabel;
        runtime.manager.registerListener(listener);
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!resumed||destroyed)return;
        runtime.refresh();
        boolean unsafe=busy.getAsBoolean();
        if(unsafe||!runtime.online()){dismiss();}
        else if(resumeImmediate&&!inFlow&&!launching&&dialog==null)resumeUpdate();
        else if(downloaded&&!inFlow&&!launching&&dialog==null)showDownloaded();
        else if(runtime.mode()==UpdateDecision.Mode.NONE){dismiss();}
        else if(!inFlow&&!launching&&dialog==null) {
            if(downloaded)showDownloaded();
            else if(runtime.required())showRequired();
            else if(promptDue())showOptional();
        }
        handler.postDelayed(this,1000);
    }};
    @Override public void onResume(){
        if(!runtime.enabled())return;
        resumed=true;handler.removeCallbacks(tick);handler.post(tick);
        runtime.manager.getAppUpdateInfo().addOnSuccessListener(info->{
            if(destroyed)return;
            if(info.installStatus()==InstallStatus.DOWNLOADED)downloaded=true;
            if(info.updateAvailability()==UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)resumeImmediate=true;
        }).addOnFailureListener(error->{runtime.unavailable();});
    }
    @Override public void onPause(){resumed=false;handler.removeCallbacks(tick);dismiss();}
    @Override public void onDestroy(){destroyed=true;onPause();runtime.manager.unregisterListener(listener);}
    @Override public boolean onActivityResult(int requestCode,int resultCode){
        if(requestCode!=REQUEST)return false;
        inFlow=false;
        resumeImmediate=false;
        if(resultCode!=Activity.RESULT_OK && resultCode!=Activity.RESULT_CANCELED)runtime.unavailable();
        return true;
    }
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void showRequired(){
        showCard(true);
    }
    private void showOptional(){
        UpdateRuntime.Snapshot s=runtime.snapshot();
        if(s!=null)activity.getSharedPreferences("app-update-prompts",0).edit().putLong("shown_"+s.info.availableVersionCode(),System.currentTimeMillis()).apply();
        activity.getIntent().removeExtra("show_app_update");
        showCard(false);
    }
    private boolean promptDue(){
        if(activity.getIntent().getBooleanExtra("show_app_update",false))return true;
        UpdateRuntime.Snapshot s=runtime.snapshot();if(s==null)return false;
        return UpdateDecision.shouldPrompt(System.currentTimeMillis(),activity.getSharedPreferences("app-update-prompts",0).getLong("shown_"+s.info.availableVersionCode(),0));
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private void showCard(boolean required){
        boolean dark=(activity.getResources().getConfiguration().uiMode&0x30)==0x20;
        int foreground=dark?0xfff7f5fc:0xff25252c, muted=dark?0xffc7c1ce:0xff77747f;
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(20),dp(24),dp(10));
        root.setBackground(shape(dark?0xff25232b:0xfffffefa,28));
        int screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
        LimitedScroll upper=new LimitedScroll(Math.max(dp(150),screenHeight-dp(220)));
        upper.setFillViewport(false);upper.setVerticalScrollBarEnabled(false);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        UpdateIllustration illustration=new UpdateIllustration(activity,accent);
        content.addView(illustration,new LinearLayout.LayoutParams(dp(126),dp(122)));
        int ink=accent==0xff00ac78?(dark?0xff9eedd0:0xff008761):(dark?0xffddd4ee:0xff5942bd);
        TextView brand=text(activity.getApplicationInfo().loadLabel(activity.getPackageManager()).toString(),12,ink,true);content.addView(brand);
        TextView heading=text(activity.getString(title),26,foreground,true);heading.setAccessibilityHeading(true);heading.setPadding(0,dp(6),0,dp(12));content.addView(heading);
        TextView description=text(activity.getString(required?body:optional),14,muted,false);description.setPadding(0,0,0,dp(20));content.addView(description);
        UpdateRuntime.Snapshot s=runtime.snapshot();
        if(s!=null&&s.notes.length>0){
            LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(14),dp(16),dp(10));
            panel.setBackground(shape(dark?0xff34303f:(accent==0xff00ac78?0xffeaf7f0:0xfff2eefc),17));
            TextView label=text(activity.getString(newsLabel),12,ink,true);label.setAccessibilityHeading(true);label.setGravity(Gravity.START);label.setPadding(0,0,0,dp(8));panel.addView(label);
            LimitedScroll notes=new LimitedScroll(dp(156));notes.setTag("app-update-news");notes.setVerticalScrollBarEnabled(true);notes.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            LinearLayout bullets=new LinearLayout(activity);bullets.setOrientation(LinearLayout.VERTICAL);
            for(String line:s.notes){
                LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
                TextView dot=text("•",14,accent,true);dot.setGravity(Gravity.TOP);row.addView(dot,new LinearLayout.LayoutParams(dp(16),ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView point=text(line,13,muted,false);point.setGravity(Gravity.START);point.setPadding(0,0,dp(5),dp(8));
                row.addView(point,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));bullets.addView(row);
            }
            notes.addView(bullets);
            panel.addView(notes);content.addView(panel,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        upper.addView(content);root.addView(upper);
        Button primary=new Button(activity);primary.setText(update);primary.setTextSize(16);primary.setAllCaps(false);primary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);primary.setTextColor(Color.WHITE);
        primary.setBackground(shape(accent,16));primary.setMinHeight(dp(52));primary.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);action.topMargin=dp(20);root.addView(primary,action);
        Button secondary=new Button(activity);secondary.setText(required?close:later);secondary.setTextColor(muted);secondary.setTextSize(13);secondary.setAllCaps(false);secondary.setBackgroundColor(Color.TRANSPARENT);secondary.setMinHeight(dp(48));
        root.addView(secondary,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog=new AlertDialog.Builder(activity).setView(root).setCancelable(!required).create();
        dialog.setOnCancelListener(d->dialog=null);
        primary.setOnClickListener(v->{dismiss();start(required?AppUpdateType.IMMEDIATE:AppUpdateType.FLEXIBLE);});
        secondary.setOnClickListener(v->{dismiss();if(required){if(criticalAccess!=null)criticalAccess.run();else activity.finish();}});
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(dp(360),activity.getResources().getDisplayMetrics().widthPixels-dp(32)),ViewGroup.LayoutParams.WRAP_CONTENT);}
    }
    private final class LimitedScroll extends ScrollView {
        private final int maximum;
        LimitedScroll(int maximum){super(activity);this.maximum=maximum;}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
            boolean consumed=super.dispatchTouchEvent(event);
            if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
            return consumed;
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?maximum:Math.min(maximum,MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST));
        }
    }
    private void showDownloaded(){
        dialog=new AlertDialog.Builder(activity).setMessage(ready)
                .setPositiveButton(restart,(d,w)->{
                    dialog=null;
                    if(resumed&&!busy.getAsBoolean())runtime.manager.completeUpdate().addOnFailureListener(e->runtime.unavailable());
                }).setNegativeButton(later,(d,w)->{dialog=null;downloaded=false;}).create();
        dialog.setOnCancelListener(d->{dialog=null;downloaded=false;});dialog.show();
    }
    @SuppressWarnings("deprecation")
    private void resumeUpdate(){
        launching=true;
        runtime.manager.getAppUpdateInfo().addOnSuccessListener(info->{
            launching=false;
            if(destroyed||!resumed||busy.getAsBoolean()||!runtime.online())return;
            resumeImmediate=false;
            if(info.updateAvailability()!=UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)return;
            try{inFlow=runtime.manager.startUpdateFlowForResult(info,activity,AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),REQUEST);}
            catch(Exception ignored){inFlow=false;runtime.unavailable();}
        }).addOnFailureListener(error->{launching=false;resumeImmediate=false;runtime.unavailable();});
    }
    @SuppressWarnings("deprecation")
    private void start(int type){
        if(launching||!resumed||busy.getAsBoolean()||runtime.mode()==UpdateDecision.Mode.NONE)return;
        launching=true;
        // AppUpdateInfo is single-use. Always obtain a fresh instance for retries/resume.
        runtime.manager.getAppUpdateInfo().addOnSuccessListener(info->{
            launching=false;
            if(destroyed||!resumed||busy.getAsBoolean()||!runtime.online())return;
            if(info.installStatus()==InstallStatus.DOWNLOADED){downloaded=true;return;}
            boolean available=info.updateAvailability()==UpdateAvailability.UPDATE_AVAILABLE
                    ||info.updateAvailability()==UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
            if(!available||!info.isUpdateTypeAllowed(type)){runtime.unavailable();return;}
            try {inFlow=runtime.manager.startUpdateFlowForResult(info,activity,AppUpdateOptions.newBuilder(type).build(),REQUEST);}
            catch(Exception ignored){inFlow=false;runtime.unavailable();}
            if(!inFlow)runtime.unavailable();
        }).addOnFailureListener(error->{launching=false;runtime.unavailable();});
    }
}
