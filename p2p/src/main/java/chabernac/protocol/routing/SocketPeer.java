/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.routing;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import chabernac.io.SocketProxy;
import chabernac.io.iSocketPool;
import chabernac.p2p.settings.P2PSettings;
import chabernac.tools.SimpleNetworkInterface;
import chabernac.utils.IPAddress;
import chabernac.utils.NetTools;

public class SocketPeer extends AbstractPeer implements Serializable {
    private static Logger LOGGER = Logger.getLogger(SocketPeer.class);
    private static final long serialVersionUID = 7852961137229337616L;
    private List<SimpleNetworkInterface> myHost = null;
    private int myPort;
    public static enum StreamSplitterSupport { TRUE, FALSE, UNKNOWN };
    private StreamSplitterSupport myStreamSplittingSupported = StreamSplitterSupport.TRUE;

    public SocketPeer (){
        super(null);
    }

    public SocketPeer(SocketPeer aSocketPeer, List<SimpleNetworkInterface> aHosts){
        super(aSocketPeer.getPeerId());
        setChannel(aSocketPeer.getChannel());
        setTemporaryPeer(aSocketPeer.isTemporaryPeer());
        setTestPeer( aSocketPeer.isTestPeer() );
        mySupportedProtocols.clear();
        mySupportedProtocols.addAll(aSocketPeer.getSupportedProtocols());
        myHost = aHosts;
        myPort = aSocketPeer.getPort();

    }

    public SocketPeer(String aPeerId, int aPort) throws NoAvailableNetworkAdapterException{
        super(aPeerId);
        myPort = aPort;
        detectLocalInterfaces();
    }

    public SocketPeer(String aPeerId,  int aPort, List<SimpleNetworkInterface> aHosts){
        super(aPeerId);
        myHost = aHosts;
        myPort = aPort;
    }

    public SocketPeer(String aPeerId, SimpleNetworkInterface aHost, int aPort){
        super(aPeerId);
        if(myHost == null){
            myHost = new ArrayList<SimpleNetworkInterface>();
        }
        myHost.add(aHost);
        myPort = aPort;
    }

    public SocketPeer (String anPeerId ) {
        super(anPeerId);
    }

    public SocketPeer ( String aPeerId , int aPort, String... aHosts ) {
        this(aPeerId, SimpleNetworkInterface.createFromIpList(null, aHosts ), aPort);
    }

    public synchronized void detectLocalInterfaces() throws NoAvailableNetworkAdapterException{
        try {
            myHost = NetTools.getLocalExposedInterfaces();
        } catch ( SocketException e ) {
            throw new NoAvailableNetworkAdapterException("Could not detect local network adapter", e);
        }

        if(myHost.size() == 0){
            try{
                SimpleNetworkInterface theLoopBackInterface = NetTools.getLoopBackInterface();
                myHost = new ArrayList<SimpleNetworkInterface>();
                myHost.add(theLoopBackInterface);
            }catch(SocketException f){
                throw new NoAvailableNetworkAdapterException("Could not detect local network adapter", f);
            }
        }

        if(myHost.size() == 0){
            throw new NoAvailableNetworkAdapterException("There is no available network adapter on this system");
        }
    }

    public List<SimpleNetworkInterface> getHosts() {
        return myHost;
    }
    public void setHosts( List<SimpleNetworkInterface> anHost ) {
        myHost = anHost;
    }
    public int getPort() {
        return myPort;
    }
    public void setPort( int anPort ) {
        myPort = anPort;
    }

    public StreamSplitterSupport isStreamSplittingSupported() {
        return myStreamSplittingSupported;
    }

    public void setStreamSplittingSupported(boolean isSupported){
        myStreamSplittingSupported = isSupported ? StreamSplitterSupport.TRUE : StreamSplitterSupport.FALSE;
    }


    private Set<String> getIPs(){
        Set<String> theIPList = new HashSet<String>();
        for(SimpleNetworkInterface theInteface : myHost){
            for(String theIP : theInteface.getIp()){
                theIPList.add( theIP );
            }
        }
        return theIPList;
    }

