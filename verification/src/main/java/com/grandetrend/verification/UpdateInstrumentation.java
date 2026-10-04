package com.grandetrend.verification;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

/** Framework-only test runner for the actual minified release APKs, independent of app code. */
public final class UpdateInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            boolean seed = "seed".equals(arguments.getString("phase"));
            long version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
            long expected = Long.parseLong(arguments.getString("expectedVersion", seed ? "2" : "3"));
            check(version == expected, "Wrong app version " + version + ", expected " + expected);
            File marker = new File(context.getFilesDir(), "upgrade-retention.sha256");
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("grades.db").getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                if (seed) {
                    try (Cursor rows = db.rawQuery("SELECT * FROM exams", null)) { check(rows.getCount() == 0, "Requires a clean test emulator"); }
                    db.beginTransaction();
                    try {
                        db.execSQL("INSERT INTO exams VALUES ('upgrade-1','升级保留月考','2025-09-01','高二上',321,NULL,'121.125',NULL,'保留备注',7)");
                        db.execSQL("INSERT INTO exams VALUES ('upgrade-2','同日第二场','2025-09-01','高二上',123,111,NULL,'198.875','同日稳定排序',9)");
                        db.execSQL("INSERT INTO scores VALUES ('upgrade-1','MATH','120','0.00000000',NULL,NULL)");
                        db.execSQL("INSERT INTO scores VALUES ('upgrade-1','BIOLOGY','80.50','71.125',NULL,120)");
                        db.execSQL("INSERT INTO scores VALUES ('upgrade-2','MATH','150','111.125',NULL,100)");
                        db.execSQL("INSERT INTO scores VALUES ('upgrade-2','BIOLOGY','100',NULL,'87.750',90)");
                        db.execSQL("INSERT OR REPLACE INTO preferences VALUES ('subjects','HISTORY,POLITICS')");
                        db.execSQL("INSERT OR REPLACE INTO preferences VALUES ('metrics_total','RAW,AWARDED_RANK')");
                        db.execSQL("INSERT OR REPLACE INTO preferences VALUES ('metrics_MATH','')");
                        db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                    Files.write(marker.toPath(), digest(db).getBytes(StandardCharsets.UTF_8));
                } else {
                    check(marker.isFile(), "App-private marker disappeared during update");
                    check(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).equals(digest(db)), "User records or preferences changed during update");
                }
                check(db.getVersion() == 1, "Schema must stay at version 1");
                result.putString("databaseSHA256", digest(db));
            }
            result.putString("retention", "passed");
            result.putString("phase", seed ? "seed" : "verify");
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("retention", "failed"); result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static String digest(SQLiteDatabase db) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String sql : new String[] {"SELECT * FROM exams ORDER BY id", "SELECT * FROM scores ORDER BY exam_id,subject", "SELECT * FROM preferences ORDER BY key"}) {
            try (Cursor cursor = db.rawQuery(sql, null)) {
                while (cursor.moveToNext()) {
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        String value = cursor.isNull(i) ? "<NULL>" : cursor.getString(i);
                        text.append(value.length()).append(':').append(value).append('|');
                    }
                    text.append('\n');
                }
            }
        }
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
