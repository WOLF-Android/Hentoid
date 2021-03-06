package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.threeten.bp.Instant;

import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.BuildConfig;
import timber.log.Timber;

public class LogUtil {

    private LogUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public static class LogEntry {
        private final Instant timestamp;
        private final String message;

        public LogEntry(@NonNull String message) {
            this.timestamp = Instant.now();
            this.message = message;
        }

        public LogEntry(@NonNull Instant timestamp, @NonNull String message) {
            this.timestamp = timestamp;
            this.message = message;
        }
    }

    public static class LogInfo {
        private String fileName = "";
        private String logName = "";
        private String noDataMessage = "";
        private String header = "";
        private List<LogEntry> log = Collections.emptyList();

        public void setFileName(@NonNull String fileName) {
            this.fileName = fileName;
        }

        public void setLogName(@NonNull String logName) {
            this.logName = logName;
        }

        public void setNoDataMessage(@NonNull String noDataMessage) {
            this.noDataMessage = noDataMessage;
        }

        public void setHeader(@NonNull String header) {
            this.header = header;
        }

        public void setLog(@NonNull List<LogEntry> log) {
            this.log = log;
        }
    }

    private static String buildLog(@Nonnull LogInfo info) {
        StringBuilder logStr = new StringBuilder();
        logStr.append(info.logName).append(" log : begin").append(LINE_SEPARATOR);
        logStr.append(String.format("Hentoid ver: %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)).append(LINE_SEPARATOR);
        if (info.log.isEmpty())
            logStr.append("No activity to report - ").append(info.noDataMessage).append(LINE_SEPARATOR);
        else {
            // Log beginning, end and duration
            Instant beginning = Stream.of(info.log).filter(l -> l.timestamp != null).min((a, b) -> a.timestamp.compareTo(b.timestamp)).get().timestamp;
            Instant end = Stream.of(info.log).filter(l -> l.timestamp != null).max((a, b) -> a.timestamp.compareTo(b.timestamp)).get().timestamp;
            long durationMs = end.toEpochMilli() - beginning.toEpochMilli();
            logStr.append("Start : ").append(beginning.toString()).append(LINE_SEPARATOR);
            logStr.append("End : ").append(end.toString()).append(" (").append(durationMs / 1000).append(" s)").append(LINE_SEPARATOR);
            logStr.append("-----").append(LINE_SEPARATOR);

            // Log header and entries
            if (!info.header.isEmpty()) logStr.append(info.header).append(LINE_SEPARATOR);
            for (LogEntry entry : info.log)
                logStr.append(entry.message).append(LINE_SEPARATOR);
        }

        logStr.append(info.logName).append(" log : end");

        return logStr.toString();
    }

    @Nullable
    public static File writeLog(@Nonnull Context context, @Nonnull LogInfo info) {
        // Create the log
        String log = buildLog(info);

        // Save it
        File rootFolder;
        try {
            String settingDir = Preferences.getRootFolderName();
            if (!settingDir.isEmpty() && FileHelper.isWritable(new File(settingDir))) {
                rootFolder = new File(settingDir); // Use selected and output-tested location (possibly SD card)
            } else {
                rootFolder = FileHelper.getDefaultDir(context, ""); // Fallback to default location (phone memory)
            }
            File cleanupLogFile = new File(rootFolder, info.fileName + ".txt");
            FileHelper.saveBinaryInFile(cleanupLogFile, log.getBytes());
            return cleanupLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
