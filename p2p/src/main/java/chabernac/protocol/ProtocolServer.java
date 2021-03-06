/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JPanel;

import org.apache.log4j.Logger;

import chabernac.io.StreamSplitter;
import chabernac.protocol.ServerInfo.Type;
import chabernac.thread.DynamicSizeExecutor;
import chabernac.util.concurrent.MonitorrableRunnable;
import chabernac.util.concurrent.iRunnableListener;
import chabernac.utils.NetTools;

public class ProtocolServer implements Runnable, iP2PServer{
  private static Logger LOGGER = Logger.getLogger(ProtocolServer.class);
  public static final String SESSION = "Session";
  
  //TODO define these properties somewhere else as they are being used in several contexts
  //including contexts not related to protocol server
  public static final String REMOTE_IP = "Socket";
  public static final String NETWORK_INTERFACE = "NetworkInterface";

  private int myPort;
  private IProtocol myProtocol = null;
  private boolean isStarted = false;
  private ServerSocket myServerSocket = null;
  private boolean isFindUnusedPort = false;
  private ServerInfo myServerInfo = new ServerInfo(Type.SOCKET);

  private Object LOCK = new Object();
  private AtomicLong mySimultanousThreads = new AtomicLong();
  private iRunnableListener myRunnableListener = null;
  private ExecutorService myClientHandlerService = null;
  private List< Socket > myRunningSockets = new ArrayList< Socket >();

  public ProtocolServer(IProtocol aProtocol, int aPort){
    this(aProtocol, aPort, false);
  }

  public ProtocolServer(IProtocol aProtocol, int aPort, boolean isFindUnusedPort){
    this.isFindUnusedPort = isFindUnusedPort;
    myProtocol = aProtocol;
    myPort = aPort;
  }

  public boolean start(){
    if(isStarted) return isStarted;
    synchronized(LOCK){
      new Thread(this).start();
      try{
        if(!isStarted) LOCK.wait();
      }catch(InterruptedException e){
        e.printStackTrace();
      }
    }
    return isStarted;
  }

  public void kill(){
    try {
      if(myServerSocket != null){
        myServerSocket.close();
        LOGGER.debug("Server socket at port " + myServerSocket.getLocalPort() + " closed");
      }
    } catch ( IOException e ) {
    }
    if(myClientHandlerService != null) myClientHandlerService.shutdownNow();
    for(Socket theSocket : new ArrayList< Socket >(myRunningSockets)){
      try {
        theSocket.close();
      } catch ( IOException e ) {
      }
    }
    myRunningSockets.clear();
  }

  public void stop(){
    kill();
    myProtocol.stop();

    int theCount = 5;
    while(isStarted && theCount-- > 0){
      synchronized ( LOCK ) {
        try {
          LOCK.wait(1000);
        } catch ( InterruptedException e ) {
        }
      }
    }
  }

  @Override
  public void run() {
    try{
      mySimultanousThreads.incrementAndGet();

      if(isFindUnusedPort){
        myServerSocket = NetTools.openServerSocket( myPort );
      } else {
        myServerSocket = new ServerSocket(myPort);
      }

      myServerInfo.setServerPort( myServerSocket.getLocalPort() );
      myProtocol.setServerInfo( myServerInfo );

      myClientHandlerService = new DynamicSizeExecutor( 10, 255, 0 );

      LOGGER.debug( "Starting protocol server at port '" + myServerSocket.getLocalPort() + "'" );

      synchronized ( LOCK ) {
        isStarted = true;
        LOCK.notify();
      }

      while(true){ 
        synchronized(this){
          Socket theClientSocket = myServerSocket.accept();
          LOGGER.debug("Client accepted, current number of clients: " + mySimultanousThreads.get());

//          killOldestSocket();
          myRunningSockets.add( theClientSocket );

          ClientSocketHandler theHandler = new ClientSocketHandler(theClientSocket);
          if(myRunnableListener != null){
            theHandler.addListener( myRunnableListener );
          }
          myClientHandlerService.execute( theHandler );
        }
      }
    }catch(SocketException e){
      if(!"socket closed".equalsIgnoreCase( e.getMessage())){
        LOGGER.error("Could not start server", e);
      }
    }catch(Exception e){
      LOGGER.error("Could not start server", e);
    } finally {
      synchronized ( LOCK ) {
        isStarted = false;
        LOCK.notify();
      }
      mySimultanousThreads.decrementAndGet();
    }
  }

  public boolean isStarted(){
    return isStarted;
  }

  public iRunnableListener getRunnableListener() {
    return myRunnableListener;
  }

  public void setRunnableListener( iRunnableListener anRunnableListener ) {
    myRunnableListener = anRunnableListener;
  }


  private class ClientSocketHandler extends MonitorrableRunnable{
    private final Socket mySocket;
    
    public ClientSocketHandler(Socket aSocket){
      mySocket = aSocket;
    }

    public void doRun(){
      BufferedReader theReader = null;
      PrintWriter theWriter = null;
      try{
        LOGGER.debug( "Incoming socket on ip'" + mySocket.getLocalAddress()  + "'");
        theReader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
        theWriter = new PrintWriter(new OutputStreamWriter(mySocket.getOutputStream()));

        String theLine = null;
        while( (theLine = theReader.readLine()) != null){
          //          LOGGER.debug("Line received: '" + theLine + "'");
          //the following line is just to ease the transition to using the stream splitter
          if(theLine.startsWith( StreamSplitter.IN )) theLine = theLine.substring( StreamSplitter.IN.length() );
          String theSession = UUID.randomUUID().toString();
          
          //untill we have found a better way to detect the netmask of the remote ip we will assume both hosts are on the same network
          //and thus have the same netmask.  so we use the local netmask and apply it to the remote ip
//          int theNetworkPrefixLength = IPAddress.getIPAddressForLocalIP(mySocket.getLocalAddress().getLocalHost().getHostAddress()).getNetworkPrefixLength();
//          myProtocol.getSessionData().putProperty( theSession, REMOTE_IP, mySocket.getInetAddress().getHostAddress()  + "/" + theNetworkPrefixLength);
          myProtocol.getSessionData().putProperty( theSession, NETWORK_INTERFACE, NetTools.getNetworkInterfaceForLocalIP( mySocket.getLocalAddress().getHostAddress() ));
          String  theResult = myProtocol.handleCommand(theSession, theLine );
          //          LOGGER.debug("Sending result: '" + theResult + "'");
          myProtocol.getSessionData().clearSessionData( theSession );
          theWriter.println( theResult );
          theWriter.flush();
        }
      }catch(SocketException e){
        //if the socket was closed by the remote client we should not log anything this is normal
        if(!e.getMessage().equalsIgnoreCase("socket closed")) {
          LOGGER.error("Socket exception occured " + e.toString(), e);
        }
      }catch(Throwable e){
        LOGGER.error( "Io exception occured in protocol server", e );
      } finally {
        try{
          myRunningSockets.remove( mySocket );
        }catch(Throwable e){
          LOGGER.error( "Error occured while removing socket from running sockets pool", e );
        }
        try{
          theWriter.flush();
          theWriter.close();
          theReader.close();
        }catch(IOException e){
          LOGGER.error( "Could not close streams", e );
        }
        try {
          mySocket.close();
        } catch ( IOException e ) {
          LOGGER.error( "Could not close socket", e );
        }
      }
    }

    @Override
    protected String getExtraInfo() {
      return mySocket.getInetAddress().getHostName() + ":" + mySocket.getPort();
    }
  }
  
  @Override
  public JPanel getDebuggingPanel() {
    return null;
  }
}
