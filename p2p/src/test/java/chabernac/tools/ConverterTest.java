/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.tools;

import java.io.IOException;
import java.net.URL;

import junit.framework.TestCase;
import chabernac.io.Base64ObjectStringConverter;
import chabernac.io.DummyNetworkInterface;
import chabernac.io.iObjectStringConverter;
import chabernac.protocol.message.Message;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import chabernac.protocol.routing.SocketPeer;
import chabernac.protocol.routing.UnknownPeerException;
import chabernac.protocol.routing.WebPeer;

public class ConverterTest extends TestCase {
  private iObjectStringConverter<RoutingTable> myRoutingTableConverter = new Base64ObjectStringConverter<RoutingTable>();
  private iObjectStringConverter<RoutingTableEntry> myRoutingTableEntryConverter = new Base64ObjectStringConverter<RoutingTableEntry>();
  
//  static{
//    BasicConfigurator.configure();
//  }

  public void testToXML() throws UnknownPeerException, IOException{
    RoutingTable theTable = new RoutingTable("1");

    SocketPeer thePeer = new SocketPeer("2", 1002, "x20d1148");
    RoutingTableEntry theEntry = new RoutingTableEntry(thePeer, 1, thePeer, 123456, 0, new DummyNetworkInterface());
    long theLastOnlineTime2 = theEntry.getLastOnlineTime();

    SocketPeer thePeer2 = new SocketPeer("3", 1003, "x01p0880");
    RoutingTableEntry theEntry2 = new RoutingTableEntry(thePeer2, 2, thePeer, 654321, 0, new DummyNetworkInterface());
    long theLastOnlineTime3 = theEntry2.getLastOnlineTime();

    SocketPeer thePeer3 = new SocketPeer("4", 1003, "x01p0880");
    RoutingTableEntry theEntry3 = new RoutingTableEntry(thePeer3, 1, thePeer3, 654321, 0, new DummyNetworkInterface());
    
    WebPeer thePeer4 = new WebPeer("5", new URL("http://localhost:8080"));
    thePeer4.setChannel( "peer4" );
    RoutingTableEntry theEntry4 = new RoutingTableEntry(thePeer4, 1, thePeer4, 654321, 0, new DummyNetworkInterface());
    long theLastOnlineTime5 = theEntry4.getLastOnlineTime();

    theTable.addRoutingTableEntry( theEntry );
    theTable.addRoutingTableEntry( theEntry2 );
    theTable.addRoutingTableEntry( theEntry3 );
    theTable.addRoutingTableEntry( theEntry4 );
    
    assertEquals(theLastOnlineTime5, theTable.getEntryForPeer( "5" ).getLastOnlineTime());

    long theLastOnlineTime4 = theEntry3.getLastOnlineTime();
    theEntry3 = theEntry3.setHopDistance( RoutingTableEntry.MAX_HOP_DISTANCE );
    theTable.addRoutingTableEntry( theEntry3 );

    RoutingTable theTable2 = myRoutingTableConverter.getObject(myRoutingTableConverter.toString(theTable ));

    assertEquals(4, theTable2.getEntries().size());

    assertEquals("x20d1148", ((SocketPeer)theTable2.getEntryForPeer( "2" ).getPeer()).getHosts().get( 0 ).getIp().get( 0 ));
    assertEquals(1002, ((SocketPeer)theTable2.getEntryForPeer( "2" ).getPeer()).getPort());
    assertEquals("2", theTable2.getEntryForPeer( "2" ).getPeer().getPeerId());
    assertEquals(theLastOnlineTime2, theTable2.getEntryForPeer( "2" ).getLastOnlineTime());
    assertEquals(1, theTable2.getEntryForPeer( "2" ).getHopDistance());

    assertEquals("x01p0880", ((SocketPeer)theTable2.getEntryForPeer( "3" ).getPeer()).getHosts().get( 0 ).getIp().get( 0 ));
    assertEquals(1003, ((SocketPeer)theTable2.getEntryForPeer( "3" ).getPeer()).getPort());
    assertEquals("3", theTable2.getEntryForPeer( "3" ).getPeer().getPeerId());
    assertEquals(theLastOnlineTime3, theTable2.getEntryForPeer( "3" ).getLastOnlineTime());
    assertEquals(2, theTable2.getEntryForPeer( "3" ).getHopDistance());

    assertEquals("x01p0880", ((SocketPeer)theTable2.getEntryForPeer( "4" ).getPeer()).getHosts().get( 0 ).getIp().get( 0 ));
    assertEquals(1003, ((SocketPeer)theTable2.getEntryForPeer( "4" ).getPeer()).getPort());
    assertEquals("4", theTable2.getEntryForPeer( "4" ).getPeer().getPeerId());
    assertEquals(theLastOnlineTime4, theTable2.getEntryForPeer( "4" ).getLastOnlineTime());
    assertEquals(RoutingTableEntry.MAX_HOP_DISTANCE, theTable2.getEntryForPeer( "4" ).getHopDistance());
    
    assertEquals("5", theTable2.getEntryForPeer( "5" ).getPeer().getPeerId());
    assertEquals(theLastOnlineTime5, theTable2.getEntryForPeer( "5" ).getLastOnlineTime());
    assertEquals(1, theTable2.getEntryForPeer( "5" ).getHopDistance());
    assertEquals("peer4", theTable2.getEntryForPeer( "5" ).getPeer().getChannel());
    assertEquals("http://localhost:8080", ((WebPeer)theTable2.getEntryForPeer( "5" ).getPeer()).getURL().toString());

  }

