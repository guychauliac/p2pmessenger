/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.message;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import chabernac.io.Base64ObjectStringConverter;
import chabernac.io.iObjectStringConverter;
import chabernac.protocol.Protocol;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.encryption.EncryptionException;
import chabernac.protocol.encryption.EncryptionProtocol;
import chabernac.protocol.routing.AbstractPeer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import chabernac.protocol.routing.UnknownPeerException;
import chabernac.protocol.routing.iPeerSender;

public abstract class AbstractMessageProtocol extends Protocol {
  protected static Logger LOGGER = Logger.getLogger( AbstractMessageProtocol.class );
  
  protected iObjectStringConverter< Message > myMessageConverter = new Base64ObjectStringConverter< Message >();
  
  
  protected List<String> myProcessingMessages = Collections.synchronizedList(new ArrayList<String>());
  protected Set<String> myProcessedMessages = Collections.synchronizedSet(new HashSet<String>());
  
  protected List<iMessageListener> myListeners = new ArrayList< iMessageListener >();
  
  protected boolean isKeepHistory = false;
  protected LinkedHashMap<String, MessageAndResponse > myHistory = new LinkedHashMap<String, MessageAndResponse >();
  protected List< iMessageListener > myHistoryListeners = new ArrayList< iMessageListener >();
  
  public static enum Response {
    UNKNOWN_PEER, 
    UNKNOWN_HOST, 
    UNDELIVERABLE, 
    DELIVERED, 
    UNCRECOGNIZED_MESSAGE, 
    COULD_NOT_DECRYPT, 
    TTL_EXPIRED, 
    MESSAGE_LOOP_DETECTED,
    MESSAGE_ALREADY_RECEIVED,
    MESSAGE_PROCESSED,
    MESSAGE_REJECTED,
    NO_CONFIRMATION_RECEIVED
    };

  public AbstractMessageProtocol( String anId ) {
    super( anId );
  }
  
  public RoutingTable getRoutingTable() throws ProtocolException{
    return ((RoutingProtocol)findProtocolContainer().getProtocol( RoutingProtocol.ID )).getRoutingTable();
  }
  
  public iPeerSender getPeerSender() throws ProtocolException{
    return ((RoutingProtocol)findProtocolContainer().getProtocol( RoutingProtocol.ID )).getPeerSender();
  }
  
  protected String forwardMessage(Message aMessage) throws UnknownPeerException, ProtocolException, UnknownHostException, IOException{
    if(aMessage.isEndOfTTL()){
      LOGGER.error("This message can not be send further because its TTL has expired: " + aMessage.getMessageId() );
      return Response.TTL_EXPIRED.name();
    } else {
      aMessage.decreaseTTL();

      RoutingTableEntry theEntry = getRoutingTable().getEntryForPeer( aMessage.getDestination().getPeerId() );

      //only forward the message if this peer is reachable
      if(!theEntry.isReachable()) return Response.UNDELIVERABLE.name() + " the peer with peer id: '" + theEntry.getPeer().getPeerId() + "' is not reachable";

      AbstractPeer theGateway = getRoutingTable().getGatewayForPeer( aMessage.getDestination() );
      
      aMessage.setLastHop(getRoutingTable().getEntryForLocalPeer().getPeer());
//      LOGGER.debug("Forwarding message with id '" + aMessage.getMessageId().toString() + "' last hop='" + aMessage.getLastHop() + "' hops='" + aMessage.getHops() + "'");

      AbstractPeer theLocalPeer = getRoutingTable().getEntryForLocalPeer().getPeer();
      if(!theGateway.isSameEndPointAs( theLocalPeer )){
        aMessage.setLocked( true );
        String theText = myMessageConverter.toString( aMessage );
        aMessage.setLocked( false );
        return getPeerSender().send(theGateway, createMessage( theText ), aMessage.getMessageTimeoutInSeconds()).getReply();
      } else {
        //TODO we should not come in this situation
        LOGGER.error("Peer with id: '" + theGateway.getPeerId() + "' has same host and port as local peer: '" + theLocalPeer.getPeerId() + "'");
        return Response.UNDELIVERABLE.name() + " gateway has same host and port of local peer";
      }
    }
  }
  
  protected String handleMessageForUs(String aSession, Message aMessage) throws EncryptionException{
    checkEnctryption(aMessage);
    
    if(myProcessedMessages.contains(aMessage.getUniqueId())){
      return Response.MESSAGE_ALREADY_RECEIVED.name();
    }
    
    myProcessedMessages.add(aMessage.getUniqueId());
    
    if(aMessage.isProtocolMessage()){
      //reoffer the content of the message to the handle method
      //this will cause sub protocols to handle the message if they are present
      return Response.DELIVERED.name() + getMasterProtocol().handleCommand( aSession, aMessage.getMessage() );
    } else {
      for(iMessageListener theListener : myListeners){
        theListener.messageReceived( aMessage );
      }
      return Response.DELIVERED.name();
    }
  }
  
