package com.zikra.tv;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
/** The existing Quran verse separator, rendered as a native drawable with its verse number. */
final class NativeAyahMark extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline=new Path();
    private final String number;
    NativeAyahMark(Context context,int ayah){number=String.valueOf(ayah);
        float[] points={24,2,28.7f,6.5f,35,5,37,11,43,13,41.5f,19.3f,46,24,41.5f,28.7f,43,35,37,37,35,43,28.7f,41.5f,24,46,19.3f,41.5f,13,43,11,37,5,35,6.5f,28.7f,2,24,6.5f,19.3f,5,13,11,11,13,5,19.3f,6.5f};
        outline.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)outline.lineTo(points[i],points[i+1]);outline.close();
    }
    public void draw(Canvas canvas){Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/48f,b.height()/48f);paint.setColor(0xffe5c77b);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.35f);canvas.drawPath(outline,paint);canvas.drawCircle(24,24,16.5f,paint);canvas.drawCircle(24,24,14,paint);paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextSize(number.length()>2?14:18);canvas.drawText(number,24,24-(paint.ascent()+paint.descent())/2,paint);canvas.restore();}
    public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
