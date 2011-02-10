/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.routing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.activation.DataSource;

import org.apache.log4j.Logger;
import org.doomdark.uuid.UUIDGenerator;

import chabernac.io.Base64ObjectStringConverter;
import chabernac.io.ClassPathResource;
import chabernac.io.iObjectPersister;
import chabernac.io.iObjectStringConverter;
import chabernac.protocol.AlreadyRunningException;
import chabernac.protocol.DynamicSizeExecutor;
import chabernac.protocol.Protocol;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.ServerInfo;
import chabernac.protocol.ServerInfo.Type;
import chabernac.tools.IOTools;
import chabernac.tools.NetTools;
import chabernac.tools.SimpleNetworkInterface;
import chabernac.tools.TestTools;

/**
 *  the routing protocol will do the following
 *  
 *  - send it's own routing table to all known peers in the routing table
 *  - update the routing table with the items received from another peer.  in this process the fasted path to a peer must be stored in the routing table
 *  - periodically contact all peers to see if they are still online and retrieve the routing table of the other peer.
 *  
 */

public class RoutingProtocol extends Protocol {
  public static String ID = "ROU";

  private static Logger LOGGER = Logger.getLogger( RoutingProtocol.class );

  static{
    if(TestTools.isInUnitTest()){
      START_PORT = 12800;
      END_PORT = 12808;
    } else {
      START_PORT = 12700;
      END_PORT = 12708;
    }
  }

  public static int START_PORT;
  public static int END_PORT;
  public static final int MULTICAST_PORT = 13879;
  public static final String MULTICAST_ADDRESS = "234.5.54.9";

  public static enum Command { REQUEST_TABLE, WHO_ARE_YOU, ANNOUNCEMENT_WITH_REPLY, ANNOUNCEMENT };
  public static enum Response { OK, NOK, UNKNOWN_COMMAND };

  private RoutingTable myRoutingTable = null;

  private long myExchangeDelay;

  //this counter has just been added for unit testing reasons
  private AtomicLong myExchangeCounter = new AtomicLong(0);

  //this list is for test reasons to simulate peers which can not reach each other locally 
  private List<String> myUnreachablePeers = new ArrayList< String >();

  //this list is for test reasons to simulate peers which can not reach each other remotely
  //  private List<String> myRemoteUnreachablePeers = new ArrayList< String >();

  private ScheduledExecutorService mySheduledService = null;

  private iObjectPersister< RoutingTable > myRoutingTablePersister = new RoutingTableObjectPersister();

  private boolean isPersistRoutingTable = false;
  private boolean isStopWhenAlreadyRunning = false;

  private ExecutorService myChangeService = null;

  private String myLocalPeerId = null;

  private ExecutorService myUDPPacketHandlerService = DynamicSizeExecutor.getSmallInstance();
  private ExecutorService myScannerService = Executors.newCachedThreadPool( );

  private MulticastSocket myServerMulticastSocket = null;

  private IRoutingProtocolMonitor myRoutingProtocolMonitor = null;

  private boolean isPeerIdInFile = false;

  private DataSource mySuperNodesDataSource = new ClassPathResource("supernodes.txt");

  private ServerInfo myServerInfo = null;
  private final String myChannel;

  private final iObjectStringConverter< RoutingTable > myRoutingTableConverter = new Base64ObjectStringConverter< RoutingTable >();
  private final iObjectStringConverter< RoutingTableEntry > myRoutingTableEntryConverter = new Base64ObjectStringConverter< RoutingTableEntry >();
  private final iObjectStringConverter< AbstractPeer> myPeerConverter = new Base64ObjectStringConverter< AbstractPeer >();

  private final iPeerSender myPeerSender;

