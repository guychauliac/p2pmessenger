/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.facade;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.activation.DataSource;

import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.ProtocolFactory;
import chabernac.protocol.ProtocolServer;
import chabernac.protocol.filetransfer.FileTransferProtocol;
import chabernac.protocol.filetransfer.iFileHandler;
import chabernac.protocol.message.MessageArchive;
import chabernac.protocol.message.MessageIndicator;
import chabernac.protocol.message.MultiPeerMessage;
import chabernac.protocol.message.MultiPeerMessageProtocol;
import chabernac.protocol.message.iDeliverReportListener;
import chabernac.protocol.message.iMultiPeerMessageListener;
import chabernac.protocol.pipe.IPipeListener;
import chabernac.protocol.pipe.Pipe;
import chabernac.protocol.pipe.PipeProtocol;
import chabernac.protocol.routing.Peer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.userinfo.UserInfo;
import chabernac.protocol.userinfo.UserInfoProtocol;
import chabernac.protocol.userinfo.iUserInfoListener;
import chabernac.protocol.userinfo.iUserInfoProvider;
import chabernac.tools.PropertyMap;

/**
 * Facade for easy user of the P2P package
 * 
 *  e.g. start a server which will persist the peer entries to a file
 *  that will exchange routing information with other peers every 300 seconds
 *  and that uses a custom User Info Provider with class 'custom.UserInfoProvider'
 *  
 *  this server will be able to handle 20 simultaneous client requests of other peers.
 * 
 *  P2PFacade theFacade = new P2PFacade()
 *  .setPersist(true)
 *  .setExchangeDelay(300)
 *  .setUserInfoProviderClass("custom.UserInfoProvider")
 *  .start(20);
 *
 * All actions which will pottentialy last long require an ExecutoreService as parameter
 * and will return a Future which will contain the result.
 * 
 * This way these methods can be called from the event dispatching thread 
 * without blocking it for too long.
 * 
 *
 */

public class P2PFacade {
  private ProtocolContainer myContainer = null;
  private ProtocolServer myProtocolServer = null;
  private PropertyMap myProperties = new PropertyMap();
  private MessageArchive myMessageArchive = null;

