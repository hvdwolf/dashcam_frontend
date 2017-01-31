package tk.rabidbeaver.dashcam;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class BetterSQLiteOpenHelper extends SQLiteOpenHelper {

    BetterSQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public void onCreate(SQLiteDatabase db) {
        // note %f fractional (1/1000th) seconds
        db.execSQL("CREATE TABLE log (time TEXT PRIMARY KEY DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), type TEXT, value TEXT)");
        db.execSQL("CREATE TABLE gps (time TEXT PRIMARY KEY DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), value TEXT)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This is still first version, so there isn't anything to upgrade to.
        // DO NOTHING.
    }

}