  /**
   * 
   * @param aLocalPeerId
   * @param aRoutingTable
   * @param anExchangeDelay the delay in seconds between exchaning routing tables with other peers
   */
  public RoutingProtocol ( String aLocalPeerId, 
                           long anExchangeDelay, 
                           boolean isPersistRoutingTable, 
                           DataSource aSuperNodesDataSource, 
                           boolean isStopWhenAlreadyRunning, 
                           String aChannel,
                           iPeerSender aPeerSender) throws ProtocolException{
    super( ID );
    myLocalPeerId = aLocalPeerId;
    myExchangeDelay = anExchangeDelay;
    myChannel = aChannel;
    myPeerSender = aPeerSender;
    this.isPersistRoutingTable = isPersistRoutingTable;
    this.isStopWhenAlreadyRunning = isStopWhenAlreadyRunning;

    if(myLocalPeerId != null && !"".equals( myLocalPeerId )){
      isPeerIdInFile = true;
    } else {
      isPeerIdInFile = false;
    }

    if(aSuperNodesDataSource != null){
      mySuperNodesDataSource = aSuperNodesDataSource;
    }

    loadRoutingTable();
  }

  public void start() throws ProtocolException{
    if(isStopWhenAlreadyRunning){
      try {
        AbstractPeer theLocalPeer = myRoutingTable.getEntryForLocalPeer().getPeer();
        //only do the check if the our port is not the same as the port from the routing table
        //if it is than we would check if we our running ourselfs, which is at this point stupid
        if(theLocalPeer instanceof SocketPeer && 
            myServerInfo != null && 
            myServerInfo.getServerPort() != ((SocketPeer)theLocalPeer).getPort() && 
            isAlreadyRunning(theLocalPeer)){
          throw new AlreadyRunningException(theLocalPeer);
        }

      } catch (UnknownPeerException e) {
        LOGGER.error("Could not get entry for local peer");
      }
    }

    resetRoutingTable();

    //add the entry for the local peer based on the server info
    if(myServerInfo != null){
      AbstractPeer theLocalPeer = null;
      if(myServerInfo.getServerType() == Type.SOCKET){
        try{
          theLocalPeer = new SocketPeer(getLocalPeerId(), myServerInfo.getServerPort());
        }catch(NoAvailableNetworkAdapterException e){
          //TODO we should do something when the network adapter becomes available again
          LOGGER.error( "The local network adapter could not be located", e );
        }
      } else if(myServerInfo.getServerType() == Type.WEB){
        try {
          theLocalPeer = new WebPeer(getLocalPeerId(), new URL(myServerInfo.getServerURL()));
        } catch ( MalformedURLException e ) {
          LOGGER.error( "The local url is invalid", e);
        }
      }
      if(theLocalPeer == null){
        throw new ProtocolException("The local peer could not be created");
      }

      if(findProtocolContainer().getSupportedProtocols() != null){
        for(String theProtocol : findProtocolContainer().getSupportedProtocols()){
          theLocalPeer.addSupportedProtocol( theProtocol );
        }
      }

      theLocalPeer.setChannel(myChannel);

      RoutingTableEntry theLocalRoutingTableEntry = new RoutingTableEntry(theLocalPeer, 0, theLocalPeer, System.currentTimeMillis());
      myRoutingTable.addRoutingTableEntry( theLocalRoutingTableEntry );
    }

    if(myServerInfo != null && myServerInfo.getServerType() == Type.SOCKET){
      if(myExchangeDelay > 0 ) scheduleRoutingTableExchange();
      myChangeService = DynamicSizeExecutor.getSmallInstance();

      myRoutingTable.addRoutingTableListener( new RoutingTableListener() );
      startUDPListener();
      saveRoutingTable();
    }
  }

  /**
   * This method will try to contact a routing protocol that might be running at the port indicated in the routing table file
   * if there is a routing protocol already running at this port then return true
   */
  private boolean isAlreadyRunning(AbstractPeer aPeer) {
    LOGGER.debug("Checking if we're already running");
    return contactPeer(aPeer, myUnreachablePeers);
  }

  private void startUDPListener(){
    myUDPPacketHandlerService.execute( new MulticastServerThread() );
  }


