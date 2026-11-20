package ro.dotdesign.updates;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Vector illustration, drawn at the actual density; no bitmap or network dependency. */
final class UpdateIllustration extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final RectF bounds=new RectF();
    private final Path stroke=new Path();
    public UpdateIllustration(Context context){this(context,0xff6b50e8);}
    public UpdateIllustration(Context context,android.util.AttributeSet attrs){this(context,0xff6b50e8);}
    UpdateIllustration(Context context,int accent){super(context);this.accent=accent;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private RectF rect(float l,float t,float r,float b){bounds.set(l,t,r,b);return bounds;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/120f;
        canvas.save();canvas.translate((getWidth()-120*scale)/2,(getHeight()-120*scale)/2);canvas.scale(scale,scale);
        paint.setColor(accent);paint.setAlpha(16);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(60,58,47,paint);
        paint.setAlpha(10);canvas.drawOval(rect(34,107,86,114),paint);
        canvas.save();canvas.rotate(-9,60,61);
        paint.setAlpha(32);canvas.drawRoundRect(rect(39,27,88,97),15,15,paint);
        paint.setColor(0xffffffff);paint.setAlpha(255);canvas.drawRoundRect(rect(35,23,84,93),15,15,paint);
        paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.3f);canvas.drawRoundRect(rect(35,23,84,93),15,15,paint);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setAlpha(70);canvas.drawLine(50,30,69,30,paint);
        paint.setAlpha(255);paint.setStrokeWidth(4);stroke.reset();stroke.moveTo(60,69);stroke.lineTo(60,45);stroke.moveTo(51,54);stroke.lineTo(60,45);stroke.lineTo(69,54);canvas.drawPath(stroke,paint);
        paint.setStrokeWidth(2);canvas.drawArc(rect(53,71,67,80),15,150,false,paint);canvas.restore();
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(90,84,14,paint);paint.setColor(0xffffffff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);
        stroke.reset();stroke.moveTo(84,84);stroke.lineTo(88,88);stroke.lineTo(96,79);canvas.drawPath(stroke,paint);
        paint.setColor(accent);paint.setStrokeWidth(2);canvas.drawLine(100,24,100,34,paint);canvas.drawLine(95,29,105,29,paint);
        paint.setAlpha(100);canvas.drawLine(23,68,23,76,paint);canvas.drawLine(19,72,27,72,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(29,31,2.5f,paint);canvas.drawCircle(106,59,2.2f,paint);
        canvas.restore();
    }
}
