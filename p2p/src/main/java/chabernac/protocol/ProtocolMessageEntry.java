/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol;

public class ProtocolMessageEntry {
  public static enum Status{INPROGRESS, FINISHED, INVALID};
  
  private String myInput;
  private String myOutput;
  private Status myMode;
  private long myTimestamp = System.currentTimeMillis();
  private long myReplyTimestamp=-1;
  
  public ProtocolMessageEntry ( String anInput , Status anMode ) {
    super();
    myInput = anInput;
    myMode = anMode;
  }
  
  public long getTimestamp() {
    return myTimestamp;
  }
  public String getInput() {
    return myInput;
  }
  public void setInput( String anInput ) {
    myInput = anInput;
  }
  public String getOutput() {
    return myOutput;
  }
  public void setOutput( String anOutput ) {
    myOutput = anOutput;
    myReplyTimestamp = System.currentTimeMillis();
  }
  public Status getState() {
    return myMode;
  }
  public void setStatus( Status anMode ) {
    myMode = anMode;
  }
  
  public long getResponseTime(){
    if(myReplyTimestamp == -1) return -1;
    return myReplyTimestamp - myTimestamp;
  }
}
