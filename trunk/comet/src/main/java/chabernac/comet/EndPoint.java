/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.comet;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The endpoint represents a client which has send a request to the server  
 *
 */

public class EndPoint {
  private final String myId;
  private BlockingQueue<CometEvent> myEventQueue = new ArrayBlockingQueue<CometEvent>(1);
  
  public EndPoint ( String anId) {
    super();
    myId = anId;
  }

  public String getId() {
    return myId;
  }
  
  
  public void setEvent(CometEvent anEvent) throws CometException{
    try {
      myEventQueue.put(anEvent);
    } catch (InterruptedException e) {
      throw new CometException("Unable to store event", e);
    }
  }
  
  public CometEvent getEvent() throws CometException{
    try {
      CometEvent theEvent = myEventQueue.take();
      if(theEvent instanceof EndPointDestroyedCometEvent){
        throw new CometException("The end point was destroyed");
      }
      return theEvent;
    } catch (InterruptedException e) {
      throw new CometException("No event available", e);
    }
  }
  
 public void destroy(){
   try {
    myEventQueue.put(new EndPointDestroyedCometEvent());
  } catch (InterruptedException e) {
  }
 }
 
 private class EndPointDestroyedCometEvent extends CometEvent{

  public EndPointDestroyedCometEvent() {
    super(null, null);
  }
   
 }
}