  private void scheduleRoutingTableExchange(){
    mySheduledService = Executors.newScheduledThreadPool( 1 );
    mySheduledService.scheduleWithFixedDelay( new ScanLocalSystem(), 1, myExchangeDelay, TimeUnit.SECONDS);
    mySheduledService.scheduleWithFixedDelay( new ExchangeRoutingTable(), 2, myExchangeDelay, TimeUnit.SECONDS);
    mySheduledService.scheduleWithFixedDelay( new SendUDPAnnouncement(), 4, myExchangeDelay, TimeUnit.SECONDS);
    mySheduledService.scheduleWithFixedDelay( new ScanFixedIpList(), 10, myExchangeDelay, TimeUnit.SECONDS);
    mySheduledService.scheduleWithFixedDelay( new ScanRemoteSystem(), 20 , 4 * myExchangeDelay, TimeUnit.SECONDS);
    mySheduledService.scheduleWithFixedDelay( new DetectRemoteSystem(), 100, 10 * myExchangeDelay, TimeUnit.SECONDS);
  }

  @Override
  public String getDescription() {
    return "Routing protocol";
  }

  public List< String > getLocalUnreachablePeerIds() {
    return myUnreachablePeers;
  }

  //  public List< String > getRemoteUnreachablePeerIds() {
  //    return myRemoteUnreachablePeers;
  //  }

  public String getLocalPeerId(){
    if(myLocalPeerId != null && !"".equals( myLocalPeerId )){
      return myLocalPeerId;
    } else if(myRoutingTable != null){
      return myRoutingTable.getLocalPeerId();
    } else {
      myLocalPeerId = UUIDGenerator.getInstance().generateTimeBasedUUID().toString();
      return myLocalPeerId;
    }
  }

  @Override
  public String handleCommand( long aSessionId, String anInput ) {
    int theFirstIndexOfSpace = anInput.indexOf( " " );
    if(theFirstIndexOfSpace == -1) theFirstIndexOfSpace = anInput.length();
    String theCommandString = anInput.substring( 0,  theFirstIndexOfSpace);

    Command theCommand = Command.valueOf( theCommandString );
    try{
      if(theCommand == Command.REQUEST_TABLE){
        //another peer has send a request for the routing protocol send it
        return myRoutingTableConverter.toString( myRoutingTable );
      } else if(theCommand == Command.WHO_ARE_YOU){
        //another peer requested my peer id, send it to him, this is also used
        //to check if I'm still alive and kicking
        try{
          RoutingTableEntry theEntryForLocalPeer = myRoutingTable.getEntryForLocalPeer();
          return myPeerConverter.toString(theEntryForLocalPeer.getPeer());
        }catch(Exception e){
          LOGGER.error( "Could not obtain entry for local peer", e );
          return Response.NOK.name();
        }
      } else if(theCommand == Command.ANNOUNCEMENT_WITH_REPLY){
        //the announcement is of the peer which is sending the annoucement
        //so the peer id inside the routingtable entry is also the containing peer
        String thePeerEntry = anInput.substring( theFirstIndexOfSpace + 1 );
        RoutingTableEntry theEntry = myRoutingTableEntryConverter.getObject( thePeerEntry ).incHopDistance();
        myRoutingTable.addRoutingTableEntry( theEntry);

        //before sending our routing table, let's verify if we can still reach our neighbours
        //this is just to avoid exchanging wrong information
//        verifyNeighbours();

//        return myRoutingTableConverter.toString( myRoutingTable.copyWithoutUnreachablePeers() );
        return myRoutingTableConverter.toString(myRoutingTable);
      } else if(theCommand == Command.ANNOUNCEMENT){
        String[] theAttributes = anInput.substring( theFirstIndexOfSpace + 1 ).split(";");

        RoutingTableEntry theSendingPeer = myRoutingTableEntryConverter.getObject( theAttributes[0] );
        //we add the sending peer to the routing table
        //      myRoutingTable.addRoutingTableEntry(theSendingPeer.incHopDistance());

        RoutingTableEntry thePeer = myRoutingTableEntryConverter.getObject( theAttributes[1] );

        //the sending peer has send the entry so we set it as gateway and increment the hop distance
        //if the gateway of the entry was our peer id  than we ignore the entry, otherwise loops might be created in the routing table hierarchy
        if(!thePeer.getGateway().getPeerId().equals( myLocalPeerId )){
          thePeer = thePeer.entryForNextPeer( theSendingPeer.getPeer() );
          myRoutingTable.addRoutingTableEntry( thePeer );
        }
        return Response.OK.name();
      }
    }catch(IOException e){
      LOGGER.error("An error occured while parsing Routing table or entries to string or visa verca", e);
      return Response.NOK.name();
    }
    return Response.UNKNOWN_COMMAND.name();
  }

//  private void verifyNeighbours(){
//    for(RoutingTableEntry theEntry : myRoutingTable.getEntries()){
//      if(theEntry.getHopDistance() == 1){
//        //verify if we can reach this peer
//        if(!contactPeer( theEntry.getPeer(), myUnreachablePeers)){
//          RoutingTableEntry theNewEntry = theEntry.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE );
//          myRoutingTable.addRoutingTableEntry( theNewEntry );
//        }
//      }
//    }
//  }

