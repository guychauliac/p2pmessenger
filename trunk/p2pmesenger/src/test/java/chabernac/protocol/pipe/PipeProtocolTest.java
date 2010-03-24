package chabernac.protocol.pipe;

import java.io.IOException;

import org.apache.log4j.BasicConfigurator;

import chabernac.protocol.AbstractProtocolTest;
import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.ProtocolServer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.UnkwownPeerException;
import chabernac.tools.IOTools;

public class PipeProtocolTest extends AbstractProtocolTest {
  
  static{
    BasicConfigurator.configure();
  }
  
  public void testPipeProtocol() throws InterruptedException, IOException, UnkwownPeerException, ProtocolException, PipeException{
    //p1 <--> p2 <--> p3 peer 1 cannot reach peer 3

    ProtocolContainer theProtocol1 = getProtocolContainer( 10, false, "1" );
    ProtocolServer theServer1 = new ProtocolServer(theProtocol1, RoutingProtocol.START_PORT, 5);

    ProtocolContainer theProtocol2 = getProtocolContainer( 10, false, "2" );
    ProtocolServer theServer2 = new ProtocolServer(theProtocol2, RoutingProtocol.START_PORT + 1, 5);

    ProtocolContainer theProtocol3 = getProtocolContainer( 10, false, "3" );
    ProtocolServer theServer3 = new ProtocolServer(theProtocol3, RoutingProtocol.START_PORT + 2, 5);

    RoutingProtocol theRoutingProtocol1 = (RoutingProtocol)theProtocol1.getProtocol( RoutingProtocol.ID );
    PipeProtocol thePipeProtocol1 = (PipeProtocol)theProtocol1.getProtocol( PipeProtocol.ID );
    
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

      Thread.sleep( 5000 );

      //open a pipe from peer 1 to peer 3, it should traverse peer 2
      Pipe thePipe = new Pipe(theRoutingProtocol1.getRoutingTable().getEntryForPeer("3").getPeer());
      thePipe.setPipeDescription("Test pipe description");
      
      thePipeProtocol1.openPipe(thePipe);

      for(int i=0;i<100;i++){
        thePipe.getSocket().getOutputStream().write(i);
        thePipe.getSocket().getOutputStream().flush();
        assertEquals(i, thePipe.getSocket().getInputStream().read());
      }
      
      assertEquals("1", thePipeListener.getPipe().getPeer().getPeerId());
      assertEquals("Test pipe description", thePipeListener.getPipe().getPipeDescription());

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
