package chabernac.control;


public abstract class SynchronizedKeyCommand extends KeyCommand implements iSynchronizedEvent{
  private static final long serialVersionUID = 5840724308828111117L;
  private transient SynchronizedEventManager myManager = null;
  private int myTimerInterval;
  private int myEventsAfterRelease = 0;
  private int myEvent = 0;
  private boolean registered = false;
  
  public SynchronizedKeyCommand(String aDescription, SynchronizedEventManager aManager){
    this(aDescription, aManager, 1);
  }
  
  public SynchronizedKeyCommand(String aDescription, SynchronizedEventManager aManager, int aTimerInterval){
    super(aDescription);
    myManager = aManager;
    myTimerInterval = aTimerInterval;
  }
  
  public final void keyPressed() {
	  if(!registered){
		  myManager.addSyncronizedEvent(this);
		  registered = true;
	  }
  }

  public final void keyReleased() {
	  if(registered){
		  myManager.removeSyncronizedEvent(this);
		  registered = false;
	  }
  }
  
  public void setEventsAfterRelease(int aNumber){ myEventsAfterRelease = aNumber; }
  public int getEventsAfterRelease(){ return myEventsAfterRelease; }
  public int getEventNrAfterRelease(){ return myEvent; }
  
  public boolean executeEvent(long aCounter){
	  if(aCounter % myTimerInterval == 0){
		  keyDown();
		  return true;
	  }
	  return false;
  }
  
  public boolean isRecordable(){
    return true;
  }
 

}