  public void testPeerEntryToXML() throws IOException{


    SocketPeer thePeer = new SocketPeer("2", 1002, "x20d1148");
    RoutingTableEntry theEntry = new RoutingTableEntry(thePeer, 1, thePeer,123456, 0, new DummyNetworkInterface());
    RoutingTableEntry theEntry2 = myRoutingTableEntryConverter.getObject( myRoutingTableEntryConverter.toString( theEntry ));
    assertEquals( theEntry.getPeer().getPeerId(), theEntry2.getPeer().getPeerId()); 
  }

//  public void testFromXML(){
//    String theXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <java version=\"1.6.0_05\" class=\"java.beans.XMLDecoder\">  <object class=\"chabernac.protocol.routing.RoutingTable\">   <void property=\"localPeerId\">    <string>3</string>   </void>   <void id=\"HashMap0\" property=\"routingTable\">    <void method=\"put\">     <string>3</string>     <object class=\"chabernac.protocol.routing.RoutingTableEntry\">      <void property=\"gateway\">       <object id=\"Peer0\" class=\"chabernac.protocol.routing.Peer\">        <void property=\"hosts\">         <object class=\"java.util.ArrayList\">          <void method=\"add\">           <string>10.240.114.251</string>          </void>         </object>        </void>        <void property=\"peerId\">         <string>3</string>        </void>        <void property=\"port\">         <int>12702</int>        </void>       </object>      </void>      <void property=\"hopDistance\">       <int>0</int>      </void>      <void property=\"peer\">       <object idref=\"Peer0\"/>      </void>     </object>    </void>    <void method=\"put\">     <string>2</string>     <object class=\"chabernac.protocol.routing.RoutingTableEntry\">      <void property=\"gateway\">       <object id=\"Peer1\" class=\"chabernac.protocol.routing.Peer\">        <void property=\"hosts\">         <object class=\"java.util.ArrayList\">          <void method=\"add\">           <string>10.240.114.251</string>          </void>         </object>        </void>        <void property=\"peerId\">         <string>2</string>        </void>        <void property=\"port\">         <int>12701</int>        </void>       </object>      </void>      <void property=\"hopDistance\">       <int>1</int>      </void>      <void property=\"peer\">       <object idref=\"Peer1\"/>      </void>     </object>    </void>    <void method=\"put\">     <string>1</string>     <object class=\"chabernac.protocol.routing.RoutingTableEntry\">      <void property=\"gateway\">       <object id=\"Peer2\" class=\"chabernac.protocol.routing.Peer\">        <void property=\"hosts\">         <object class=\"java.util.ArrayList\">          <void method=\"add\">           <string>10.240.114.251</string>          </void>         </object>        </void>        <void property=\"peerId\">         <string>1</string>        </void>        <void property=\"port\">         <int>12700</int>        </void>       </object>      </void>      <void property=\"hopDistance\">       <int>1</int>      </void>      <void property=\"peer\">       <object idref=\"Peer2\"/>      </void>     </object>    </void>    <void method=\"put\">     <string>4</string>     <object class=\"chabernac.protocol.routing.RoutingTableEntry\">      <void property=\"gateway\">       <object id=\"Peer3\" class=\"chabernac.protocol.routing.Peer\">        <void property=\"hosts\">         <object class=\"java.util.ArrayList\">          <void method=\"add\">           <string>10.240.114.251</string>          </void>         </object>        </void>        <void property=\"peerId\">         <string>4</string>        </void>        <void property=\"port\">         <int>12703</int>        </void>       </object>      </void>      <void property=\"hopDistance\">       <int>1</int>      </void>      <void property=\"peer\">       <object idref=\"Peer3\"/>      </void>     </object>    </void>   </void>   <void property=\"routingTable\">    <object idref=\"HashMap0\"/>   </void>  </object> </java> ";
//    RoutingTable theTable = (RoutingTable)XMLTools.fromXML(theXML);
//    for(RoutingTableEntry theEntry : theTable){
//      assertNotNull( theEntry.getPeer() );
//      assertNotNull( theEntry.getGateway() );
//    }
//  }

  public void testCarriageReturnLineFeed(){
    String theString = "the \r\n carriage return";
    String theXML = XMLTools.toXML( theString );
    assertFalse( theXML.contains( "\r\n" ) );
    assertEquals( theString, XMLTools.fromXML( theXML ) );

  }

  public void testBytesToXML(){
    Message theMessage = new Message();
    byte[] theBytes = new byte[100];
    for(int i=0;i<theBytes.length;i++){
      theBytes[i] = (byte)i;
    }
    theMessage.setBytes( theBytes );

    String theXML = XMLTools.toXML( theMessage );
    Message theNewMessage = (Message)XMLTools.fromXML( theXML );

    assertNotNull( theNewMessage );
    assertNotNull( theNewMessage.getBytes() );

    byte[] theNewBytes = theNewMessage.getBytes();

    assertEquals( theBytes.length, theNewBytes.length );

    for(int i=0;i<theBytes.length;i++){
      assertEquals( theBytes[i], theNewBytes[i] );
    }

  }

}