  private void checkEnctryption( Message anMessage ) throws EncryptionException {
    if(anMessage.containsIndicator( MessageIndicator.ENCRYPTED )){
      try{
        EncryptionProtocol theEncryptionProtocol = ((EncryptionProtocol)findProtocolContainer().getProtocol( EncryptionProtocol.ID ));
        theEncryptionProtocol.decryptMessage( anMessage );
      }catch(Exception e){
        throw new EncryptionException("Could not decrypt message", e);
      }
    }
  }
  
  protected boolean obtainPublicKey(AbstractPeer aPeer){
    try{
      EncryptionProtocol theEncryptionProtocol = ((EncryptionProtocol)findProtocolContainer().getProtocol( EncryptionProtocol.ID ));
      return theEncryptionProtocol.getPublicKeyFor(aPeer, true) != null;
      
    }catch(Exception e){
      LOGGER.error("Unable to obtain public key for peer '" + aPeer.getPeerId() + "'", e);
      return false;
    }
  }
  
  protected void inspectMessage(Message aMessage) throws MessageException{
    try {
      aMessage.setSource(getRoutingTable().getEntryForLocalPeer().getPeer());
    } catch (Exception e1) {
      throw new MessageException("Unable to set source peer in message", e1);
    }
    //check if both peers are on the same channel
    if(!aMessage.getSource().isOnSameChannel(aMessage.getDestination())){
      throw new MessageException("Can not send message to peer on another channel");
    }
    
    if(aMessage.isProtocolMessage()){
      //inspect if the protocol inside the message is supported by the other peer
      String theProtocol = aMessage.getMessage().substring( 0, 3 );
      if(!aMessage.getDestination().isProtocolSupported(  theProtocol )){
        throw new MessageException( "The protocol inside this message is '" + theProtocol + "' is not supported by this client" );
      }
    }

    if(aMessage.containsIndicator( MessageIndicator.TO_BE_ENCRYPTED)){
      try{
        EncryptionProtocol theEncryptionProtocol = ((EncryptionProtocol)findProtocolContainer().getProtocol( EncryptionProtocol.ID ));
        theEncryptionProtocol.encryptMessage( aMessage );
      }catch(Exception e){
        throw new MessageException("Could not send encrypted message", e);
      }
    }

    //we reset the TTL, it might be that the same message is reused.
    aMessage.resetTTL();
  }
  
  protected String inspectResult(String aResult) throws MessageException{
    if(aResult.startsWith( Response.DELIVERED.name() )){
      return aResult.substring( Response.DELIVERED.name().length() );
    } else if(aResult.startsWith(Response.MESSAGE_ALREADY_RECEIVED.name())){
      throw new MessageAlreadyDeliveredException("This message was already delivered to this peer");
    }
    throw new MessageException("Message could not be delivered return code: '" + aResult + "'", Response.valueOf(aResult.split(" ")[0]));  
  }
  
  /**
   * in this method we ask the routing protocol to check the sending peer.
   * When the peer is not yet in the routing table, the routing protocol will try to contact the peer
   * and update the routing table
   * @param anMessage
   */
  protected void checkMessage( Message anMessage ) {
    try{
      RoutingProtocol theProtocol = ((RoutingProtocol)findProtocolContainer().getProtocol( RoutingProtocol.ID ));
      theProtocol.checkPeer( anMessage.getSource() );
      if(anMessage.getLastHop() != null) theProtocol.checkPeer( anMessage.getLastHop() );
    }catch(Exception e){
      LOGGER.error("An error occured while checking for peer ", e);
    }
  }
  
  public boolean isKeepHistory() {
    return isKeepHistory;
  }

  public void setKeepHistory(boolean aKeepHistory) {
    isKeepHistory = aKeepHistory;
  }
  
  public void clearHistory(){
    myHistory.clear();
  }

  public List<MessageAndResponse> getHistory(){
    return Collections.unmodifiableList( new ArrayList< MessageAndResponse >( myHistory.values()) );
  }
  
  public void addMessageListener(iMessageListener aListener){
    myListeners.add( aListener );
  }

  public void removeMessageListener(iMessageListener aListener){
    myListeners.remove( aListener );
  }

  public void addMessageHistoryListener(iMessageListener aListener){
    myHistoryListeners.add( aListener );
  }

  public void removeMessageHistoryListener(iMessageListener aListener){
    myHistoryListeners.remove( aListener );
  }
}