    /**
     * this method creates a socket by using the socket pool
     * you must call check in or close on the connection pool after you've used this socket!!
     * @param aPort
     * @return
     */
    public SocketProxy createSocket(final int aPort){
        final iSocketPool theSocketPool = P2PSettings.getInstance().getSocketPool();

        final CountDownLatch theCountDownLatch = new CountDownLatch(getIPs().size());
        for(Iterator< SimpleNetworkInterface > i = new ArrayList<SimpleNetworkInterface>(myHost).iterator(); i.hasNext();){
            final SimpleNetworkInterface theHost = i.next();
            try{
                ExecutorService theExecutorService =  Executors.newCachedThreadPool();
                final BlockingQueue<SocketProxy> theSocketQueue = new ArrayBlockingQueue<SocketProxy>( 1 );
                final CyclicBarrier theBarrier = new CyclicBarrier( theHost.getIp().size() );
                for(final String theIp : theHost.getIp()){
                    theExecutorService.execute( new Runnable(){
                        public void run(){
                            try{
                                //we use a barrier to make sure the connection attempts to all ip's are started simultaniously
                                //this will make sure that we will choose the fastest network interface in case when the remote
                                //peer is reachable trough different interfaces like wifi and wired network
                                theBarrier.await( 2, TimeUnit.SECONDS );
                                String theHostIP = theIp;
                                if(IPAddress.isIpAddress( theIp )){
                                    theHostIP = new IPAddress(theIp).getIPAddressOnly();
                                }
                                LOGGER.debug( "trying to connect to '" + theHostIP + "' at '" + aPort + "'" );
                                theSocketQueue.put( theSocketPool.checkOut(new InetSocketAddress(theHostIP, aPort)));
                                synchronized(this){
                                    myHost.remove( theHost ); 
                                    myHost.add( 0, theHost);
                                }
                            }catch(Exception e){
                                //                LOGGER.error( "Error while checking out socket for ip '" + theIp + ":" + aPort + "'", e );
                                LOGGER.error( "Could not create socket for ip '" + theIp + ":" + aPort + "'" );
                            }
                            theCountDownLatch.countDown();
                            if(theCountDownLatch.getCount() == 0){
                                try {
                                    theSocketQueue.put(new SocketProxy((SocketAddress)null));
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    });
                }

                SocketProxy theSocket = theSocketQueue.poll( 5, TimeUnit.SECONDS );
                theExecutorService.shutdownNow();
                if(theSocket.getSocketAddress() == null) return null;
                return theSocket;
            }catch(Exception e){
                LOGGER.error("Could not open connection to peer: " + myHost + ":" + myPort, e);
            }
        }
        return null;
    }

    public String toString(){
        StringBuilder theBuilder = new StringBuilder();
        theBuilder.append( getPeerId() );
        theBuilder.append("@");
        theBuilder.append(getChannel());
        theBuilder.append( " (" );
        if(getHosts() != null && getHosts().size() > 0){
            for(Iterator< SimpleNetworkInterface > i = getHosts().iterator();i.hasNext();){
                SimpleNetworkInterface theHost = i.next();
                theBuilder.append( theHost );
                if(i.hasNext()) theBuilder.append( "," );
            }
        }
        theBuilder.append( ":" );
        theBuilder.append( getPort());
        theBuilder.append(")");
        return theBuilder.toString();
    }

    public boolean isSameEndPointAs(AbstractPeer aPeer){
        if(!(aPeer instanceof SocketPeer)) return false;

        SocketPeer thePeer = (SocketPeer)aPeer;
        List<SimpleNetworkInterface> theHosts = thePeer.getHosts();
        boolean isSameHost = false;
        for(SimpleNetworkInterface theHost : theHosts){
            isSameHost |= getHosts().contains( theHost );
        }
        if(!isSameHost) return false;
        return getPort() == thePeer.getPort(); 
    }

    @Override
    public boolean isValidEndPoint() {
        if(myHost == null) return false;
        if(myHost.size() == 0) return false;
        if(myPort <= 0) return false;
        return true;
    }

    @Override
    public String getEndPointRepresentation() {
        return myHost + ":" + myPort;
    }

    @Override
    public boolean isContactable() {
        return true;
    }

    public static void main(String args[]) throws UnknownHostException, IOException{
        Socket theSocket = null;
        try{
            theSocket = new Socket("10.240.251.46/22", 12700);
            System.out.println("socket was created!");
        }finally{
            if(theSocket != null){
                theSocket.close();
            }
        }
    }
}
