package chabernac.protocol.pipe;

import java.io.IOException;

import org.apache.log4j.Logger;

import chabernac.protocol.AbstractProtocolTest;
import chabernac.protocol.P2PServerFactoryException;
import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.iP2PServer;
import chabernac.protocol.routing.NoAvailableNetworkAdapterException;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.SocketPeer;
import chabernac.protocol.routing.UnknownPeerException;
import chabernac.tools.IOTools;

public class PipeProtocolTest extends AbstractProtocolTest {
  private static Logger LOGGER = Logger.getLogger(PipeProtocolTest.class);
  
//  static{
//    BasicConfigurator.resetConfiguration();
//    BasicConfigurator.configure();
//  }
  
  public void testPipeProtocol() throws InterruptedException, IOException, UnknownPeerException, ProtocolException, PipeException, NoAvailableNetworkAdapterException, P2PServerFactoryException{
    //p1 <--> p2 <--> p3 peer 1 cannot reach peer 3

    ProtocolContainer theProtocol1 = getProtocolContainer( -1, false, "1" );
    iP2PServer theServer1 = getP2PServer( theProtocol1, RoutingProtocol.START_PORT);

    ProtocolContainer theProtocol2 = getProtocolContainer( -1, false, "2" );
    iP2PServer theServer2 = getP2PServer( theProtocol2, RoutingProtocol.START_PORT + 1);

    ProtocolContainer theProtocol3 = getProtocolContainer( -1, false, "3" );
    iP2PServer theServer3 = getP2PServer( theProtocol3, RoutingProtocol.START_PORT + 2);

    RoutingProtocol theRoutingProtocol1 = (RoutingProtocol)theProtocol1.getProtocol( RoutingProtocol.ID );
    PipeProtocol thePipeProtocol1 = (PipeProtocol)theProtocol1.getProtocol( PipeProtocol.ID );
    
    RoutingProtocol theRoutingProtocol2 = (RoutingProtocol)theProtocol2.getProtocol( RoutingProtocol.ID );
    
    RoutingProtocol theRoutingProtocol3 = (RoutingProtocol)theProtocol3.getProtocol( RoutingProtocol.ID );
    PipeProtocol thePipeProtocol3 = (PipeProtocol)theProtocol3.getProtocol( PipeProtocol.ID );
    EchoPipeListener thePipeListener = new EchoPipeListener();
    thePipeProtocol3.addPipeListener( thePipeListener );
    
    
    theRoutingProtocol1.getLocalUnreachablePeerIds().add( "3" );
    theRoutingProtocol3.getLocalUnreachablePeerIds().add( "1" );

    try{
      assertTrue( theServer1.start() );
      assertTrue( theServer2.start() );
      assertTrue( theServer3.start() );

      theRoutingProtocol1.scanLocalSystem();
      theRoutingProtocol2.scanLocalSystem();
      theRoutingProtocol3.scanLocalSystem();
      
      for(int i=0;i<5;i++){
        theRoutingProtocol1.exchangeRoutingTable();
        theRoutingProtocol2.exchangeRoutingTable();
        theRoutingProtocol3.exchangeRoutingTable();
      }

      //after a local system scan we must at least know our selfs
      assertNotNull( theRoutingProtocol1.getRoutingTable().getEntryForLocalPeer(5) );
      assertNotNull( theRoutingProtocol3.getRoutingTable().getEntryForLocalPeer(5) );


      //open a pipe from peer 1 to peer 3, it should traverse peer 2
      Pipe thePipe = new Pipe((SocketPeer)theRoutingProtocol1.getRoutingTable().getEntryForPeer("3", 5).getPeer());
      thePipe.setPipeDescription("Test pipe description");
      
      LOGGER.debug( "opening pipe" );
      
      thePipeProtocol1.openPipe(thePipe);
      
      LOGGER.debug( "Testing pipe" );

      for(int i=0;i<100;i++){
        thePipe.getSocket().getOutputStream().write(i);
        thePipe.getSocket().getOutputStream().flush();
        assertEquals(i, thePipe.getSocket().getInputStream().read());
      }
      
      LOGGER.debug( "Pipe tested" );
      
      assertEquals("1", thePipeListener.getPipe().getPeer().getPeerId());
      assertEquals("Test pipe description", thePipeListener.getPipe().getPipeDescription());
      
      LOGGER.debug( "Pipe properties tested" );

      //now lets try to send bytes over the pipe
    }finally{
      theServer1.stop();
      theServer2.stop();
      theServer3.stop();
    }

  }

  private class EchoPipeListener implements IPipeListener{
    private Pipe myPipe = null;
    
    
    @Override
    public void incomingPipe(final Pipe aPipe) {
      myPipe = aPipe;
      new Thread(new Runnable(){
        public void run(){
          try {
            IOTools.copyStream(aPipe.getSocket().getInputStream(), aPipe.getSocket().getOutputStream());
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }).start();
    }
    
    public Pipe getPipe(){
      return myPipe;
    }
  }
}
