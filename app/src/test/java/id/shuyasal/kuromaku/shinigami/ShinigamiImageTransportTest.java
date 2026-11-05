package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ShinigamiImageTransportTest {
    static final String URL="https://storage.shngm.id/test.webp";
    static final byte[] WEBP="RIFF1234WEBPVP8 payload".getBytes();
    static class Result implements DataFetcher.DataCallback<InputStream> {
        InputStream input; Exception error;
        public void onDataReady(InputStream value) { input=value; }
        public void onLoadFailed(Exception failure) { error=failure; }
    }
    DataFetcher<InputStream> fetch(OkHttpClient client) {
        ModelLoader<ShinigamiImageRequest,InputStream> loader=new ShinigamiImageRequest.Factory().build(null);
        return loader.buildLoadData(new ShinigamiImageRequest(URL,"https://shinigami.asia",client),320,4096,new Options()).fetcher;
    }
    Response response(Request request,int status,String mime,byte[] bytes) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("test")
                .header("Content-Type",mime).body(ResponseBody.create(MediaType.parse(mime),bytes)).build();
    }
    @Test public void slowResponseKeepsStreamAndUsesLegitimateReferer() throws Exception {
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            assertEquals("https://shinigami.asia/",chain.request().header("Referer"));
            assertNull(chain.request().header("Cookie"));
            try { Thread.sleep(40); } catch(InterruptedException e) { throw new IOException(e); }
            return response(chain.request(),200,"image/webp",WEBP);
        }).build();
        DataFetcher<InputStream> fetcher=fetch(client); Result result=new Result();
        fetcher.loadData(Priority.NORMAL,result);
        assertNull(result.error); assertArrayEquals(WEBP,result.input.readAllBytes()); fetcher.cleanup();
    }
    @Test public void disconnectedConnectionReportsFailureWithoutBlankSuccess() {
        AtomicInteger calls=new AtomicInteger();
        DataFetcher<InputStream> fetcher=fetch(new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet(); throw new IOException("Disconnected");
        }).retryOnConnectionFailure(false).build());
        Result result=new Result(); fetcher.loadData(Priority.NORMAL,result);
        assertNotNull(result.error); assertNull(result.input); assertEquals(1,calls.get()); fetcher.cleanup();
    }
    @Test public void forbiddenResponseIsNotRetried() {
        AtomicInteger calls=new AtomicInteger();
        DataFetcher<InputStream> fetcher=fetch(new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet(); return response(chain.request(),403,"text/html","challenge-cookie-secret".getBytes());
        }).build());
        Result result=new Result(); fetcher.loadData(Priority.NORMAL,result);
        assertNotNull(result.error); assertTrue(result.error.getMessage().contains("403"));
        assertFalse(result.error.getMessage().contains("secret")); assertEquals(1,calls.get()); fetcher.cleanup();
    }
    @Test public void redirectsCannotLeaveValidatedHttpsHosts() {
        AtomicInteger calls=new AtomicInteger();
        DataFetcher<InputStream> fetcher=fetch(new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet(); return response(chain.request(),302,"text/html",new byte[0])
                    .newBuilder().header("Location","http://evil.example/private").build();
        }).followRedirects(false).build());
        Result result=new Result(); fetcher.loadData(Priority.NORMAL,result);
        assertNotNull(result.error); assertTrue(result.error.getMessage().contains("Redirect"));
        assertEquals(1,calls.get()); fetcher.cleanup();
    }
    @Test public void cancellationDoesNotStartARequest() {
        AtomicInteger calls=new AtomicInteger();
        DataFetcher<InputStream> fetcher=fetch(new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet(); return response(chain.request(),200,"image/webp",WEBP);
        }).build());
        fetcher.cancel(); Result result=new Result(); fetcher.loadData(Priority.NORMAL,result);
        assertEquals(0,calls.get()); assertNull(result.input); fetcher.cleanup();
    }
}