  public RoutingTable getRoutingTable(){
    return myRoutingTable;
  }

  public void checkPeer(final AbstractPeer aPeer){
    if(!myRoutingTable.containsEntryForPeer( aPeer.getPeerId() )){
      myScannerService.execute( new Runnable(){
        public void run(){
          contactPeer( aPeer, myUnreachablePeers );
        }
      });
    }
  }

  boolean contactPeer(AbstractPeer aPeer, List<String> anUnreachablePeers){
    try{
      LOGGER.debug("Sending message to '" + aPeer.getEndPointRepresentation() );
      aPeer.setPeerSender(myPeerSender);
      String theResponse = aPeer.send( createMessage( Command.WHO_ARE_YOU.name() ));
      AbstractPeer theRemotePeer = myPeerConverter.getObject( theResponse );

      if(anUnreachablePeers == null || !anUnreachablePeers.contains( theRemotePeer.getPeerId() )){
        RoutingTableEntry theEntry = new RoutingTableEntry(theRemotePeer, 1, theRemotePeer, System.currentTimeMillis());

        LOGGER.debug("Detected system on '" + theRemotePeer.getEndPointRepresentation());
        //only if we have detected our self we set the hop distance to 0
        if(theRemotePeer.getPeerId().equals(myRoutingTable.getLocalPeerId())){
          theEntry = theEntry.derivedEntry( 0 );
        }
        myRoutingTable.addRoutingTableEntry( theEntry );
        return true;
      }
    }catch(Exception e){
      //TODO remove extensive logging
      //      try {
      //        if(aPeer.getPort() == RoutingProtocol.START_PORT && aPeer.getHosts().get( 0 ).equalsIgnoreCase( InetAddress.getLocalHost().getHostAddress() )){
//      LOGGER.error( "Error occured while contacting peer '" + aPeer.getPeerId() + "' " + aPeer.getHosts() + ": " + aPeer.getPort() );
      //        }
      //      } catch ( UnknownHostException e1 ) {
      //        e1.printStackTrace();
      //      }
      return false;
    }
    return false;
  }

  public void scanLocalSystem(){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.localSystemScanStarted();
    try{
      LOGGER.debug( "Scanning local system" );
      List<String> theLocalHosts = NetTools.getLocalExposedIpAddresses();
      for(int i=START_PORT;i<=END_PORT;i++){
        myScannerService.execute( new ScanSystem(this, theLocalHosts, i, myUnreachablePeers));
      }
    }catch(SocketException e){
      LOGGER.error( "Could not get local ip addressed", e );
    }  
  }

