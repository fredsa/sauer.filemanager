package filemanager;

import com.allen_sauer.gwt.log.client.Log;

import filemanager.shared.FileManagerConstants;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.http.HttpServletResponse;

public class Uploader {
  /**
   *
   */
  private static final String MIME_TYPE_FALLBACK = "application/octet-stream";
  /**
   *
   */
  private static final int MAX_RETRIES = 3;

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      showUsage();
      return;
    }
    String baseUrl = args[0];
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    Log.debug("Will submit to base URL: " + baseUrl);

    for (int i = 1; i < args.length; i++) {
      String filename = args[i];
      Log.debug("Filename: " + filename);

      File file = new File(filename);
      if (!file.canRead()) {
        Log.error("- File is not readable: " + filename);
        continue;
      }

      int count = 0;
      boolean success = false;
      while (!success && ++count < MAX_RETRIES) {
        if (count > 1) {
          Log.info("- ATTEMPT " + count + " of " + MAX_RETRIES + "....");
        }
        success = doUpload(baseUrl, file);
      }
      if (!success) {
        Log.fatal("ABORTING");
        System.exit(1);
      }
    }
  }


  private static boolean doUpload(String baseUrl, File file) throws IOException {
    HttpClient httpclient = new DefaultHttpClient();
    try {

      String mimeType = guessMimeType(file);
      Log.info("- MIME Type: " + mimeType);

      String uploadUrl = getUploadUrl(baseUrl);

      if (!uploadUrl.startsWith("http")) {
        // fix for devappserver lacking scheme/host/port
        uploadUrl = baseUrl + uploadUrl;
      }

      Log.debug("- Blobstore upload URL: " + uploadUrl);

      HttpPost httppost = new HttpPost(uploadUrl);
      FileBody fileBody = new FileBody(file, mimeType);

      MultipartEntity reqEntity = new MultipartEntity();
      reqEntity.addPart("file1", fileBody);
      // reqEntity.addPart("mimeType", new StringBody(mimeType));

      httppost.setEntity(reqEntity);

      Log.debug("- " + httppost.getRequestLine());
      HttpResponse response = httpclient.execute(httppost);
      HttpEntity resEntity = response.getEntity();

      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != HttpServletResponse.SC_OK) {
        Log.error("- Upload failed due to server response: " + response.getStatusLine());
        return false;
      }
      Log.info("- Done");
    } finally {
      try {
        httpclient.getConnectionManager().shutdown();
      } catch (Exception ignore) {
      }
    }
    return true;
  }


  private static String guessMimeType(File file) throws FileNotFoundException, IOException {
    String mimeType;
    FileInputStream fileStream = new FileInputStream(file);
    mimeType = URLConnection.guessContentTypeFromStream(new BufferedInputStream(fileStream));
    if (mimeType == null) {
      mimeType = URLConnection.guessContentTypeFromName(file.getName());
    }
    if (mimeType == null) {
      Log.warn("- Unable to determine MIME Type from stream or filename; will proceed with "
          + MIME_TYPE_FALLBACK);
      mimeType = MIME_TYPE_FALLBACK;
    }
    return mimeType;
  }

  private static String getUploadUrl(String baseUrl) throws IOException {
    URL url = new URL(baseUrl + FileManagerConstants.REQUEST_BLOBSTORE_UPLOAD_URL);
    BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
    String uploadUrl = reader.readLine();
    return uploadUrl;
  }

  private static void showUsage() {
    System.err.println("java -jar uploader.jar <url> <file1> [file2] ...");
  }
}
