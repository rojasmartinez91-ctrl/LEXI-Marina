package cl.lexi;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class LessonArt extends View {
    private static final int[] COLORS = {0xffcac3cb, 0xffedb37b, 0xff77c9ff, 0xfffa8c7f,
        0xffe2aa8a, 0xfff5cc9f, 0xff97e8df, 0xffa6aafa};
    private static final int[] SIZES = {4, 6, 7, 5, 13, 11, 8, 8};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean solar;
    private int selected;
    LessonArt(Context context, boolean solar) {
        super(context);
        this.solar = solar;
        setContentDescription(solar ? "Diagrama del sistema solar, no a escala" : "Cuatro grupos de ocho puntos");
    }
    void select(int planet) { selected = planet; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth() / 320f, getHeight() / 100f);
        c.save();
        c.translate((getWidth() - 320 * scale) / 2, (getHeight() - 100 * scale) / 2);
        c.scale(scale, scale);
        paint.setColor(0xff332357);
        c.drawRoundRect(0, 0, 320, 100, 18, 18, paint);
        if (solar) {
            paint.setColor(0xffffca74);
            c.drawCircle(29, 50, 21, paint);

            for (int i = 0; i < COLORS.length; i++) {
                int x = 65 + i * 33;
                if (selected == i) {
                    paint.setColor(0xfff4eaff);
                    c.drawCircle(x, 50, SIZES[i] + 4, paint);
                }
                paint.setColor(COLORS[i]);
                c.drawCircle(x, 50, SIZES[i], paint);
            }
        } else {
            for (int group = 0; group < 4; group++) {
                paint.setColor(group % 2 == 0 ? 0xff8df4e4 : 0xfff5a9d9);
                for (int dot = 0; dot < 8; dot++)
                    c.drawCircle(19 + group * 76 + dot % 4 * 13, 38 + dot / 4 * 23, 4, paint);
            }
        }
        c.restore();
    }
}

