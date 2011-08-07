package chabernac.protocol.asyncfiletransfer;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

import org.apache.log4j.Logger;

import chabernac.tools.SystemTools;

public class FileTransferPopup extends JPopupMenu {
  private static final long serialVersionUID = -1777097383738597120L;
  private static final Logger LOGGER = Logger.getLogger(FileTransferPopup.class);
  private final FileTransferHandler myFileTransferHandler;
  private FileTransferState.State myLastState = null;

  public FileTransferPopup(FileTransferHandler aHandler) throws AsyncFileTransferException{
    myFileTransferHandler = aHandler;
    buildMenu();
    addListeners();
  }

  private void addListeners() throws AsyncFileTransferException{
    myFileTransferHandler.addFileTransferListener(new FileTransferListener());
  }

  private void buildMenu(){
    removeAll();
    FileTransferState theState = myFileTransferHandler.getState();
    if(myLastState != theState.getState()){
      switch(theState.getState()){
      case DONE:{
        add(new OpenAction());
        add(new ExploreAction());
        add(new RemoveAction());
        break;
      }
      case FAILED:
      case NOT_STARTED:
      case REFUSED:
      case PAUSED:{
        add(new StartAction());
        add(new RemoveAction());
        break;
      }
      case RUNNING:{
        add(new StopAction());
        add(new RemoveAction());
        break;
      }
      }
    }
    myLastState = theState.getState();
  }

  private class FileTransferListener implements iFileTransferListener {
    @Override
    public void transferStateChanged() {
      buildMenu();
    }
  }

  private class OpenAction extends AbstractAction {
    private static final long serialVersionUID = -7356442420934284553L;

    public OpenAction(){
      putValue( Action.NAME, "Openen" );
    }

    @Override
    public void actionPerformed(ActionEvent anArg0) {
      try {
        SystemTools.openFile(myFileTransferHandler.getFile());
      } catch (Exception e) {
        LOGGER.error("An error occured while opening file", e);
      }
    }
  }

  private class ExploreAction extends AbstractAction {
    private static final long serialVersionUID = -7356442420934284553L;

    public ExploreAction(){
      putValue( Action.NAME, "Folder openen" );
    }

    @Override
    public void actionPerformed(ActionEvent anArg0) {
      try {
        SystemTools.openDirectory(myFileTransferHandler.getFile());
      } catch (Exception e) {
        LOGGER.error("An error occured while opening directory", e);
      }
    }
  }

  private class RemoveAction extends AbstractAction {
    private static final long serialVersionUID = -7356442420934284553L;

    public RemoveAction(){
      putValue( Action.NAME, "Verwijderen" );
    }

    @Override
    public void actionPerformed(ActionEvent anArg0) {
      try {
        myFileTransferHandler.cancel();
      } catch (Exception e) {
        LOGGER.error("An error occured while cancelling download", e);
      }
    }
  }

  private class StartAction extends AbstractAction {
    private static final long serialVersionUID = -7356442420934284553L;

    public StartAction(){
      putValue( Action.NAME, "Download starten" );
    }

    @Override
    public void actionPerformed(ActionEvent anArg0) {
      try {
        myFileTransferHandler.resume();
      } catch (Exception e) {
        LOGGER.error("An error occured while resuming download", e);
      }
    }
  }

  private class StopAction extends AbstractAction {
    private static final long serialVersionUID = -7356442420934284553L;

    public StopAction(){
      putValue( Action.NAME, "Download stoppen" );
    }

    @Override
    public void actionPerformed(ActionEvent anArg0) {
      try {
        myFileTransferHandler.resume();
      } catch (Exception e) {
        LOGGER.error("An error occured while stopping download", e);
      }
    }
  }

}
