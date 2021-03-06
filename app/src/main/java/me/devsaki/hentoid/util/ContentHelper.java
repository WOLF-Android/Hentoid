package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.JsonContent;
import timber.log.Timber;

import static me.devsaki.hentoid.util.FileHelper.getDefaultDir;
import static me.devsaki.hentoid.util.FileHelper.getExtSdCardFolder;
import static me.devsaki.hentoid.util.FileHelper.isSAF;
import static me.devsaki.hentoid.util.FileUtil.deleteQuietly;

/**
 * Utility class for Content-related operations
 */
public final class ContentHelper {

    private static final String UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";


    private ContentHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Open the app's web browser to view the given Content's gallery page
     * @param context Context to use for the action
     * @param content Content to view
     */
    public static void viewContentGalleryPage(@NonNull final Context context, @NonNull Content content) {
        viewContentGalleryPage(context, content, false);
    }

    /**
     * Open the app's web browser to view the given Content's gallery page
     * @param context Context to use for the action
     * @param content Content to view
     * @param wrapPin True if the intent should be wrapped with PIN protection
     */
    public static void viewContentGalleryPage(@NonNull final Context context, @NonNull Content content, boolean wrapPin) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(content.getGalleryUrl());
        intent.putExtras(builder.getBundle());
        if (wrapPin) intent = UnlockActivity.wrapIntent(context, intent);
        context.startActivity(intent);
    }

    /**
     * Update the given Content's JSON file with its current values
     * @param context Context to use for the action
     * @param content Content whose JSON file to update
     */
    public static void updateJson(@Nonnull Context context, @Nonnull Content content) {
        DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(content.getJsonUri()));
        if (null == file)
            throw new InvalidParameterException("'" + content.getJsonUri() + "' does not refer to a valid file");

        try {
            JsonHelper.updateJson(JsonContent.fromEntity(content), JsonContent.class, file);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", content.getJsonUri());
        }
    }

    /**
     * Create the given Content's JSON file and populate it with its current values
     * @param content Content whose JSON file to create
     */
    public static void createJson(@Nonnull Content content) {
        File dir = getContentDownloadDir(content);
        try {
            JsonHelper.createJson(JsonContent.fromEntity(content), JsonContent.class, dir);
        } catch (IOException e) {
            Timber.e(e, "Error while writing to %s", dir.getAbsolutePath());
        }
    }

    /**
     * Open the given Content in the built-in image viewer
     * @param context Context to use for the action
     * @param content Content to view
     * @param searchParams Current search parameters (so that the next/previous book feature
     *                     is faithful to the library screen's order)
     */
    public static void openHentoidViewer(@NonNull Context context, @NonNull Content content, Bundle searchParams) {
        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageFolder());

        ImageViewerActivityBundle.Builder builder = new ImageViewerActivityBundle.Builder();
        builder.setContentId(content.getId());
        if (searchParams != null) builder.setSearchParams(searchParams);

        Intent viewer = new Intent(context, ImageViewerActivity.class);
        viewer.putExtras(builder.getBundle());

        context.startActivity(viewer);
    }

    /**
     * Update the given Content's number of reads in both DB and JSON file
     * @param context Context to use for the action
     * @param dao DAO to use for the action
     * @param content Content to update
     */
    @WorkerThread
    public static void updateContentReads(@NonNull Context context, @Nonnull CollectionDAO dao, @NonNull Content content) {
        content.increaseReads().setLastReadDate(Instant.now().toEpochMilli());
        dao.insertContent(content);

        if (!content.getJsonUri().isEmpty()) updateJson(context, content);
        else createJson(content);
    }

    /**
     * Return the URI string to use to display the given Content's cover in the library screen
     * NB : Method is used by onBindViewHolder(), speed is key
     * @param content Content whose cover to retrieve
     * @return URI string where the cover should be retrieved
     */
    public static String getThumb(@NonNull final Content content) {
        String coverUrl = content.getCoverImageUrl();

        // If trying to access a non-downloaded book cover (e.g. viewing the download queue)
        if (content.getStorageFolder().equals("")) return coverUrl;

        String extension = HttpHelper.getExtensionFromUri(coverUrl);
        // Some URLs do not link the image itself (e.g Tsumino) => jpg by default
        // NB : ideal would be to get the content-type of the resource behind coverUrl, but that's too time-consuming
        if (extension.isEmpty() || extension.contains("/")) extension = "jpg";

        File f = new File(Preferences.getRootFolderName(), content.getStorageFolder() + File.separator + "thumb." + extension);
        return f.exists() ? f.getAbsolutePath() : coverUrl;
    }

    /**
     * Find the picture files for the given Content
     * NB1 : Pictures with non-supported formats are not included in the results
     * NB2 : Cover picture is not included in the results
     * @param content Content to retrieve picture files for
     * @return List of picture files
     */
    @Nullable
    public static File[] getPictureFilesFromContent(@NonNull final Content content) {
        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, content.getStorageFolder());

        Timber.d("Opening: %s from: %s", content.getTitle(), dir);
        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
            Timber.d("File not found!! Exiting method.");
            ToastUtil.toast(R.string.sd_access_error);
            return null;
        }

        return dir.listFiles(
                file -> (file.isFile()
                        && !file.getName().toLowerCase().startsWith("thumb")
                        && Helper.isImageExtensionSupported(FileHelper.getExtension(file.getName()))
                )
        );
    }

    /**
     * Remove the given Content from the disk and the DB
     *
     * @param content Content to be removed
     * @param dao     DAO to be used
     */
    @WorkerThread
    public static void removeContent(@NonNull Content content, @NonNull CollectionDAO dao) {
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteContent(content);

        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (!content.getStorageFolder().isEmpty()) {
            File dir = getContentDownloadDir(content);
            if (deleteQuietly(dir) || FileUtil.deleteWithSAF(dir)) {
                Timber.i("Directory %s removed.", dir);
            } else {
                Timber.w("Failed to delete directory: %s", dir); // TODO use exception to display feedback on screen
            }
        }
    }

    /**
     * Remove the given page from the disk and the DB
     *
     * @param image Page to be removed
     * @param dao   DAO to be used
     */
    @WorkerThread
    public static void removePage(@NonNull ImageFile image, @NonNull CollectionDAO dao, @NonNull final Context context) {
        // Remove from DB
        // NB : start with DB to have a LiveData feedback, because file removal can take much time
        dao.deleteImageFile(image);

        // Remove the page from disk
        if (image.getAbsolutePath() != null && !image.getAbsolutePath().isEmpty()) {
            File imgFile = new File(image.getAbsolutePath());
            FileHelper.removeFile(imgFile); // TODO use exception to display feedback on screen
        }

        // Update content JSON if it exists (i.e. if book is not queued)
        Content content = dao.selectContent(image.content.getTargetId());
        if (!content.getJsonUri().isEmpty()) updateJson(context, content);
    }

    /**
     * Create the download directory of the given content
     *
     * @param context Context
     * @param content Content for which the directory to create
     * @return Created directory
     */
    public static File createContentDownloadDir(@NonNull Context context, @NonNull final Content content) {
        String folderDir = formatDirPath(content);

        String settingDir = Preferences.getRootFolderName();
        if (settingDir.isEmpty()) {
            settingDir = getDefaultDir(context, folderDir).getAbsolutePath();
        }

        Timber.d("New book directory %s in %s", folderDir, settingDir);

        File file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    /**
     * Get the download directory of the given Content
     * @param content Content whose download directory to retrieve
     * @return Download directory of the given Content
     */
    public static File getContentDownloadDir(@NonNull final Content content) {
        String rootFolderName = Preferences.getRootFolderName();
        return new File(rootFolderName, content.getStorageFolder());
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Canonical download directory path of the given content, according to current user preferences
     */
    public static String formatDirPath(@NonNull final Content content) {
        String siteFolder = content.getSite().getFolder();
        String result = siteFolder;

        String title = content.getTitle().replaceAll(UNAUTHORIZED_CHARS, "_");
        String author = content.getAuthor().toLowerCase().replaceAll(UNAUTHORIZED_CHARS, "_");

        switch (Preferences.getFolderNameFormat()) {
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_ID:
                result += title;
                break;
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID:
                result += author + " - " + title;
                break;
            case Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_AUTH_ID:
                result += title + " - " + author;
                break;
        }
        result += " - ";

        // Unique content ID
        String suffix = formatBookId(content);

        // Truncate folder dir to something manageable for Windows
        // If we are to assume NTFS and Windows, then the fully qualified file, with it's drivename, path, filename, and extension, altogether is limited to 260 characters.
        int truncLength = Preferences.getFolderTruncationNbChars();
        int titleLength = result.length() - siteFolder.length();
        if ((truncLength > 0) && ((titleLength + suffix.length()) > truncLength))
            result = result.substring(0, siteFolder.length() + truncLength - suffix.length() - 1);

        result += suffix;

        return result;
    }

    /**
     * Format the Content ID for folder naming purposes
     * @param content Content whose ID to format
     * @return Formatted Content ID
     */
    @SuppressWarnings("squid:S2676") // Math.abs is used for formatting purposes only
    private static String formatBookId(@NonNull final Content content) {
        String id = content.getUniqueSiteId();
        // For certain sources (8muses, fakku), unique IDs are strings that may be very long
        // => shorten them by using their hashCode
        if (id.length() > 10) id = Helper.formatIntAsStr(Math.abs(id.hashCode()), 10);
        return "[" + id + "]";
    }

    /**
     * Return the given site's download directory. Create it if it doesn't exist.
     * @param context Context to use for the action
     * @param site Site to get the download directory for
     * @return Download directory of the given Site
     */
    public static File getOrCreateSiteDownloadDir(@NonNull final Context context, @NonNull final Site site) {
        File file;
        String settingDir = Preferences.getRootFolderName();
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    /**
     * Open the "share with..." Android dialog for the given Content
     * @param context Context to use for the action
     * @param item Content to share
     */
    public static void shareContent(@NonNull final Context context, @NonNull final Content item) {
        String url = item.getGalleryUrl();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, url);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }
}
