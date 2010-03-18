import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.echo.EchoProtocol;
import junit.framework.TestCase;

/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */

public class EchoProtocolTest extends TestCase {
    public void testEchoProtocol(){
      EchoProtocol theProtocol = new EchoProtocol();
      assertEquals( "test", theProtocol.handleCommand( 0, "test" ));
    }
    
    public void testEchoProtocolInContainer(){
      ProtocolContainer theContainer = new ProtocolContainer();
      theContainer.addProtocol( new EchoProtocol() );
      
      assertEquals( "test", theContainer.handleCommand( 0, "ECOtest" ));
    }
    
}
