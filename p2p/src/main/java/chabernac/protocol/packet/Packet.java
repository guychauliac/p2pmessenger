/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.packet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

public class Packet {
  private final String myFrom;
  private final String myTo;
  
  //the id of the protocol for which the packet is intended
  private final String myId;
  private final String myListenerId;
  private final String myBytes;
  private final int myHopDistance;
  private final boolean isSendResponse;
  private final Map<String, String> myHeaders = new HashMap<String, String>();
  
  public Packet( String aTo, String aId, String aListenerId, byte[] aBytes, int aHopDistance, boolean isSendResponse ) {
    this(null, aTo, aId, aListenerId, new String(Base64.encodeBase64( aBytes )), aHopDistance, isSendResponse);
  }
  
  public Packet( String aTo, String aId, String aListenerId, String aBytes, int aHopDistance, boolean isSendResponse ) {
    this(null, aTo, aId, aListenerId, aBytes, aHopDistance, isSendResponse);
  }
  
  Packet( String aFrom, String aTo, String aId, String aListenerId, String aBytes, int aHopDistance, boolean isSendResponse ) {
    super();
    myFrom = aFrom;
    myTo = aTo;
    myId = aId;
    myListenerId = aListenerId;
    myBytes = aBytes;
    myHopDistance = aHopDistance;
    this.isSendResponse = isSendResponse;
  }
  

  public String getFrom() {
    return myFrom;
  }

  public String getTo() {
    return myTo;
  }
  
  public Packet setFrom(String aFrom){
    return new Packet( aFrom, myTo, myId, myListenerId, myBytes, myHopDistance, isSendResponse );
  }

  public String getId() {
    return myId;
  }

  public byte[] getBytes() {
    return Base64.decodeBase64( myBytes.getBytes() );
  }
  
  public String getBytesAsString(){
    return myBytes;
  }


  public int getHopDistance() {
    return myHopDistance;
  }
  
  public Packet decreaseHopDistance(){
    return new Packet( myFrom, myTo, myId, myListenerId, myBytes, myHopDistance - 1, isSendResponse);
  }

  public boolean isSendResponse() {
    return isSendResponse;
  }
  
  public void setHeader(String aKey, String aValue){
	  myHeaders.put(aKey, aValue);
  }

  public String getListenerId() {
    return myListenerId;
  }
  
  public String getHeader(String aKey){
	  return myHeaders.get(aKey);
  }
  
  public boolean containsHeader(String aKey){
	  return myHeaders.containsKey(aKey);
  }
  
  public Map<String, String> getHeaders(){
	  if(myHeaders == null) return new HashMap<String, String>();
	  return Collections.unmodifiableMap(myHeaders);
  }
}
