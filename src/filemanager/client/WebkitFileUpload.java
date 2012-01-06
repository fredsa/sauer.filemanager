package filemanager.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FileUpload;

public class WebkitFileUpload extends FileUpload {

  public static class Blob extends JavaScriptObject {
    protected Blob() {
    }

    final native int getSize() /*-{
      return this.size;
    }-*/;

    final native String getType() /*-{
      return this.type;
    }-*/;

    final native Blob slice(int start, int length, String contentType) /*-{
      return this.slice(start, length, contentType);
    }-*/;
  }

  public final static class File extends Blob {
    protected File() {
    }

    native String getFileName() /*-{
      return this.fileName;
    }-*/;

    native int getFileSize() /*-{
      return this.fileSize;
    }-*/;

    native String getlastModifiedDate() /*-{
      this.lastModifiedDate;
    }-*/;

    native String getName() /*-{
      return this.name;
    }-*/;

    native String getWebkitRelativePath() /*-{
      return this.webkitRelativePath;
    }-*/;
  }

  public WebkitFileUpload() {
    setDirectory(true);
  }

  JsArray<File> getFiles() {
    return getFiles(getElement());
  }

  void setDirectory(boolean directory) {
    getElement().setPropertyBoolean("webkitdirectory", directory);
  }

  void setMultiple(boolean multiple) {
    getElement().setPropertyBoolean("multiple", multiple);
  }

  private native JsArray<File> getFiles(Element e) /*-{
    return e.files;
  }-*/;

}
