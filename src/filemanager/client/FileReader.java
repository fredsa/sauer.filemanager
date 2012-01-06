package filemanager.client;

import com.google.gwt.core.client.JavaScriptObject;

import filemanager.client.WebkitFileUpload.Blob;

public final class FileReader extends JavaScriptObject {

  public interface Load {
    void onLoad();
  }

  public static final int DONE = 2;
  public static final int EMPTY = 0;
  public static final int LOADING = 1;

  //  // event handler attributes
  //  attribute Function onloadstart;
  //  attribute Function onprogress;
  //  attribute Function onload;
  //  attribute Function onabort;
  //  attribute Function onerror;
  //  attribute Function onloadend;

  /**
   * TODO: return null if not supported
   */
  public static native FileReader createIfSupported() /*-{
    var reader = new FileReader();
    reader.onload = function(evt) {
      // alert("load");
    }
    reader.onerror = function(evt) {
      alert("err");
    }
    return reader;
  }-*/;

  protected FileReader() {
  }

  public native void abort()/*-{
    abort();
  }-*/;

  /**
   * TODO Change return type to FileError
   */
  public native JavaScriptObject getError()/*-{
    return this.error;
  }-*/;

  public native int getReadyState()/*-{
    return this.readyState;
  }-*/;

  public native Object getResult()/*-{
    return this.result;
  }-*/;

  public native void readAsArrayBuffer(Blob blob)/*-{
    this.readAsArrayBuffer(blob);
  }-*/;

  public native void readAsBinaryString(Blob blob)/*-{
    this.readAsBinaryString(blob);
  }-*/;

  public native void readAsDataURL(Blob blob)/*-{
    this.readAsDataURL(blob);
  }-*/;

  public native void readAsText(Blob blob)/*-{
    this.readAsText(blob);
  }-*/;

  public native void readAsText(Blob blob, String encoding)/*-{
    this.readAsText(blob, ecoding);
  }-*/;

}
