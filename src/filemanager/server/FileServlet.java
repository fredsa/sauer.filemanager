package filemanager.server;

import com.allen_sauer.gwt.log.client.Log;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import filemanager.shared.FileManagerConstants;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class FileServlet extends HttpServlet {

  /**
   * Datastore kind representing raw file assets.
   */
  private static final String KIND_ASSET = "Asset";

  /**
   * URL parameter for the blob upload count.
   */
  private static final String PARAMETER_BLOB_UPLOAD_COUNT = "blob-upload-count";

  /**
   * URL parameter for storing the BlobKey string.
   */
  private static final String PARAMETER_BLOBKEY_STRING = "blob-key-string";

  /**
   * Datastore property for storing the BlobKey.
   */
  private static final String PROPERTY_BLOBKEY = "blobkey";

  /**
   * URI used by task queue to guarantee deletion of orphaned blobs.
   */
  private static final String URI_DELETE_BLOB = "/delete-blob";

  /**
   * URI which we can redirect to post upload.
   */
  private static final String URI_STORE_BLOB_INFO = "/store-blob-info";

  /**
   * URI which we redirect to upon successful upload.
   */
  private static final String URI_UPLOAD_COMPLETE = "/upload-complete";

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
    BlobstoreService bs = BlobstoreServiceFactory.getBlobstoreService();
    String uri = req.getRequestURI();

    /**
     * Task queue based deletion of orphaned blobs.
     */
    if (uri.equals(URI_DELETE_BLOB)) {
      String blobKeyString = req.getParameter(PARAMETER_BLOBKEY_STRING);
      Log.debug("Delete orphaned blob with key: " + blobKeyString);
      bs.delete(new BlobKey(blobKeyString));
      return;
    }

    Map<String, BlobKey> blobs;
    try {
      /**
       * Ensure that this request is a result of POST to a blobstore upload URL.
       */
      try {
        blobs = bs.getUploadedBlobs(req);
      } catch (IllegalStateException ignore) {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
            "File uploads must POST to a blobstore service provided upload URL");
        return;
      }

      /**
       * Ensure that at least one file was uploaded.
       */
      if (blobs.isEmpty()) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing one or more uploaded files");
        return;
      }

      /**
       * We now have a valid upload request with at least one file available to
       * us in blobstore.
       */
      for (Entry<String, BlobKey> entry : blobs.entrySet()) {
        BlobInfo info = new BlobInfoFactory().loadBlobInfo(entry.getValue());
        Log.info("Got blob: blobkey=" + info.getBlobKey() + ", content type="
            + info.getContentType() + ", filename=" + info.getFilename() + ", size="
            + info.getSize() + ", creation=" + info.getCreation());

        // Persist the asset information to the datastore
        persistAsset(info);
      }
      // We are required to send a redirect in response to a blobstore upload
      resp.sendRedirect(URI_UPLOAD_COMPLETE + "?" + PARAMETER_BLOB_UPLOAD_COUNT + "="
          + blobs.size());
      return;
    } catch (IOException ex) {
      throw new ServletException(ex);
    }
  }

  /**
   * Serve assets from blobstore.
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String uri = req.getRequestURI();
    String filename = lastPathComponent(uri);

    /**
     * User has requested an upload form.
     */
    if (uri.equals("/")) {
      BlobstoreService bs = BlobstoreServiceFactory.getBlobstoreService();
      resp.setContentType("text/html");

      resp.getWriter().println("<html>");
      resp.getWriter().println("<body>");
      resp.getWriter().println(
          "<form action='" + bs.createUploadUrl("/upload")
              + "' method='post' enctype='multipart/form-data'>");
      resp.getWriter().println("<input type='file' name='myFile'>");
      resp.getWriter().println("<input type='submit' value='Submit'>");
      resp.getWriter().println("</form>");
      resp.getWriter().println("</body>");
      resp.getWriter().println("</html>");
      return;
    }

    /**
     * User has requested a single-use blob upload URL.
     */
    if (uri.endsWith(FileManagerConstants.REQUEST_BLOBSTORE_UPLOAD_URL)) {
      Log.info("Creating single use upload URL...");
      BlobstoreService bs = BlobstoreServiceFactory.getBlobstoreService();
      String url = bs.createUploadUrl(URI_STORE_BLOB_INFO);
      Log.info("- Upload URL: " + url);
      resp.getWriter().println(url);
      return;
    }

    /**
     * We serve a success page when content has been successfully uploaded.
     */
    if (uri.equals(URI_UPLOAD_COMPLETE)) {
      String count = req.getParameter(PARAMETER_BLOB_UPLOAD_COUNT);
      resp.setContentType("text/plain");
      resp.getWriter().println("- Assets successfullly uploaded: " + count);
      return;
    }

    /**
     * We have a valid request for an asset.
     */
    Key assetEntityKey = KeyFactory.createKey(KIND_ASSET, filename);
    BlobKey blobkey = getBlobKey(assetEntityKey);
    if (blobkey == null) {
      Log.info("Asset not found: " + filename);
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    BlobstoreService bs = BlobstoreServiceFactory.getBlobstoreService();
    Log.info("Serving asset '" + filename + "' using blob key: " + blobkey);
    bs.serve(blobkey, resp);
  }

  /**
   * Get blob key for an existing asset.
   *
   * @param assetEntityKey the entity key for the asset
   * @return an existing blob key or null of the asset does not yet exist
   */
  private BlobKey getBlobKey(Key assetEntityKey) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    MemcacheService ms = MemcacheServiceFactory.getMemcacheService();

    /**
     * Lookup blobkey in memcache.
     */
    BlobKey blobkey = (BlobKey) ms.get(assetEntityKey);
    if (blobkey != null) {
      return blobkey;
    }

    /**
     * Lookup blobkey in datastore.
     */
    try {
      Entity entity = ds.get(assetEntityKey);
      blobkey = (BlobKey) entity.getProperty(PROPERTY_BLOBKEY);
      /**
       * Store blobkey in memcache.
       */
      ms.put(assetEntityKey, blobkey);
      return blobkey;
    } catch (EntityNotFoundException ignore) {
      return null;
    }
  }

  /**
   * Determine the filename based on the last path component of a request URI.
   *
   * @param uri the URI for a requested resource
   * @return the last path component in the provided URI
   */
  private String lastPathComponent(String uri) {
    int pos = uri.lastIndexOf('/');
    return pos == -1 ? uri : uri.substring(pos + 1);
  }

  /**
   * Persist a new asset entity to the datastore and ensure existing blob is not
   * orphaned.
   *
   * @param blobInfo a new blob info
   */
  private void persistAsset(BlobInfo blobInfo) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    MemcacheService ms = MemcacheServiceFactory.getMemcacheService();

    String filename = blobInfo.getFilename();
    Log.info("Persisting asset: " + filename);

    /**
     * Construct a new datastore entity, which we will persist.
     */
    Key assetEntityKey = KeyFactory.createKey(KIND_ASSET, filename);
    Entity entity = new Entity(assetEntityKey);
    BlobKey newBlobKey = blobInfo.getBlobKey();
    entity.setProperty(PROPERTY_BLOBKEY, newBlobKey);

    /**
     * Check whether there's an existing entity which will be orphaned.
     */
    BlobKey oldBlobKey = getBlobKey(assetEntityKey);

    /**
     * Update datastore.
     */
    if (oldBlobKey == null) {
      /**
       * There is no existing blob which would be orphaned, so simply put()
       * asset.
       */
      Log.debug("- Write new asset entity for '" + filename + "'");
      ds.put(entity);
    } else {
      /**
       * Use of transactional task ensures that the task to delete the orphaned
       * blob is created if, and only if, the datastore transaction succeeds.
       */
      Log.debug("- Write new asset entity for '" + filename
          + "' and (transactionally) request deletion of orphaned blob key: " + oldBlobKey);
      Queue queue = QueueFactory.getDefaultQueue();
      Transaction txn = ds.beginTransaction();
      ds.put(entity);
      queue.add(TaskOptions.Builder.withUrl(URI_DELETE_BLOB).param(PARAMETER_BLOBKEY_STRING,
          oldBlobKey.getKeyString()));
      txn.commit();
    }

    /**
     * Update memcache.
     */
    Log.debug("- Update memcache with new blob key: " + newBlobKey);
    ms.put(assetEntityKey, newBlobKey);
  }
}
