/**
 * Copyright (c) 2012 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.p2p.io;

import java.io.IOException;

import chabernac.io.SocketSenderReply;
import chabernac.io.iSocketSender;
import chabernac.protocol.routing.PeerMessage;
import chabernac.protocol.routing.SocketPeer;
import chabernac.tools.SimpleNetworkInterface;

public class PeerToPeerSplitSender {
  private final iSocketSender mySocketSender;

  public PeerToPeerSplitSender ( iSocketSender aSocketSender ) {
    super();
    mySocketSender = aSocketSender;
  }

  public String sendMessageTo(PeerMessage aPeerMessage, SocketPeer aPeer, String aMessage, int aTimeoutInSeconds) throws IOException {
    if(aPeer.getPeerId() != null && mySocketSender.containsSocketForId( aPeer.getPeerId() )){
      return mySocketSender.send( aPeer.getPeerId(), aMessage );
    } else {
      for(SimpleNetworkInterface theHost : aPeer.getHosts()){
        for(String theIp : theHost.getIp()){
          SocketSenderReply theReply = mySocketSender.send(theIp, aPeer.getPort(), aMessage);
          aPeer.setPeerId( theReply.getRemoteId() );
          return theReply.getReply();
        }
      }
      throw new IOException("Could not send message to '" + aPeer.getPeerId() + "'");
    }
  }

  public String getRemoteId(SocketPeer aPeer) throws IOException{
    for(SimpleNetworkInterface theHost : aPeer.getHosts()){
      for(String theIp : theHost.getIp()){
        return mySocketSender.getRemoteId( theIp, aPeer.getPort() );
      }
    }
    throw new IOException("Could get remote id'" + aPeer.getPeerId() + "'");
  }

}