/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SocketPool extends Observable{
  private List< SocketProxy > myCheckedInPool = Collections.synchronizedList( new ArrayList< SocketProxy >());
  private List< SocketProxy > myCheckedOutPool = Collections.synchronizedList( new ArrayList< SocketProxy >());
  private List< SocketProxy > myConnectingPool = Collections.synchronizedList( new ArrayList< SocketProxy >());

  private static SocketPool INSTANCE = null; 

  private ScheduledExecutorService myService = null;


  private SocketPool(){
  }

  private void notifyAllObs(){
    setChanged();
    notifyObservers();
  }

  public synchronized static SocketPool getInstance(){
    if(INSTANCE == null){
      INSTANCE = new SocketPool();
    }
    return INSTANCE;
  }

  public void setCleanUpTimeInSeconds(int aCleanUpTimeoutInSeconds){
    if(myService != null) myService.shutdownNow();
    if(aCleanUpTimeoutInSeconds > 0){
      myService = Executors.newScheduledThreadPool(1);
      myService.scheduleAtFixedRate( 
          new Runnable(){
            public void run(){
              cleanUp();
            }
          }, 
          aCleanUpTimeoutInSeconds, 
          aCleanUpTimeoutInSeconds, 
          TimeUnit.SECONDS);
    }
  }

  private SocketProxy searchFirstSocketWithAddressInPool(SocketAddress anAddress, List<SocketProxy> aPool){
    for(SocketProxy theSocket : aPool){
      if(theSocket.getSocketAddress().equals( anAddress )){
        return theSocket;
      }
    }
    return null;
  }

  public Socket checkOut(SocketAddress anAddress) throws IOException{
    SocketProxy theSocketProxy = searchFirstSocketWithAddressInPool( anAddress, myCheckedInPool);
    if(theSocketProxy != null){
      synchronized(this){
        myCheckedInPool.remove( theSocketProxy );
        myCheckedOutPool.add( theSocketProxy );
        return theSocketProxy.connect();
      }
    }
    
    theSocketProxy = searchFirstSocketWithAddressInPool( anAddress, myConnectingPool );
    if(theSocketProxy != null){
      //in this case some other thread also is trying to connect to the same address.  We will not allow to seperate threads
      //to try to connect to the same host at the same port as it will start consuming to much resources after a while
      //throw an exception
      throw new IOException("Another process already tries to contact this host at this port");
    }

    theSocketProxy = new SocketProxy(anAddress);
    myConnectingPool.add( theSocketProxy );
    notifyAllObs();
    try{
      Socket theSocket = theSocketProxy.connect( );
      myCheckedOutPool.add( theSocketProxy );
      return theSocket;
    } finally{
      myConnectingPool.remove( theSocketProxy );
      notifyAllObs();
    }
  }
  
  private SocketProxy searchProxyForSocket(Socket aSocket){
    for(SocketProxy theProxy : myCheckedOutPool){
      if(theProxy.getSocket() == aSocket){
        return theProxy;
      }
    }
    return null;
  }

  public void checkIn(Socket aSocket){
    if(aSocket != null){
      synchronized(this){
        SocketProxy theProxy = searchProxyForSocket( aSocket );
        myCheckedOutPool.remove( theProxy );
        myCheckedInPool.add(theProxy);
      }
      notifyAllObs();
    }
  }

  public synchronized void close(Socket aSocket){
    try {
      aSocket.close();
    } catch ( IOException e ) {
    }
    SocketProxy theProxy = searchProxyForSocket( aSocket );
    myCheckedInPool.remove( theProxy );
    myCheckedOutPool.remove( theProxy );
    notifyAllObs();
  }

  public synchronized void cleanUp(){
    for(SocketProxy theSocket : myCheckedInPool){
      try {
        theSocket.getSocket().close();
      } catch ( IOException e ) {
      }
    }
    myCheckedInPool.clear();
    notifyAllObs();
  }

  List< SocketProxy > getCheckInPool(){
    return Collections.unmodifiableList(  myCheckedInPool );
  }

  List< SocketProxy > getCheckOutPool(){
    return Collections.unmodifiableList(  myCheckedOutPool );
  }
  
  List< SocketProxy > getConnectingPool(){
    return Collections.unmodifiableList(  myConnectingPool);
  }
}
