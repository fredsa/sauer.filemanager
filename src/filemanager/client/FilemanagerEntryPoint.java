package filemanager.client;

import com.allen_sauer.gwt.log.client.Log;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.ui.RootLayoutPanel;

public class FilemanagerEntryPoint implements EntryPoint {
  @Override
  public void onModuleLoad() {
    Log.setUncaughtExceptionHandler();

    /*
     * Use a deferred command so that the Uncaight Exception Handler
     * is in effect before onModuleLoad2() is invoked
     */
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        onModuleLoad2();
      }
    });
  }

  private void onModuleLoad2() {
    RootLayoutPanel.get().add(new UploadFormWidget());
  }
}
