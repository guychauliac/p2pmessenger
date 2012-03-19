/**
 * Copyright (c) 2012 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.io;

import java.io.IOException;

import org.apache.log4j.BasicConfigurator;

import junit.framework.TestCase;

public class StreamSplittingServerTest extends TestCase {
  static{
    BasicConfigurator.resetConfiguration();
    BasicConfigurator.configure();
  }
  
  public void testStreamSplittingServer() throws IOException, InterruptedException{
    
    StreamSplittingServer theServer1 = new StreamSplittingServer( new MultiplyHandler( 2 ), 13000, false, "1" );
    StreamSplittingServer theServer2 = new StreamSplittingServer( new MultiplyHandler( 3 ), 13001, false, "2" );
    theServer1.start();
    theServer2.start();
    assertTrue(theServer1.isStarted());
    assertTrue(theServer2.isStarted());
    
    try{
    int times = 10000;
    assertEquals( Integer.toString(5 * 3), theServer1.send( "localhost", 13001, Integer.toString(5) ).getReply());
    assertEquals( Integer.toString(5 * 2), theServer2.send( "localhost", 13000, Integer.toString(5) ).getReply());
    for(int i=0;i<times;i++){
      assertEquals( Integer.toString(i * 3), theServer1.send( "2", Integer.toString(i) ));
      assertEquals( Integer.toString(i * 2), theServer2.send( "1", Integer.toString(i) ));
    }
    }finally{
      theServer1.close();
      theServer2.close();
      Thread.sleep(1000);
      assertFalse(theServer1.isStarted());
      assertFalse(theServer2.isStarted());
    }
  }
  
  public void testSimultanousConnectionAttempt(){
    
  }
}
