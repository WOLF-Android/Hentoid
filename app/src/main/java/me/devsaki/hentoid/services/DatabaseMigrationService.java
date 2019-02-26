package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Service responsible for migrating the oldHentoidDB to the ObjectBoxDB
 *
 * @see UpdateCheckService
 */
public class DatabaseMigrationService extends IntentService {

    private static boolean running;

    public DatabaseMigrationService() {
        super(DatabaseMigrationService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, DatabaseMigrationService.class);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;
        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
        Timber.w("Service destroyed");

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        cleanUpDB();
        migrate();
    }

    private void eventProgress(Content content, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, content, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, File importLogFile) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, importLogFile));
    }

    private void trace(int priority, List<String> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(s);
    }

    private void cleanUpDB() {
        Timber.d("Cleaning up DB.");
        Context context = HentoidApp.getAppContext();
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        db.deleteAllBooks();
    }

    /**
     * Migrate HentoidDB books to ObjectBoxDB
     */
    private void migrate() {
        int booksOK = 0;
        int booksKO = 0;
        long newKey;
        Content content;

        HentoidDB oldDB = HentoidDB.getInstance(this);
        ObjectBoxDB newDB = ObjectBoxDB.getInstance(this);

        List<Integer> bookIds = oldDB.selectMigrableContentIds();
        List<String> log = new ArrayList<>();
        SparseArray<Long> keyMapping = new SparseArray<>();

        trace(Log.INFO, log, "Books migration starting : %s books total", bookIds.size() + "");
        for (int i = 0; i < bookIds.size(); i++) {
            content = oldDB.selectContentById(bookIds.get(i));

            try {
                if (content != null) {
                    newKey = newDB.insertContent(content);
                    keyMapping.put(bookIds.get(i), newKey);
                    booksOK++;
                    trace(Log.DEBUG, log, "Migrate book OK : %s", content.getTitle());
                } else {
                    booksKO++;
                    trace(Log.WARN, log, "Migrate book KO : ID %s", bookIds.get(i) + "");
                }
            } catch (Exception e) {
                Timber.e(e, "Migrate book ERROR");
                booksKO++;
                if (null == content)
                    content = new Content().setTitle("none").setUrl("").setSite(Site.NONE);
                trace(Log.ERROR, log, "Migrate book ERROR : %s %s %s", e.getMessage(), bookIds.get(i) + "", content.getTitle());
            }

            eventProgress(content, bookIds.size(), booksOK, booksKO);
        }
        trace(Log.INFO, log, "Books migration complete : %s OK; %s KO", booksOK + "", booksKO + "");

        int queueOK = 0;
        int queueKO = 0;
        SparseIntArray queueIds = oldDB.selectQueueForMigration();
        trace(Log.INFO, log, "Queue migration starting : %s entries total", queueIds.size() + "");
        for (int i = 0; i < queueIds.size(); i++) {
            Long targetKey = keyMapping.get(queueIds.keyAt(i));

            if (targetKey != null) {
                newDB.insertQueue(targetKey, queueIds.get(queueIds.keyAt(i)));
                queueOK++;
                trace(Log.INFO, log, "Migrate queue OK : target ID %s", targetKey + "");
            } else {
                queueKO++;
                trace(Log.WARN, log, "Migrate queue KO : source ID %s", queueIds.keyAt(i) + "");
            }
        }
        trace(Log.INFO, log, "Queue migration complete : %s OK; %s KO", queueOK + "", queueKO + "");
        this.getApplicationContext().deleteDatabase(Consts.DATABASE_NAME);

        // Write log in root folder
        File importLogFile = writeMigrationLog(log);

        eventComplete(bookIds.size(), booksOK, booksKO, importLogFile);

        stopForeground(true);
        stopSelf();
    }

    private File writeMigrationLog(List<String> log) {
        // Create the log
        StringBuilder logStr = new StringBuilder();
        logStr.append("Migration log : begin").append(System.getProperty("line.separator"));
        if (log.isEmpty())
            logStr.append("No activity to report - No migrable content detected on existing database");
        else for (String line : log)
            logStr.append(line).append(System.getProperty("line.separator"));
        logStr.append("Migration log : end");

        // Save it
        File rootFolder;
        try {
            String settingDir = Preferences.getRootFolderName();
            if (!settingDir.isEmpty() && FileHelper.isWritable(new File(settingDir))) {
                rootFolder = new File(settingDir); // Use selected and output-tested location (possibly SD card)
            } else {
                rootFolder = FileHelper.getDefaultDir(this, ""); // Fallback to default location (phone memory)
            }
            File importLogFile = new File(rootFolder, "migration_log.txt");
            FileHelper.saveBinaryInFile(importLogFile, logStr.toString().getBytes());
            return importLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}