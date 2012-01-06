package filename.experimental;
import com.google.appengine.repackaged.com.google.common.io.ByteStreams;

import filemanager.shared.FileManagerConstants;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Logger;

public class ExperimentalUploader {
  private static final String ENCODING = "UTF-8";

  private static final Logger LOGGER = Logger.getLogger(ExperimentalUploader.class.getName());

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      showUsage();
      return;
    }
    String baseUrl = args[0];
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    LOGGER.info("Using base URL: " + baseUrl);

    String filename = args[1];
    LOGGER.info("Using filename: " + filename);


    String uploadUrl = getUploadUrl(baseUrl);
    LOGGER.info("Using upload URL: " + uploadUrl);

    upload(baseUrl + uploadUrl, filename);
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


  private static void upload(String baseUrl, String filename) throws IOException {
    LOGGER.info("Posting file: " + filename);
    File file = new File(filename);
    if (!file.canRead()) {
      LOGGER.warning("- ERROR: File is not readable: " + filename);
      return;
    }

    FileInputStream fileStream = new FileInputStream(file);

    // Determine MIME Type
    String mimeType = URLConnection.guessContentTypeFromStream(new BufferedInputStream(fileStream));
    LOGGER.info("- MIME Type: " + mimeType);

    URL url =
        new URL(baseUrl + "/" + file.getName() + "?mimeType="
            + URLEncoder.encode(mimeType, ENCODING));
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    long length = ByteStreams.copy(fileStream, connection.getOutputStream());
    LOGGER.info("- Length: " + length);


    int responseCode = connection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      LOGGER.info("- ERROR: Received response code " + responseCode + " while reading file: "
          + filename);
      return;
    }

    LOGGER.info("- SUCCESS");
  }
}