  /**
   * this method will scan the routing table and find hosts which are unreachable
   * for this hosts a port scan will be started to detect if the peer is not online
   * on a different port, if one is found, the port scan stops
   */
  public void scanRemoteSystem(boolean isExcludeLocal){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.remoteSystemScanStarted();
    //first search all hosts which have no single peer
    Map<SimpleNetworkInterface, Boolean> theHosts = new HashMap< SimpleNetworkInterface, Boolean >();

    for(RoutingTableEntry theEntry : myRoutingTable){
      if(isExcludeLocal && !theEntry.getPeer().getPeerId().equals(myRoutingTable.getLocalPeerId()) && theEntry.getPeer() instanceof SocketPeer){
        for(SimpleNetworkInterface theHost : ((SocketPeer)theEntry.getPeer()).getHosts()){
          boolean isReachable = false;
          if(theHosts.containsKey( theHost )){
            isReachable = theHosts.get(theHost);
          }
          theHosts.put( theHost, isReachable | theEntry.isReachable() );
        }
      }
    }

    //now try to scan all hosts which are not reachable
    for(SimpleNetworkInterface theHost : theHosts.keySet()){
      if(!theHosts.get(theHost)){
        //this host is not reachable, scan it
        boolean isContacted = false;
        for(int i=START_PORT;i<=END_PORT && !isContacted;i++){
          try{
            if(!isExcludeLocal || i!=((SocketPeer)myRoutingTable.getEntryForLocalPeer().getPeer()).getPort()){
              LOGGER.debug("Scanning the following host: '" + theHost + "' on port '" + i + "'");
              isContacted = contactPeer( new SocketPeer("", theHost, i), myUnreachablePeers );
            }
          }catch(Exception e){}
        }
      }
    }
  }

  /**
   * this function will look at the local ip adres and start port scanning in the same range for other peers
   * we only do this if there are no ohter peers in the network but our selfs
   */
  public void detectRemoteSystem(){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.detectingRemoteSystemStarted();
    if(myRoutingTable.getNrOfReachablePeers() <= 1){
      try{
        InetAddresIterator theIterator = new InetAddresIterator(InetAddress.getLocalHost(), 24);
        while(myRoutingTable.getNrOfReachablePeers() <= 1 && theIterator.hasNext()){
          ScanSystem theScanSystem = new ScanSystem(this, theIterator.next(), START_PORT);
          theScanSystem.setCondition( new NrOfPeersSmallerThenCondition(myRoutingTable, 1) );
          myScannerService.execute( theScanSystem );
        }
      }catch(Exception e ){
        LOGGER.error( "An error occured while scanning system", e );
      }
    }
  }

  private class ScanLocalSystem implements Runnable{
    public void run(){
      scanLocalSystem();
    }
  }

  private class ScanRemoteSystem implements Runnable{
    public void run(){
      scanRemoteSystem(false);
    }
  }

  public void scanSuperNodes(){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.scanningSuperNodes();
    try{
      List<String> theIps = IOTools.loadStreamAsList( mySuperNodesDataSource.getInputStream() );
      for(String theIp : theIps){
        if(!hostHasActivePeer( theIp )){
          if(theIp.startsWith("http:")){
            myScannerService.execute( new ScanWebSystem(RoutingProtocol.this, new URL(theIp)));
          } else {
            myScannerService.execute( new ScanSystem(RoutingProtocol.this, theIp, START_PORT, myUnreachablePeers));
          }
        }
      }
    }catch(IOException e){
      LOGGER.error("An error occured while scanning super nodes", e);
    }
  }
  
  private boolean hostHasActivePeer(String anIp){
    for(RoutingTableEntry theEntry : getRoutingTable()){
      if(theEntry.getPeer().getEndPointRepresentation().contains( anIp ) && theEntry.isResponding()){
        return true;
      }
    }
    return false;
  }

