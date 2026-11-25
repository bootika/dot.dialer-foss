package ro.dotdesign.support;

import android.content.Intent;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opens no external email client and sends no data. */
@RunWith(AndroidJUnit4.class)
public class SupportUiTest {
    @Test public void rotationPreservesTheForm() {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<SupportActivity> scenario=ActivityScenario.launch(new Intent(c,SupportActivity.class))) {
            capture(c,"support-default.png");
            scenario.onActivity(a->{((EditText)a.findViewById(SupportResources.DESCRIPTION_ID)).setText("Test Alexandru Butica");((CheckBox)a.findViewById(SupportResources.TECHNICAL_ID)).setChecked(false);});
            scenario.recreate();
            scenario.onActivity(a->{assertEquals("Test Alexandru Butica",((EditText)a.findViewById(SupportResources.DESCRIPTION_ID)).getText().toString());assertFalse(((CheckBox)a.findViewById(SupportResources.TECHNICAL_ID)).isChecked());});
        }
    }
    @Test public void missingEmailClientOffersCopyInsteadOfSending() {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        android.content.Context c=inst.getTargetContext();
        SupportActivity activity = launch(c);
        try {
            android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){
                @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){
                    if(Intent.ACTION_SENDTO.equals(intent.getAction())) throw new android.content.ActivityNotFoundException("Test client unavailable");
                    return null;
                }
            };
            inst.addMonitor(monitor);
            try {
                inst.runOnMainSync(()->{
                    ((EditText)activity.findViewById(SupportResources.DESCRIPTION_ID)).setText("Test Alexandru Butica");
                    findText(activity.getWindow().getDecorView(),activity.getString(SupportResources.string("send"))).performClick();
                    assertNotNull(activity.activeDialog);
                    assertTrue(activity.activeDialog.isShowing());
                    assertEquals(activity.getString(SupportResources.string("copy")),activity.activeDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getText().toString());
                });
            }finally{inst.removeMonitor(monitor);}
        } finally { inst.runOnMainSync(activity::finish); }
    }
    private void capture(android.content.Context c,String name) {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();inst.waitForIdleSync();
        android.graphics.Bitmap bitmap=inst.getUiAutomation().takeScreenshot();
        if(bitmap!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    @Test public void emailIntentIsReviewedAndContainsOnlySelectedFields() {
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        android.content.Context c=inst.getTargetContext();
        java.util.concurrent.atomic.AtomicReference<Intent> captured=new java.util.concurrent.atomic.AtomicReference<>();
        SupportActivity activity=launch(c);
        try {
            android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){
                @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){
                    if(!Intent.ACTION_SENDTO.equals(intent.getAction())) return null;
                    captured.set(intent);return new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null);
                }
            };
            inst.addMonitor(monitor);
            try {
                inst.runOnMainSync(()->{
                    SupportActivity a=activity;
                    View send=findText(a.getWindow().getDecorView(),a.getString(a.getResources().getIdentifier("support_send","string",a.getPackageName())));
                    send.performClick(); assertNull(captured.get());
                    ((EditText)a.findViewById(SupportResources.DESCRIPTION_ID)).setText("Test Alexandru Butica");
                    ((CheckBox)a.findViewById(SupportResources.TECHNICAL_ID)).setChecked(false);send.performClick();
                });
                Intent result=captured.get();assertNotNull(result);assertEquals(Intent.ACTION_SENDTO,result.getAction());
                String uri=result.getData().toString();
                assertEquals(c.getPackageName().contains("dictat")?"dictate@dotdesign.ro":"dialer@dotdesign.ro",android.net.MailTo.parse(uri).getTo());
                assertTrue(android.net.Uri.decode(uri).contains("Test Alexandru Butica"));
                assertFalse(android.net.Uri.decode(uri).contains("Device:"));
                assertFalse(android.net.Uri.decode(uri).contains("Android:"));
            }finally{inst.removeMonitor(monitor);}
        } finally { inst.runOnMainSync(activity::finish); }
    }
    private SupportActivity launch(android.content.Context c) {
        return (SupportActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(c,SupportActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private View findText(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    @Test public void formSurvivesRecreationAndHasCorrectRecipient() {
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(c, SupportActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SupportActivity a = (SupportActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText edit=a.findViewById(SupportResources.DESCRIPTION_ID);
            assertNotNull(edit);
            edit.setText("Test Alexandru Butica: verificare formular");
            CheckBox technical=a.findViewById(SupportResources.TECHNICAL_ID);
            assertTrue(technical.isChecked());
            technical.setChecked(false);
            String wanted=c.getPackageName().contains("dictat") ? "dictate@dotdesign.ro" : "dialer@dotdesign.ro";
            assertTrue(contains(a.getWindow().getDecorView(),wanted));
            android.os.Bundle saved=new android.os.Bundle();
            a.onSaveInstanceState(saved);
            assertEquals("Test Alexandru Butica: verificare formular",saved.getString("description"));
            assertFalse(saved.getBoolean("technical"));
            a.finish();
        });
    }
    private boolean contains(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return true;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),text))return true;}
        return false;
    }
}
