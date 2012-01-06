package filemanager.client;

import com.allen_sauer.gwt.log.client.Log;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.FormElement;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

import filemanager.client.WebkitFileUpload.File;
import filemanager.shared.FileManagerConstants;

public class UploadFormWidget extends Composite {

  interface UploadFormWidgetUiBinder extends UiBinder<Widget, UploadFormWidget> {
  }

  private static UploadFormWidgetUiBinder uiBinder = GWT.create(UploadFormWidgetUiBinder.class);

  @UiField
  WebkitFileUpload fileUpload;

  @UiField
  FormElement form;

  @UiField
  HTMLPanel loggerContainer;

  @UiField
  Button selectButton;

  @UiField
  HTML statusHTML;

  private String downloadUrl;

  private int loadingCount;

  public UploadFormWidget() {
    initWidget(uiBinder.createAndBindUi(this));
    fileUpload.setDirectory(false);
    fileUpload.setMultiple(true);
    try {
      getDownloadUrl();
    } catch (RequestException e) {
      Log.fatal("Failed to get download URL", e);
    }
  }

  @UiHandler("fileUpload")
  void onChangeFileUpload(ChangeEvent evt) {
    selectButton.setEnabled(false);
    JsArray<File> files = fileUpload.getFiles();
    try {
      processUpload1(files);
    } catch (RequestException e) {
      Log.fatal("Request Exception", e);
    }
  }

  @UiHandler("selectButton")
  void onClickSelectButton(ClickEvent evt) {
    InputElement fileUploadElement = fileUpload.getElement().cast();
    fileUploadElement.click();
  }

  private void getDownloadUrl() throws RequestException {
    statusHTML.setHTML("Requesting download URL...");

    RequestBuilder builder = new RequestBuilder(RequestBuilder.GET,
        FileManagerConstants.REQUEST_BLOBSTORE_UPLOAD_URL);
    builder.sendRequest("", new RequestCallback() {

      @Override
      public void onError(Request request, Throwable e) {
        statusHTML.setHTML(new SafeHtmlBuilder().appendHtmlConstant(
            "Error requesting download URL: ").appendEscaped(e.toString()).toSafeHtml());
        Log.fatal("Eror requesting download URL", e);
      }

      @Override
      public void onResponseReceived(Request request, Response response) {
        String text = response.getText();
        int statusCode = response.getStatusCode();
        if (statusCode != Response.SC_OK) {
          Log.error("Unexpected status code: " + statusCode);
        } else {
          downloadUrl = text;
        }
        statusHTML.setHTML(new SafeHtmlBuilder().appendHtmlConstant("Received download URL: ").appendEscaped(
            text).toSafeHtml());
        Log.debug("Received download URL:");
        Log.debug("- Status Code: " + statusCode);
        Log.debug("- Status Text: " + response.getStatusText());
        Log.debug("- Headers: " + response.getHeadersAsString());
        Log.debug("- Text: " + text);
        form.setAction(downloadUrl);
        form.setMethod("POST");
        form.setTarget("_self");
        form.setEnctype("multipart/form-data");
      }
    });
  }

  private void processUpload1(final JsArray<File> files) throws RequestException {
    loadingCount = files.length();
    final FileReader[] fileReaders = new FileReader[files.length()];
    for (int i = 0; i < files.length(); i++) {
      final File file = files.get(i);
      final FileReader reader = FileReader.createIfSupported();
      fileReaders[i] = reader;
      reader.readAsBinaryString(file);
      Log.debug(file.getFileName());
      Log.debug(" " + file.getType());
      Scheduler.get().scheduleFixedPeriod(new RepeatingCommand() {
        public boolean execute() {
          int readyState = reader.getReadyState();
          Log.debug("File " + file.getFileName() + " readyState = " + readyState);
          if (readyState == FileReader.DONE) {
            Log.debug("Loading Count " + loadingCount-- + " -> " + loadingCount);
            if (loadingCount == 0) {
              processUpload2(files, fileReaders);
            }
            return false;
          }
          return true;
        }
      }, 100);
    }
  }

  private void processUpload2(JsArray<File> files, FileReader[] fileReaders) {
    if (downloadUrl == null) {
      Log.error("Sorry, download URL is not yet available");
      return;
    }

    String boundary = "MimeBoundary" + System.currentTimeMillis() + "UploadFormWidget";
    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, downloadUrl);
    rb.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

    String data = "";

    for (int i = 0; i < files.length(); i++) {
      File file = files.get(i);
      Log.debug(file.getFileName());
      Log.debug(" " + file.getType());

      data += "--" + boundary + "\r\n";
      data += "Content-Disposition: form-data; name=\"file" + i + "\"; filename=\""
          + file.getFileName() + "\"\r\n";
      data += "Content-Type: " + file.getType() + "\r\n";
      data += "\r\n";
      data += "\r\n";
      data += fileReaders[i].getResult();
      data += "\r\n";
      data += "--" + boundary + "\r\n";
    }

    data += "--" + boundary + "--\r\n";
    try {
      rb.sendRequest(data, new RequestCallback() {

        @Override
        public void onError(Request request, Throwable e) {
          Log.fatal("EXCEPTION!", e);
        }

        @Override
        public void onResponseReceived(Request request, Response response) {
          Log.debug("RESPONSE RECEIVED!\n" + response.getStatusCode() + " "
              + response.getStatusText() + "\n" + response.getText());
        }
      });
    } catch (RequestException e) {
      Log.fatal("Request exception", e);
    }
    //    form.submit();
  }
}