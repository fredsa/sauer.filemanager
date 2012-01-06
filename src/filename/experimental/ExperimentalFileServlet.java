package filename.experimental;

import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.common.io.ByteStreams;

import filemanager.shared.FileManagerConstants;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ExperimentalFileServlet extends HttpServlet {


  /**
   * Datastore kind representing raw file assets.
   */
  private static final String KIND_ASSET = "Asset";

  /**
   * Datastore property for storing file contents.
   */

  private static final String PROPERTY_CONTENT = "content";

  /**
   * Datastore property for storing file MIME Type.
   */
  private static final String PROPERTY_MIME_TYPE = "mimeType";

  private static final Logger LOGGER = Logger.getLogger(ExperimentalFileServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String uri = req.getRequestURI();
    LOGGER.log(Level.INFO, "doGet(" + uri + ")");

    if (uri.equals("/ok")) {
      LOGGER.log(Level.INFO, "ok");
      return;
    }

    if (uri.endsWith(FileManagerConstants.REQUEST_BLOBSTORE_UPLOAD_URL)) {
      LOGGER.log(Level.INFO, "request upload url");
      BlobstoreService bs = BlobstoreServiceFactory.getBlobstoreService();
      String url = bs.createUploadUrl("/ok");
      resp.getWriter().println(url);
      return;
    }

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    try {
      Entity entity = ds.get(KeyFactory.createKey(KIND_ASSET, uri));
      Blob data = (Blob) entity.getProperty(PROPERTY_CONTENT);
      String mimeType = (String) entity.getProperty(PROPERTY_MIME_TYPE);
      resp.setContentType(mimeType);
      // resp.setHeader("Content-Disposition", "inline");
      resp.getOutputStream().write(data.getBytes());
    } catch (EntityNotFoundException e) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
  }


  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String uri = req.getRequestURI();
    LOGGER.log(Level.INFO, "doPost(" + uri + ")");
    String mimeType = req.getParameter("mimeType");
    LOGGER.log(Level.INFO, "- MIME Type: " + mimeType);
    byte[] data = ByteStreams.toByteArray(req.getInputStream());
    LOGGER.log(Level.INFO, "- length: " + data.length);
    Blob blob = new Blob(data);
    Entity entity = new Entity(KIND_ASSET, uri);
    entity.setProperty(PROPERTY_MIME_TYPE, mimeType);
    entity.setProperty(PROPERTY_CONTENT, blob);
    DatastoreServiceFactory.getDatastoreService().put(entity);
  }
}
