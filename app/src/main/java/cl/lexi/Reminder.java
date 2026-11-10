package cl.lexi;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;

public final class Reminder extends BroadcastReceiver {
    private static final String FIRE = "cl.lexi.REMIND";
    private static final String DONE = "cl.lexi.DONE";
    private static final String CHANNEL = "daily_tasks";
    private static final int ID = 21;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("lexi", Context.MODE_PRIVATE);
    }
    private static PendingIntent action(Context c, String action) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, Reminder.class).setAction(action),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static boolean done(Context c) {
        return LocalDate.now().toString().equals(prefs(c).getString("task_done", ""));
    }
    static void complete(Context c) {
        prefs(c).edit().putString("task_done", LocalDate.now().toString()).apply();
        reset(c);
    }
    static void ensure(Context c) {
        if (prefs(c).getBoolean("reminder", false)) schedule(c, false);
    }
    static void reset(Context c) {
        c.getSystemService(AlarmManager.class).cancel(action(c, FIRE));
        c.getSystemService(NotificationManager.class).cancel(ID);
        prefs(c).edit().remove("next_reminder").apply();
        ensure(c);
    }
    private static void schedule(Context c, boolean retry) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime evening = now.toLocalDate().atTime(21, 0).atZone(now.getZone());
        long current = now.toInstant().toEpochMilli();
        long saved = prefs(c).getLong("next_reminder", 0);
        long next;
        if (done(c)) next = evening.plusDays(1).toInstant().toEpochMilli();
        else if (now.isBefore(evening)) next = evening.toInstant().toEpochMilli();
        else if (retry) {
            ZonedDateTime following = now.plusMinutes(5);
            next = (following.toLocalDate().equals(now.toLocalDate()) ? following : evening.plusDays(1))
                .toInstant().toEpochMilli();
        } else if (saved > current) next = saved;
        else next = current + 1000;

        PendingIntent pending = action(c, FIRE);
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending); }
            catch (SecurityException e) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending); }
        } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
        prefs(c).edit().putLong("next_reminder", next).apply();
    }
    @Override public void onReceive(Context c, Intent intent) {
        if (DONE.equals(intent.getAction())) { complete(c); return; }
        if (!FIRE.equals(intent.getAction())) { reset(c); return; }
        if (!prefs(c).getBoolean("reminder", false)) return;
        if (!done(c) && !LocalTime.now().isBefore(LocalTime.of(21, 0))) {
            NotificationManager notifications = c.getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Tareas de LEXI",
                NotificationManager.IMPORTANCE_HIGH));
            Notification notification = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("LEXI · Lavarse los dientes")
                .setContentText("Marina, toca Listo cuando termines.")
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, "Listo", action(c, DONE)).build())
                .build();
            if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                notifications.notify(ID, notification);
            }
        }
        schedule(c, true);
    }
}

