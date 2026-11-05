package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ShinigamiDownloadStateTest {
    @Test public void metadataOrHtmlIsNotAnOfflineChapter() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        File dir=local.chapterDir("not-image"); dir.mkdirs();
        try (FileOutputStream out=new FileOutputStream(new File(dir,"0.img"))) {
            out.write("<html>200 but error</html>".getBytes());
        }
        assertNull(local.pageFile("not-image",0));
        assertEquals("Belum diunduh", local.storageLabel("not-image",1));
        assertEquals("Belum diunduh", local.storageLabel("metadata-only",18));
    }
    void png(ShinigamiDownloader local,String id,int index) throws Exception {
        local.chapterDir(id).mkdirs();
        try (FileOutputStream out=new FileOutputStream(new File(local.chapterDir(id),index+".img"))) {
            Bitmap.createBitmap(50,50,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,out);
        }
    }
    @Test public void requiresEveryExpectedPageNotOnlyDirectory() throws Exception {
        ShinigamiDownloader local=new ShinigamiDownloader(ApplicationProvider.getApplicationContext());
        png(local,"complete",0);
        assertFalse(local.isDownloaded("complete",2));
        assertEquals("Belum diunduh",local.storageLabel("complete",2));
        png(local,"complete",1);
        assertEquals("Tersimpan offline",local.storageLabel("complete",2));
        assertFalse(local.isDownloaded("complete",0));
    }
    @Test public void truncatedPngWithReadableDimensionsIsNotSaved() throws Exception {
        ShinigamiDownloader local=new ShinigamiDownloader(ApplicationProvider.getApplicationContext());
        png(local,"truncated",0);
        File file=new File(local.chapterDir("truncated"),"0.img");
        try(java.io.RandomAccessFile out=new java.io.RandomAccessFile(file,"rw")) {
            out.setLength(file.length()-12);
        }
        assertNull(local.pageFile("truncated",0));
        assertFalse(local.isDownloaded("truncated",1));
    }
    @Test public void offlineLibraryRequiresKnownCompletePageCount() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        ShinigamiDownloader local=new ShinigamiDownloader(context);
        png(local,"library-unknown",0);
        assertFalse(local.downloadedChapters().contains("library-unknown"));
        new ShinigamiStore(context).setChapterPages("library-valid",java.util.Arrays.asList("https://storage.shngm.id/a.webp"));
        png(local,"library-valid",0);
        assertTrue(local.downloadedChapters().contains("library-valid"));
    }
    @Test public void runningDownloadAndReadingProgressAreSeparate() {
        Context context=ApplicationProvider.getApplicationContext();
        ShinigamiDownloader local=new ShinigamiDownloader(context);
        new ShinigamiStore(context).setProgress("running",100);
        assertEquals("Belum diunduh",local.storageLabel("running",3));
        local.setRunning("running",true);
        assertEquals("Mengunduh...",local.storageLabel("running",3));
        local.setRunning("running",false);
        assertEquals("Belum diunduh",local.storageLabel("running",3));
    }
}