  /**
   * this method will send a request to all the peers in the routing table
   */
  public void exchangeRoutingTable(){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.exchangingRoutingTables();
    LOGGER.debug("Exchanging routing table for peer: " + myRoutingTable.getLocalPeerId());

    for(RoutingTableEntry theEntry : myRoutingTable.getEntries()){
      AbstractPeer thePeer = theEntry.getPeer();
      thePeer.setPeerSender(myPeerSender);

      //simulate that we can not contact this peer directly, but we can contact it trough a gateway
      //so check if hop distance = 1


      if(!thePeer.getPeerId().equals(myRoutingTable.getLocalPeerId())){
        try {
          if(myUnreachablePeers.contains( thePeer.getPeerId())){
            //simulate that we cannot contact the peer
            throw new Exception("Simulate that we can not contact peer: " + thePeer.getPeerId());
          }
          String theCMD = createMessage( Command.ANNOUNCEMENT_WITH_REPLY.name() + " "  + myRoutingTableEntryConverter.toString( myRoutingTable.getEntryForLocalPeer() ));
          String theTable = thePeer.send( theCMD ) ;
          //          String theTable = thePeer.send( createMessage( Command.REQUEST_TABLE.name() ));
          RoutingTable theRemoteTable = myRoutingTableConverter.getObject( theTable );

          if(!theRemoteTable.getLocalPeerId().equals( thePeer.getPeerId() )){
            //if we get here it means that another peer has taken the place of the previous peer,
            //i.e. it is running on the same host and port
            //this means that the peer is not reachable any more
            RoutingTableEntry theOldEntry = theEntry.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE );
            myRoutingTable.removeRoutingTableEntry( theOldEntry );
          }

          //test that we did not take the place of another peer on the same host and port
          if(!myLocalPeerId.equals( theRemoteTable.getLocalPeerId() )){

            myRoutingTable.merge( theRemoteTable );
            //we can connect directly to this peer, so the hop distance is 1
            //theEntry.setHopDistance( 1 );
            RoutingTableEntry theEntryOfRemotePeer = myRoutingTable.getEntryForPeer( theRemoteTable.getLocalPeerId() );

            myRoutingTable.addRoutingTableEntry( theEntryOfRemotePeer.derivedEntry( 1 ) );
          }
        } catch ( Exception e ) {
          LOGGER.error( "Could not contact peer '" + thePeer.getPeerId() + "'", e );
          //update all peers which have this peer as gateway to the max hop distance
          for(RoutingTableEntry theEntry2 : myRoutingTable.getEntries()){
            if(theEntry2.getGateway().getPeerId().equals( theEntry.getPeer().getPeerId())){
              //              theEntry2.setHopDistance( RoutingTableEntry.MAX_HOP_DISTANCE );
              myRoutingTable.addRoutingTableEntry( theEntry2.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE ) );
            }
          }
        }
      }
    }
    myExchangeCounter.incrementAndGet();
    LOGGER.debug("End exchanging routing table for peer: " + myRoutingTable.getLocalPeerId());

    //save the routing table
    if(isPersistRoutingTable) saveRoutingTable();
  }

  private void sendAnnoucement( RoutingTableEntry anEntry ) {
    try{
      if(myRoutingTable.getEntryForLocalPeer() == null){
        //we're not able to send announcment yet because we have not detected our selfs
        return;
      }
    }catch(Exception e){
      LOGGER.error("Could not get entry for local peer", e);
    }

    for(RoutingTableEntry theEntry : myRoutingTable.getEntries()){
      AbstractPeer thePeer = theEntry.getPeer();
      thePeer.setPeerSender(myPeerSender);

      //do not send the entry to our selfs, we already have the entry
      if(!thePeer.getPeerId().equals( myRoutingTable.getLocalPeerId()) &&
          //only send announcements to our neighbours, this means no peers with a hop distance > 1
          theEntry.getHopDistance() <= 1 &&
          //also do not send the entry to the peer from which the entry is coming.
          !theEntry.getPeer().getPeerId().equals( anEntry.getPeer().getPeerId() ) &&
          //do not send announcement to peers we cannot reach in test mode
          !myUnreachablePeers.contains(thePeer.getPeerId())){
        try {
          LOGGER.debug("Sending announcement of peer '" + anEntry.getPeer().getPeerId() +  "' from peer '" + myLocalPeerId +  "' to peer '" + thePeer.getPeerId() + "' on '" + thePeer.getEndPointRepresentation() + "'");
          String theResult = thePeer.send( createMessage( Command.ANNOUNCEMENT.name() + " "  + myRoutingTableEntryConverter.toString( myRoutingTable.getEntryForLocalPeer()) + ";" + myRoutingTableEntryConverter.toString( anEntry ))) ;
          if(!Response.OK.name().equals( theResult )){
            throw new Exception("Unexpected result code '" + theResult + "'");
          }
        } catch ( Exception e ) {
          //the peer can not be reached 
          //update all peers which have this peer as gateway to the max hop distance
          for(RoutingTableEntry theEntry2 : myRoutingTable.getEntries()){
            if(theEntry2.getGateway().getPeerId().equals( theEntry.getPeer().getPeerId())){
              //              theEntry2.setHopDistance( RoutingTableEntry.MAX_HOP_DISTANCE );
              myRoutingTable.addRoutingTableEntry( theEntry2.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE ) );
            }
          }
        }
      }
    }

  }

  public long getExchangeCounter(){
    return myExchangeCounter.longValue();
  }

  private class ExchangeRoutingTable implements Runnable{

    @Override
    public void run() {
      exchangeRoutingTable();
    }
  }

  private File getRoutingTableLocation(){
    String theFile = "RoutingTable";
    if(isPeerIdInFile){
      theFile += "_" + myLocalPeerId;
    }
    theFile += ".bin";
    return new File(theFile);
  }

  private void loadRoutingTable(){
    if(isPersistRoutingTable && getRoutingTableLocation().exists()){
      File theFile = getRoutingTableLocation();
      try{
        FileInputStream theInputStream = new FileInputStream(theFile);
        myRoutingTable = myRoutingTablePersister.loadObject( theInputStream );
        theInputStream.close();
        return;
      }catch(Exception e){
        LOGGER.error( "Could not load routing table", e );
      }
    } 

    myRoutingTable = new RoutingTable(getLocalPeerId());
  }

  public void resetRoutingTable(){
    for(RoutingTableEntry theEntry : myRoutingTable.getEntries()){
      myRoutingTable.addRoutingTableEntry( theEntry.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE ) );
    }
  }

  private void saveRoutingTable(){
    File theFile = getRoutingTableLocation();
    try{
      FileOutputStream theStream = new FileOutputStream(theFile);
      myRoutingTablePersister.persistObject( myRoutingTable, theStream );
      theStream.flush();
      theStream.close();
    }catch(Exception e){
      LOGGER.error("Unable to save routing table", e);
    }
  }

  @Override
  public void stop() {
    //remove all listeners from the routing table
    myRoutingTable.removeAllRoutingTableListeners();

    if(myScannerService != null){
      myScannerService.shutdownNow();
    }

    if(mySheduledService != null){
      mySheduledService.shutdownNow();
    }

    if(myChangeService != null) {
      myChangeService.shutdownNow();
    }

    if(myUDPPacketHandlerService != null){
      myUDPPacketHandlerService.shutdownNow();
    }

    if(isPersistRoutingTable) saveRoutingTable();

    if(myServerMulticastSocket != null){
      myServerMulticastSocket.close();
    }

    //announce that we leave the P2P network
    try{
      RoutingTableEntry theSelfRoutingTableEntry = getRoutingTable().getEntryForLocalPeer();
      if(theSelfRoutingTableEntry != null){
        theSelfRoutingTableEntry = theSelfRoutingTableEntry.derivedEntry( RoutingTableEntry.MAX_HOP_DISTANCE );
        sendAnnoucement( theSelfRoutingTableEntry );
      }
    } catch ( UnknownPeerException e ) {
    }
  }

  private class RoutingTableListener implements IRoutingTableListener{
    @Override
    public void routingTableEntryChanged( RoutingTableEntry anEntry ) {
      //make sure all peers in the routing table have the same peersender
      anEntry.getPeer().setPeerSender( myPeerSender );
      myChangeService.execute( new SendAnnouncement(anEntry) );
    }

    @Override
    public void routingTableEntryRemoved( RoutingTableEntry anEntry ) {
      //TODO should we do something here?
    }
  }

  private class SendAnnouncement implements Runnable{
    private RoutingTableEntry myEntry = null;

    public SendAnnouncement(RoutingTableEntry anEntry){
      myEntry = anEntry;
    }

    public void run(){
      sendAnnoucement(myEntry); 
    }
  }

  public static boolean isInPortRange(int aPort){
    if(aPort < START_PORT) return false;
    if(aPort > END_PORT) return false;
    return true;
  }

  private class MulticastServerThread implements Runnable{
    @Override
    public void run() {
      try{
        myServerMulticastSocket = new MulticastSocket(MULTICAST_PORT);
        InetAddress theGroup = InetAddress.getByName(MULTICAST_ADDRESS);
        myServerMulticastSocket.joinGroup(theGroup);

        while(!myServerMulticastSocket.isClosed()){
          byte[] theBytes = new byte[1024];
          DatagramPacket thePacket = new DatagramPacket(theBytes, theBytes.length);
          myServerMulticastSocket.receive( thePacket );
          ObjectInputStream theObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(theBytes));
          Object theObject = theObjectInputStream.readObject();
          if(theObject instanceof RoutingTableEntry){
            RoutingTableEntry theEntry = (RoutingTableEntry)theObject;
            if(!myUnreachablePeers.contains(theEntry.getPeer().getPeerId()) && isInPortRange( ((SocketPeer)theEntry.getPeer()).getPort() )){
              if(!theEntry.getPeer().getPeerId().equals( myLocalPeerId )){
                theEntry = theEntry.incHopDistance();
              }

              myRoutingTable.addRoutingTableEntry( theEntry );
            }
          }
        }
      }catch(SocketException e){
        if(!"socket closed".equalsIgnoreCase( e.getMessage())){
          LOGGER.error("Could not start udp server", e);
        }
      }catch(Exception e){
        LOGGER.error("Could not start udp server", e);
      }
    }
  }

  public void sendUDPAnnouncement(){
    if(myRoutingProtocolMonitor != null) myRoutingProtocolMonitor.sendingUDPAnnouncement();
    try{
      ByteArrayOutputStream theByteArrayOutputStream = new ByteArrayOutputStream();
      ObjectOutputStream theObjectOutputStream = new ObjectOutputStream(theByteArrayOutputStream);
      theObjectOutputStream.writeObject( myRoutingTable.getEntryForLocalPeer() );

      MulticastSocket theMulticastSocket = new MulticastSocket(MULTICAST_PORT);
      InetAddress theGroup = InetAddress.getByName(MULTICAST_ADDRESS);
      theMulticastSocket.joinGroup(theGroup);

      byte[] theBytes = theByteArrayOutputStream.toByteArray();
      DatagramPacket thePacket = new DatagramPacket(theBytes, theBytes.length, theGroup, MULTICAST_PORT);
      thePacket.setAddress( theGroup );

      theMulticastSocket.send( thePacket );
    }catch(Exception e){
      LOGGER.error( "Could not send datagram packet", e);
    }
  }

  private class SendUDPAnnouncement implements Runnable{

    @Override
    public void run() {
      sendUDPAnnouncement();
    }
  } 

  private class DetectRemoteSystem implements Runnable{

    @Override
    public void run() {
      detectRemoteSystem();
    }
  }

  public IRoutingProtocolMonitor getRoutingProtocolMonitor() {
    return myRoutingProtocolMonitor;
  }

  public void setRoutingProtocolMonitor( IRoutingProtocolMonitor anRoutingProtocolMonitor ) {
    myRoutingProtocolMonitor = anRoutingProtocolMonitor;
  }

  public class ScanFixedIpList implements Runnable {

    @Override
    public void run() {
      scanSuperNodes();
    }
  }

  @Override
  public void setServerInfo( ServerInfo aServerInfo ) throws ProtocolException {
    myServerInfo = aServerInfo;
    start();
  }
  
  public iPeerSender getPeerSender() {
    return myPeerSender;
  }
}