  /**
   * set the exchange delay.
   * 
   * This is the delay in seconds between exchanging routing table information
   * 
   * in test mode U can use a small number e.g. 5 seconds
   * in real production mode U should use a higher number e.g. 300 seconds
   * 
   * @param anExchangeDelay
   * @return
   * @throws P2PFacadeException
   */
  public P2PFacade setExchangeDelay(long anExchangeDelay) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("routingprotocol.exchangedelay", Long.toString(anExchangeDelay));
    return this;
  }

  public P2PFacade setPersist(boolean isPersist) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("routingprotocol.persist", Boolean.toString( isPersist ));
    return this;
  }

  public P2PFacade setPeerId(String aPeerId) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("peerid", aPeerId);
    return this;
  }
  
  public P2PFacade setSuperNodesDataSource(DataSource aDataSource) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("routingprotocol.supernodes", aDataSource);
    return this;
  }

  /**
   * set the user info provider class
   * the full name of the class must be an implementation of chabernac.protocol.userinfo.iUserInfoProvider
   * 
   * @param aUserInfoProviderClass
   * @return
   * @throws P2PFacadeException
   */
  public P2PFacade setUserInfoProvider(iUserInfoProvider aUserInfoProvider) throws P2PFacadeException{
    myProperties.setProperty("chabernac.protocol.userinfo.iUserInfoProvider", aUserInfoProvider);
    if(isStarted()) {
      try {
        ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).setUserInfoProvider( aUserInfoProvider );
      } catch ( ProtocolException e ) {
        throw new P2PFacadeException("An error occured while setting user info provider", e);
      }
    }
    return this;
  }

  public Future<Boolean> sendFile(final File aFile, final String aPeerId, ExecutorService aService) {
    return aService.submit(  new Callable< Boolean >(){

      @Override
      public Boolean call() throws Exception {
        sendFile( aFile, aPeerId );
        return Boolean.TRUE;
      }
    });
  }

  private void sendFile(File aFile, String aPeerId) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      ((FileTransferProtocol)myContainer.getProtocol( FileTransferProtocol.ID )).sendFile( aFile, aPeerId );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while sending file", e);
    }
  }

  public void setFileHandler(iFileHandler aFileHandler) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      ((FileTransferProtocol)myContainer.getProtocol( FileTransferProtocol.ID )).setFileHandler( aFileHandler );
    } catch ( ProtocolException e ) {
      throw new P2PFacadeException("An error occured while setting file handler", e);
    }
  }

  public Future<Boolean> sendMessage(final MultiPeerMessage aMessage, ExecutorService aService){
    return aService.submit(  new Callable< Boolean >(){

      @Override
      public Boolean call() throws Exception {
        sendMessage( aMessage );
        return Boolean.TRUE;
      }
    });
  }

  private void sendMessage(MultiPeerMessage aMessage) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).sendMessage( aMessage );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while sending multi peer message", e);
    }
  }

  public Future<MultiPeerMessage> sendEncryptedMessage(final MultiPeerMessage aMessage, ExecutorService aService){
    return aService.submit(  new Callable< MultiPeerMessage >(){

      @Override
      public MultiPeerMessage call() throws Exception {
        return sendEncryptedMessage( aMessage );
      }
    });
  }

  private MultiPeerMessage sendEncryptedMessage(MultiPeerMessage aMessage) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      aMessage.addMessageIndicator( MessageIndicator.TO_BE_ENCRYPTED );
      return ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).sendMessage( aMessage );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while sending multi peer message", e);
    }
  }

  public void addMessageListener(iMultiPeerMessageListener aMessageListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).addMultiPeerMessageListener( aMessageListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while adding message listener", e);
    }
  }

  public void removeMessageListener(iMultiPeerMessageListener aMessageListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).removeMultiPeerMessageListener(  aMessageListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while removing message listener", e);
    }
  }

  public void addDeliveryReportListener(iDeliverReportListener aDeliveryReportListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).addDeliveryReportListener( aDeliveryReportListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while adding delivery report listener", e);
    }
  }

  public void removeDeliveryReportListener(iDeliverReportListener aDeliveryReportListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).removeDeliveryReportListener( aDeliveryReportListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while adding delivery report listener", e);
    }
  }

  public Pipe openPipe(String aPeerId, String aPipeDescription) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      Peer thePeer = getPeer( aPeerId );
      Pipe thePipe = new Pipe(thePeer);
      thePipe.setPipeDescription( aPipeDescription );
      ((PipeProtocol)myContainer.getProtocol( PipeProtocol.ID )).openPipe( thePipe );
      return thePipe;
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while opening pipe", e);
    }
  }

  public void closePipe(Pipe aPipe) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((PipeProtocol)myContainer.getProtocol( PipeProtocol.ID )).closePipe( aPipe );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while closing pipe", e);
    }
  }

  public void addPipeListener(IPipeListener aPipeListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((PipeProtocol)myContainer.getProtocol( PipeProtocol.ID )).addPipeListener( aPipeListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while adding pipe listener", e);
    }
  }

  public void addUserInfoListener(iUserInfoListener aListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).addUserInfoListener( aListener );
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while adding user info listener", e);
    }
  }

  /**
   * this method returns a map with user info
   * key: peer id
   * 
   * @return
   * @throws P2PFacadeException
   */
  public Map< String, UserInfo > getUserInfo() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).getUserInfo();
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while getting user info", e);
    }
  }

  public String getPeerId() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getLocalPeerId();
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while retrieving peer id", e);
    }
  }

  public Peer getPeer(String aPeerId) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getRoutingTable().getEntryForPeer( aPeerId ).getPeer();
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while retrieving peer id", e);
    }
  }

  public MessageArchive getMessageArchive() throws P2PFacadeException {
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      if(myMessageArchive == null){
        myMessageArchive = new MessageArchive(((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )));
      }
      return myMessageArchive;
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while creationg message archive", e);
    }
  }

  public boolean isStarted(){
    if(myProtocolServer == null) return false;
    return myProtocolServer.isStarted();
  }

  public P2PFacade start(int aNumberOfThreads) throws P2PFacadeException{
    if(isStarted()) return this;

    try{
      ProtocolFactory theFactory = new ProtocolFactory(myProperties);
      myContainer = new ProtocolContainer(theFactory);
      myProtocolServer = new ProtocolServer(myContainer, RoutingProtocol.START_PORT, aNumberOfThreads, true);
      myProtocolServer.start();

      //we retrieve the routing protcol
      //this way it is instantiated and start exchanging routing information
      myContainer.getProtocol( RoutingProtocol.ID );

      //retrieve the user info protocol
      //this way it is instantiated and listens for routing table changes and retrieves user info of the changed peers
      myContainer.getProtocol( UserInfoProtocol.ID );
      return this;
    }catch(Exception e){
      throw new P2PFacadeException("Could not start P2P Facade", e);
    }
  }

  public P2PFacade stop(){
    if(myProtocolServer != null){
      myProtocolServer.stop();
    }
    return this;
  }

}
