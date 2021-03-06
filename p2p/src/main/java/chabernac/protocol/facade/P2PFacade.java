/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.facade;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.sound.sampled.AudioFormat;

import org.apache.log4j.Logger;

import chabernac.io.BasicSocketPool;
import chabernac.io.CachingSocketPool;
import chabernac.io.iSocketPool;
import chabernac.p2p.debug.RoutingFrame;
import chabernac.p2p.settings.P2PSettings;
import chabernac.protocol.P2PServerFactory;
import chabernac.protocol.P2PServerFactory.ServerMode;
import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.ProtocolFactory;
import chabernac.protocol.iP2PServer;
import chabernac.protocol.iProtocolDelegate;
import chabernac.protocol.application.ApplicationProtocol;
import chabernac.protocol.asyncfiletransfer.AsyncFileTransferProtocol;
import chabernac.protocol.asyncfiletransfer.iAsyncFileTransferHandler;
import chabernac.protocol.cam.CamProtocol;
import chabernac.protocol.cam.CamProtocolException;
import chabernac.protocol.cam.iCamListener;
import chabernac.protocol.encryption.EncryptionProtocol;
import chabernac.protocol.filetransfer.FileTransferProtocol;
import chabernac.protocol.filetransfer.iFileHandler;
import chabernac.protocol.infoexchange.InfoExchangeProtocol;
import chabernac.protocol.infoexchange.InfoObject;
import chabernac.protocol.infoexchange.iInfoListener;
import chabernac.protocol.message.AsyncMessageProcotol;
import chabernac.protocol.message.FailedMessageResender;
import chabernac.protocol.message.Message;
import chabernac.protocol.message.MessageArchive;
import chabernac.protocol.message.MessageIndicator;
import chabernac.protocol.message.MultiPeerMessage;
import chabernac.protocol.message.MultiPeerMessageProtocol;
import chabernac.protocol.message.iDeliverReportListener;
import chabernac.protocol.message.iMultiPeerMessageListener;
import chabernac.protocol.packet.AbstractTransferState;
import chabernac.protocol.packet.AbstractTransferState.Direction;
import chabernac.protocol.packet.AsyncTransferProtocol;
import chabernac.protocol.packet.TransferOverviewFrame;
import chabernac.protocol.packet.TransferOverviewPanel;
import chabernac.protocol.packet.iTransferContainer;
import chabernac.protocol.pipe.IPipeListener;
import chabernac.protocol.pipe.Pipe;
import chabernac.protocol.pipe.PipeProtocol;
import chabernac.protocol.routing.AbstractPeer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import chabernac.protocol.routing.SocketPeer;
import chabernac.protocol.routing.WebPeerProtocol;
import chabernac.protocol.userinfo.AutoUserInfoStatusDector;
import chabernac.protocol.userinfo.UserInfo;
import chabernac.protocol.userinfo.UserInfo.Status;
import chabernac.protocol.userinfo.UserInfoProtocol;
import chabernac.protocol.userinfo.iUserInfoListener;
import chabernac.protocol.userinfo.iUserInfoProvider;
import chabernac.protocol.version.Version;
import chabernac.protocol.version.VersionProtocol;
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
  private static Logger LOGGER = Logger.getLogger(P2PFacade.class);
  private ServerMode myServerMode = ServerMode.SOCKET;
  private ProtocolContainer myContainer = null;
  private List<iP2PServer> myProtocolServers = new ArrayList< iP2PServer >();
  private PropertyMap myProperties = new PropertyMap();
  private MessageArchive myMessageArchive = null;
  private boolean myIsKeepHistory = false;
  private FailedMessageResender myMessageResender = null;
  private boolean isActivateMessageResender = false;
  private int myWebPort = 8080;
  private Integer myAJPPort = null;
  private URL myWebURL = null;
  private Set<String> mySupportedProtocols = null;
  private TransferOverviewPanel myFileTransferOverview = null; 
  private TransferOverviewFrame myFileTransferOverviewFrame = null;
  private AutoUserInfoStatusDector myAutoUserInfoStatusDetector = null;
  private boolean isAutoUserStatusEnabled = false;

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

  public P2PFacade setStopWhenAlreadyRunning(boolean isStopWhenAlreadyRunning) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("routingprotocol.stopwhenalreadyrunning", Boolean.toString( isStopWhenAlreadyRunning));
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

  public P2PFacade addSuperNode(String aSuperNode) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    if(!myProperties.containsKey( "routingprotocol.supernodes" )){
      myProperties.put( "routingprotocol.supernodes", new ArrayList<String>() );
    }
    ((List<String>)myProperties.get( "routingprotocol.supernodes" )).add( aSuperNode );
    return this;
  }

  public P2PFacade setChannel(String aChannel) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property when the server has already been started");
    myProperties.setProperty("routingprotocol.channel", aChannel);
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

  public P2PFacade setAutoUserStatusDetectionEnabled(boolean isAutoUserStatusEnabled) throws P2PFacadeException{
    if(isStarted()){
      if(isAutoUserStatusEnabled){
        if(myAutoUserInfoStatusDetector == null){
          myAutoUserInfoStatusDetector = new AutoUserInfoStatusDector( getPersonalInfo() );
          myAutoUserInfoStatusDetector.start();
        }
      } else {
        if(myAutoUserInfoStatusDetector != null){
          myAutoUserInfoStatusDetector.stop();
        } 
      }
    } else {
      this.isAutoUserStatusEnabled = isAutoUserStatusEnabled;
    }
    return this;
  }

  public P2PFacade disableAutoUserStatusDetection() {
    if(myAutoUserInfoStatusDetector != null){
      myAutoUserInfoStatusDetector.stop();
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

  public AbstractTransferState sendFileAsync(File aFile, String aPeerId, int aPacketSize, int anOutstandingPackets) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      return ((AsyncTransferProtocol)myContainer.getProtocol( AsyncTransferProtocol.ID )).startFileTransfer( aFile, aPeerId, aPacketSize, anOutstandingPackets);
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while sending file", e);
    }
  }

  public AbstractTransferState startAudioTransfer(String aPeerId, AudioFormat.Encoding anEncoding,int aSamplesPerSecond, int aBits, int aSpeexQuality, int aPacketsPerSecond, Direction aDirection) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      return ((AsyncTransferProtocol)myContainer.getProtocol( AsyncTransferProtocol.ID )).startAudioTransfer(aPeerId, anEncoding, aSamplesPerSecond, aBits, aSpeexQuality, aPacketsPerSecond, aDirection);
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while starting audio transfer", e);
    }
  }

  public P2PFacade setFileHandler(iFileHandler aFileHandler) throws P2PFacadeException{
    myProperties.setProperty( "chabernac.protocol.filetransfer.iFileHandler", aFileHandler );
    if(isStarted()){
      try {
        ((FileTransferProtocol)myContainer.getProtocol( FileTransferProtocol.ID )).setFileHandler( aFileHandler );
      } catch ( ProtocolException e ) {
        throw new P2PFacadeException("An error occured while setting file handler", e);
      }
    }
    return this;
  }

  public P2PFacade setAsyncFileHandler(iAsyncFileTransferHandler aFileHandler) throws P2PFacadeException{
    myProperties.setProperty( "chabernac.protocol.filetransfer.iAsyncFileTransferHandler", aFileHandler );
    if(isStarted()){
      try {
        ((AsyncFileTransferProtocol)myContainer.getProtocol( AsyncFileTransferProtocol.ID )).setFileHandler( aFileHandler );
      } catch ( ProtocolException e ) {
        throw new P2PFacadeException("An error occured while setting file handler", e);
      }
    }
    return this;
  }

  public iTransferContainer getAsyncFileTransferContainer() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      return ((AsyncTransferProtocol)myContainer.getProtocol( AsyncTransferProtocol.ID ));
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while sending file", e);
    }
  }

  public TransferOverviewPanel getFileTransferOverview() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    if(myFileTransferOverview == null) {
      myFileTransferOverview = new TransferOverviewPanel( getAsyncFileTransferContainer() );
    }

    return myFileTransferOverview;
  }

  public void showFileTransferOverView() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    if(myFileTransferOverviewFrame == null){
      myFileTransferOverviewFrame = new TransferOverviewFrame( getAsyncFileTransferContainer() );
    }
    myFileTransferOverviewFrame.setVisible( true );
  }

  public String sendTechnicalMessage(String aPeerId, String aMessage) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try{
      Message theMessage = new Message();
      theMessage.setProtocolMessage( true );
      theMessage.setDestination( getPeer( aPeerId ) );
      theMessage.setSource( getPeer( getPeerId()) );
      theMessage.setMessage( aMessage );
      return ((AsyncMessageProcotol)myContainer.getProtocol( AsyncMessageProcotol.ID )).sendAndWaitForResponse( theMessage );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while sending technical message", e);
    }
  }

  public Future<MultiPeerMessage> sendMessage(final MultiPeerMessage aMessage, ExecutorService aService){
    return aService.submit(  new Callable< MultiPeerMessage >(){

      @Override
      public MultiPeerMessage call() throws Exception {
        return sendMessage( aMessage );
      }
    });
  }

  private MultiPeerMessage sendMessage(MultiPeerMessage aMessage) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      return ((MultiPeerMessageProtocol)myContainer.getProtocol( MultiPeerMessageProtocol.ID )).sendMessage( aMessage );
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
      aMessage = aMessage.addMessageIndicator( MessageIndicator.TO_BE_ENCRYPTED );
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
      AbstractPeer thePeer = getPeer( aPeerId );
      if(!(thePeer instanceof SocketPeer)) throw new P2PFacadeException("Can only open pipe to socket peer");
      Pipe thePipe = new Pipe((SocketPeer)thePeer);
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

  public void removePipeListener(IPipeListener aPipeListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((PipeProtocol)myContainer.getProtocol( PipeProtocol.ID )).removePipeListener( aPipeListener );
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

  public void removeUserInfoListener( iUserInfoListener aUserInfoListener) throws P2PFacadeException {
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).removeUserInfoListener( aUserInfoListener );
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

  public UserInfo getPersonalInfo() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).getPersonalInfo();
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

  public AbstractPeer getPeer(String aPeerId) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getRoutingTable().getEntryForPeer( aPeerId ).getPeer();
    } catch ( Exception e ) {
      throw new P2PFacadeException("An error occured while retrieving peer id", e);
    }
  }

  public RoutingTableEntry getRoutingTableEntry(String aPeerId) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try {
      return ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getRoutingTable().getEntryForPeer( aPeerId );
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

  public Map< String, Version > getVersions() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      return Collections.unmodifiableMap( ((VersionProtocol)myContainer.getProtocol( VersionProtocol.ID )).getVersions() );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while creationg message archive", e);
    }
  }

  public Version getLocalVersion() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      return  ((VersionProtocol)myContainer.getProtocol( VersionProtocol.ID )).getLocalVersion();
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while creationg message archive", e);
    }
  }


  public P2PFacade setVersion( Version aVersion ) throws P2PFacadeException {
    if(isStarted()) throw new P2PFacadeException("Can not execute this action when the server is started");

    myProperties.setProperty("chabernac.protocol.version", aVersion);
    return this;
  }

  @SuppressWarnings("unchecked")
  public InfoObject getInfoObject() throws P2PFacadeException{
    if(!isStarted()) {
      if(!myProperties.containsKey( "chabernac.protocol.infoexchange.InfoObject" )){
        myProperties.setProperty( "chabernac.protocol.infoexchange.InfoObject", new InfoObject() );
      }
      return (InfoObject)myProperties.getProperty( "chabernac.protocol.infoexchange.InfoObject", new InfoObject() );
    } else {
      try{
        return  ((InfoExchangeProtocol< InfoObject >)myContainer.getProtocol( InfoExchangeProtocol.ID )).getInfoObject();
      }catch(Exception e){
        throw new P2PFacadeException("An error occured while getting info object", e);
      }
    }
  }

  public P2PFacade setInfoObject(String aKey, Serializable anObject) throws P2PFacadeException{
    getInfoObject().put( aKey, anObject );
    return this;
  }

  public Map<String, InfoObject> getInfoMap() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is started");
    try{
      return  ((InfoExchangeProtocol< InfoObject >)myContainer.getProtocol( InfoExchangeProtocol.ID )).getInfoMap();
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while getting info object", e);
    }
  }

  public void addInfoListener(iInfoListener< InfoObject > aListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      ((InfoExchangeProtocol< InfoObject >)myContainer.getProtocol( InfoExchangeProtocol.ID )).addInfoListener( aListener );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while getting info object", e);
    }
  }

  public void removeInfoListener(iInfoListener< InfoObject > aListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      ((InfoExchangeProtocol< InfoObject >)myContainer.getProtocol( InfoExchangeProtocol.ID )).removeInfoListener( aListener );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while getting info object", e);
    }
  }

  public P2PFacade setApplicationProtocolDelegate(iProtocolDelegate aProtolDelegate) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      ((ApplicationProtocol)myContainer.getProtocol( ApplicationProtocol.ID )).setDelegate( aProtolDelegate );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while setting protocol deleegate", e);
    }
    return this;
  }

  public P2PFacade setSocketReuse(boolean isSocketReuse){
    iSocketPool theSocketPool = P2PSettings.getInstance().getSocketPool();
    if(theSocketPool instanceof BasicSocketPool){
      ((BasicSocketPool)theSocketPool).setSocketReuse( isSocketReuse );
    }
    return this;
  }

  public String sendApplicationMessage(String aPeerId, String aMessage) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      return ((ApplicationProtocol)myContainer.getProtocol( ApplicationProtocol.ID )).sendMessage( aPeerId, aMessage );
    }catch(Exception e){
      throw new P2PFacadeException("An error occured while setting protocol deleegate", e);
    }
  }

  public P2PFacade setMessageResenderActivated(boolean isActivated) throws P2PFacadeException{
    isActivateMessageResender = isActivated;

    if(isStarted()){
      if(isActivated && myMessageResender == null){
        try {
          myMessageResender = new FailedMessageResender( myContainer );
          myMessageResender.start();
        } catch ( ProtocolException e ) {
          throw new P2PFacadeException("Unable to activate message resender", e);
        }
      } else if(!isActivated && myMessageResender != null){
        myMessageResender.stop();
        myMessageResender = null;
      }
    }

    return this;
  }

  public FailedMessageResender getFailedMessageResender(){
    return myMessageResender;
  }

  public void showRoutingTable() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      RoutingFrame theFrame = new RoutingFrame( myProtocolServers.get(0),  myContainer, false);
      theFrame.setVisible( true );
    }catch(Exception e){
      throw new P2PFacadeException("Could not show routing frame", e);
    }
  }

  P2PFacade setKeepRoutingTableHistory(boolean isKeepHistory) throws P2PFacadeException{
    if(!isStarted()){
      myIsKeepHistory = isKeepHistory;
    } else {
      try{
        ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getRoutingTable().setKeepHistory( isKeepHistory );
      }catch(Exception e){
        throw new P2PFacadeException("Could not change keep history", e);
      }
    }
    return this;
  }

  RoutingTable getRoutingTable() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      return ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).getRoutingTable();
    }catch(Exception e){
      throw new P2PFacadeException("Could not show routing frame", e);
    }
  }

  public void scanSuperNodes() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      ((RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID )).scanSuperNodes();
    }catch(Exception e){
      throw new P2PFacadeException("Could not show routing frame", e);
    }
  }

  public void changeRemoteUserStatus(String aUserId, Status aStatus) throws P2PFacadeException{
    changeRemoteUserStatus( aUserId, aStatus, null );
  }

  public void changeRemoteUserStatus(String aUserId, Status aStatus, String aStatusDescription) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      ((UserInfoProtocol)myContainer.getProtocol( UserInfoProtocol.ID )).changeStatus( aUserId, aStatus, aStatusDescription );
    }catch(Exception e){
      throw new P2PFacadeException("Could not remotely change user info status", e);
    }
  }

  public boolean isStarted(){
    if(myProtocolServers.isEmpty()) return false;
    return myProtocolServers.get(0).isStarted();
  }

  public void forceProtocolStart(String aProtocolId) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");
    try {
      myContainer.getProtocol( aProtocolId, true );
    } catch ( Exception e ) {
      throw new P2PFacadeException("Could not force start protocol '" + aProtocolId + "'", e);
    }
  }

  public P2PFacade setServerMode(ServerMode aServerMode) throws P2PFacadeException {
    if(isStarted()) throw new P2PFacadeException("Can not set this property whern the server has been started");
    myServerMode = aServerMode;
    return this;
  }

  public P2PFacade setWebPort( int aWebPort ) throws P2PFacadeException {
    if(isStarted()) throw new P2PFacadeException("Can not set this property whern the server has been started");
    if(myServerMode != ServerMode.WEB && myServerMode != ServerMode.BOTH)  throw new P2PFacadeException("Can only set this property on a webnode");
    myWebPort = aWebPort;
    return this;
  }


  public P2PFacade setAJPPort( Integer aAJPPort ) throws P2PFacadeException {
    if(isStarted()) throw new P2PFacadeException("Can not set this property whern the server has been started");
    if(myServerMode != ServerMode.WEB && myServerMode != ServerMode.BOTH)  throw new P2PFacadeException("Can only set this property on a webnode");
    myAJPPort = aAJPPort;
    return this;
  }

  public P2PFacade setWebURL( URL aWebURL ) throws P2PFacadeException {
    if(isStarted()) throw new P2PFacadeException("Can not set this property whern the server has been started");
    if(myServerMode != ServerMode.WEB && myServerMode != ServerMode.BOTH)  throw new P2PFacadeException("Can only set this property on a webnode");

    myWebURL = aWebURL;
    return this;
  }

  private CamProtocol getCamProtocol() throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    try{
      return ((CamProtocol)myContainer.getProtocol( CamProtocol.ID ));
    }catch(Exception e){
      throw new P2PFacadeException("Could not show routing frame", e);
    }
  }

  public void setCamListener(iCamListener aListener) throws P2PFacadeException{
    if(!isStarted()) throw new P2PFacadeException("Can not execute this action when the server is not started");

    getCamProtocol().addCamListener( aListener );
  }

  public void requestCapture(String aPeerId, int aWidth, int aHeight, float aQuality) throws P2PFacadeException{
    try {
      getCamProtocol().requestCapture( aPeerId, aWidth, aHeight, aQuality );
    } catch ( CamProtocolException e ) {
      throw new P2PFacadeException("An error occured while capturing", e);
    }
  }

  public P2PFacade addSupportedProtocol(String aProtocol) throws P2PFacadeException{
    if(isStarted()) throw new P2PFacadeException("Can not set this property whern the server has been started");

    if(mySupportedProtocols == null) mySupportedProtocols = new HashSet<String>();
    mySupportedProtocols.add(aProtocol);
    return this;
  }

  public P2PFacade start() throws P2PFacadeException{
    if(isStarted()) return this;

    try{
      ProtocolFactory theFactory = new ProtocolFactory(myProperties);
      myContainer = new ProtocolContainer(theFactory, mySupportedProtocols);

      //we retrieve the routing protocol
      //this way it is instantiated and will start exchanging routing information when the server is started
      RoutingProtocol theRoutingProtocol = (RoutingProtocol)myContainer.getProtocol( RoutingProtocol.ID );
      theRoutingProtocol.getRoutingTable().setKeepHistory( myIsKeepHistory );


      if(myServerMode == ServerMode.WEB || myServerMode == ServerMode.BOTH){
        myProtocolServers.add(P2PServerFactory.createWebServer( myContainer, myWebURL, myWebPort, myAJPPort));
      }

      if(myServerMode == ServerMode.SOCKET || myServerMode == ServerMode.BOTH || myServerMode == ServerMode.SPLITTING_SOCKET) {
        myProtocolServers.add(P2PServerFactory.createSocketServer( myContainer, myServerMode ));
      }

      for(iP2PServer theServer : myProtocolServers){
        if(!theServer.start()) throw new P2PFacadeException("Unable to start protocol server");
      }

      //retrieve the user info protocol
      //this way it is instantiated and listens for routing table changes and retrieves user info of the changed peers
      if(mySupportedProtocols == null || mySupportedProtocols.contains( UserInfoProtocol.ID )) myContainer.getProtocol( UserInfoProtocol.ID );

      //retrieve the version protocol so that is starts exchanging versions
      //the version protocol is not used any more, version info is exchanged with info exchange protocol
      //      if(mySupportedProtocols == null || mySupportedProtocols.contains( VersionProtocol.ID)) myContainer.getProtocol( VersionProtocol.ID );

      if(mySupportedProtocols == null || mySupportedProtocols.contains( WebPeerProtocol.ID)) myContainer.getProtocol( WebPeerProtocol.ID );

      if(mySupportedProtocols == null || mySupportedProtocols.contains( InfoExchangeProtocol.ID)) myContainer.getProtocol( InfoExchangeProtocol.ID );

      //load the encryption protocol immediatly, generating the public/private key pair takes some time
      if(mySupportedProtocols == null || mySupportedProtocols.contains( EncryptionProtocol.ID)) myContainer.getProtocol( EncryptionProtocol.ID );

      iSocketPool theSocketPool = P2PSettings.getInstance().getSocketPool();
      if(theSocketPool instanceof CachingSocketPool){
        ((CachingSocketPool)theSocketPool).setCleanUpTimeInSeconds( 30 );
      }

      if(isActivateMessageResender){
        myMessageResender = new FailedMessageResender( myContainer );
        myMessageResender.start();
      }

      setAutoUserStatusDetectionEnabled(isAutoUserStatusEnabled);

      return this;
    }catch(Exception e){
      LOGGER.error( "An error occured while starting facade", e );
      stop();
      throw new P2PFacadeException("Could not start P2P Facade", e);
    }
  }

  public P2PFacade stop(){
    for(iP2PServer theServer : myProtocolServers){
      theServer.stop();
    }

    P2PSettings.getInstance().getSocketPool().cleanUp();
    return this;
  }
}
