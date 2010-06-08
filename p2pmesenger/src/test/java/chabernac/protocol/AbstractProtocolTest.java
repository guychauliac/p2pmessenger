/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol;

import junit.framework.TestCase;
import chabernac.protocol.routing.PeerSocketFactory;
import chabernac.tools.PropertyMap;

public abstract class AbstractProtocolTest extends TestCase {
  public void setUp(){
    PeerSocketFactory.getInstance().clear();
  }
  
  public ProtocolContainer getProtocolContainer(long anExchangeDelay, boolean isPersist, String aPeerId){
    PropertyMap theProperties = new PropertyMap();
    theProperties.setProperty( "routingprotocol.exchangedelay", Long.toString( anExchangeDelay));
    theProperties.setProperty("routingprotocol.persist", Boolean.toString( isPersist));
    theProperties.setProperty("peerid", aPeerId);
    ProtocolFactory theFactory = new ProtocolFactory(theProperties);
    return new ProtocolContainer(theFactory);
  }
}
