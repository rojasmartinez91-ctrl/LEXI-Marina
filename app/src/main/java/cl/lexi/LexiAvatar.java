package cl.lexi;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class LexiAvatar extends View {
    enum Mood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY, SURPRISED, CONFUSED, OFFLINE }
    private static final float[] EYES = {68, 111};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visibleArea = new Rect();
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) invalidate();
    }

    private Mood mood = Mood.IDLE;
    private boolean reduced;
    private long changedAt;
    private float previousGaze, targetGaze;

    private float gaze(long now) {
        if (reduced) return targetGaze;
        float fraction = Math.min(1f, (now - changedAt) / 180f);
        return previousGaze + (targetGaze - previousGaze) * fraction * fraction * (3f - 2f * fraction);
    }
    LexiAvatar(Context context) { super(context); setContentDescription("Avatar de LEXI"); }
    void show(Mood next) {
        if (mood == next) return;
        changedTo(next, SystemClock.uptimeMillis());
        invalidate();
    }

    private void changedTo(Mood next, long now) {
        previousGaze = gaze(now);
        targetGaze = next == Mood.THINKING ? 5f : 0f;
        changedAt = now;
        mood = next;
    }
    void reduceMotion(boolean value) { reduced = value; invalidate(); }

    private void oval(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        c.drawOval(x, y, x + w, y + h, paint);
    }
    private void capsule(Canvas c, float x, float y, float w, float h, float r, int color) {
        paint.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, r, r, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 200f;
        long ms = SystemClock.uptimeMillis();
        float wave = reduced ? 0 : (float)Math.sin(ms / 350.0);
        canvas.save();
        canvas.translate((getWidth() - scale * 200) / 2, (getHeight() - scale * 200) / 2);
        canvas.scale(scale, scale);
        oval(canvas, 10, 7, 180, 180, 0xffe9ddff);
        oval(canvas, 48, 171, 104, 12, 0xffcbb3f0);
        canvas.translate(0, wave * 3);
        canvas.rotate(mood == Mood.LISTENING || mood == Mood.SPEAKING ? wave * 2 : 0, 100, 100);
        capsule(canvas, 60, 124, 80, 49, 24, 0xff6b49b6);
        oval(canvas, 88, 144, 24, 12, 0xff65e2d0);
        capsule(canvas, 91, 20, 8, 28, 4, 0xff6041a0);
        oval(canvas, 86, 15, 18, 18, 0xffed9fc9);
        capsule(canvas, 30, 68, 28, 46, 13, 0xff8060c9);
        capsule(canvas, 142, 68, 28, 46, 13, 0xff8060c9);
        capsule(canvas, 40, 41, 120, 100, 40, 0xff714cb7);
        capsule(canvas, 49, 54, 102, 72, 32, 0xff28204b);
        float eye = mood == Mood.SURPRISED ? 28 : 22;
        if (!reduced) {
            long blink = Math.abs(ms % 4300 - 4150);
            eye *= Math.max(0.14f, Math.min(1f, blink / 125f));
        }
        float shift = gaze(ms) + (mood == Mood.LISTENING ? wave * 2 : 0);
        for (float x : EYES) {
            oval(canvas, x + shift, 85 - eye / 2, 19, eye, 0xff8df4e4);
            if (eye > 4) oval(canvas, x + shift + 5, 80, 5, 5, Color.WHITE);
        }
        oval(canvas, 61, 101, 16, 7, 0xfff0a1cf);
        oval(canvas, 123, 101, 16, 7, 0xfff0a1cf);
        if (mood == Mood.SPEAKING) {
            float open = reduced ? 9 : 4 + (float)Math.abs(Math.sin(ms / 83.0)) * 13;
            oval(canvas, 90, 103, 20, open, 0xfff5a9d9);
        } else if (mood == Mood.SURPRISED) {
            oval(canvas, 94, 102, 12, 13, 0xfff5a9d9);
        } else {
            paint.setColor(0xfff5a9d9);
            boolean neutral = mood == Mood.CONFUSED || mood == Mood.OFFLINE;
            if (neutral) canvas.drawRoundRect(93, 107, 107, 110, 2, 2, paint);
            else canvas.drawArc(87, 98, 113, mood == Mood.HAPPY ? 120 : 114, 0, 180, true, paint);
        }
        if (mood == Mood.LISTENING || mood == Mood.THINKING) {
            for (int i = 0; i < 3; i++) {
                int color = reduced || (ms / 240) % 3 == i ? 0xff299d92 : 0xffb698e6;
                oval(canvas, 84 + i * 12, 181, 7, 7, color);
            }
        }
        canvas.restore();
        if (!reduced && isShown() && hasWindowFocus() && getGlobalVisibleRect(visibleArea)) {
            boolean active = mood == Mood.SPEAKING || mood == Mood.LISTENING ||
                mood == Mood.THINKING || ms - changedAt < 250;
            postInvalidateDelayed(active ? 33 : 80);
        }
    }
}


