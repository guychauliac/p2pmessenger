/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.tools;

import chabernac.protocol.routing.Peer;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import junit.framework.TestCase;

public class XMLToolsTest extends TestCase {
  public void testToXML(){
    RoutingTable theTable = new RoutingTable("1");

    Peer thePeer = new Peer("2", "x20d1148", 1002);
    RoutingTableEntry theEntry = new RoutingTableEntry(thePeer, 1, thePeer);
    
    Peer thePeer2 = new Peer("3", "x01p0880", 1003);
    RoutingTableEntry theEntry2 = new RoutingTableEntry(thePeer2, 2, thePeer);

    theTable.addRoutingTableEntry( theEntry );
    theTable.addRoutingTableEntry( theEntry2 );
    
    RoutingTable theTable2 = (RoutingTable)XMLTools.fromXML(XMLTools.toXML( theTable ));
    
    assertEquals(2, theTable2.getEntries().size());
    
   assertEquals("x20d1148", theTable2.getEntryForPeer( "2" ).getPeer().getHosts().get( 0 ));
   assertEquals(1002, theTable2.getEntryForPeer( "2" ).getPeer().getPort());
   assertEquals("2", theTable2.getEntryForPeer( "2" ).getPeer().getPeerId());
   
   assertEquals("x01p0880", theTable2.getEntryForPeer( "3" ).getPeer().getHosts().get( 0 ));
   assertEquals(1003, theTable2.getEntryForPeer( "3" ).getPeer().getPort());
   assertEquals("3", theTable2.getEntryForPeer( "3" ).getPeer().getPeerId());

  }
  
  public void testPeerEntryToXML(){
    Peer thePeer = new Peer("2", "x20d1148", 1002);
    RoutingTableEntry theEntry = new RoutingTableEntry(thePeer, 1, thePeer);
    RoutingTableEntry theEntry2 = (RoutingTableEntry)XMLTools.fromXML( XMLTools.toXML( theEntry ));
    assertEquals( theEntry.getPeer().getPeerId(), theEntry2.getPeer().getPeerId()); 
  }
  
}
