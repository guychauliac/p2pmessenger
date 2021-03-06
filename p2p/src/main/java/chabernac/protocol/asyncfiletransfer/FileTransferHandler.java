package chabernac.protocol.asyncfiletransfer;

import java.io.File;


public class FileTransferHandler {
  private final String myTransferId;
  private final iTransferController myTransferController;

  public FileTransferHandler(String anTransferId, iTransferController anTransferController) {
    super();
    myTransferId = anTransferId;
    myTransferController = anTransferController;
  }

  public String getTransferId(){
    return myTransferId;
  }

  public void cancel() throws AsyncFileTransferException{
    myTransferController.cancel(myTransferId);
  }

  public void pause() throws AsyncFileTransferException{
    myTransferController.pause(myTransferId);
  }

  public void resume() throws AsyncFileTransferException{
    myTransferController.resume(myTransferId);
  }
  
  public void waitUntillDone() throws AsyncFileTransferException{
    myTransferController.waitUntillDone( myTransferId);
  } 

  public FileTransferState getState(){
    return myTransferController.getState(myTransferId);
  }
  
  public File getFile() throws AsyncFileTransferException{
    return myTransferController.getFile(myTransferId);
  }
  
  public void addFileTransferListener(iFileTransferListener aListener) throws AsyncFileTransferException{
    myTransferController.addFileTransferListener(myTransferId, aListener);
  }
  
  public String toString(){
    try {
      return getFile().getName() + " " + getState().toString();
    } catch ( AsyncFileTransferException e ) {
      return "Exception occured in to string of FileTransferHandler";
    }
  }
}
