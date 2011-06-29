/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.asyncfiletransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;
import chabernac.io.ClassPathResource;

public class FilePacketIOTest extends TestCase {
  public void testFilePacket() throws IOException{
    File theRead = new ClassPathResource( "/chabernac/protocol/asyncfiletransfer/mars_1k_color.jpg" ).getFile();
    File theWrite = new File("out.jpg");
    if(theWrite.exists()) theWrite.delete();
    
    FilePacketIO theReadPacket = new FilePacketIO( theRead, 1024);
    FilePacketIO theWritePacket = new FilePacketIO( theWrite, 1024);
    
    System.out.println("File size: " + theRead.length());
    
    for(int i=0;i<=theReadPacket.getNrOfPackets();i++){
      System.out.println("Writing packet " + i);
      theWritePacket.writePacket( theReadPacket.getPacket( i ) );
    }
    
    theWritePacket.close();
    
    assertEquals( theRead.length(), theWrite.length() );
    
    InputStream theReader1 = new FileInputStream( theRead );
    InputStream theReader2 = new FileInputStream( theWrite );
    
    int thebyte;
    while( (thebyte = theReader1.read()) != -1){
      assertEquals( thebyte, theReader2.read() );
    }
    
    theReader1.close();
    theReader2.close();
  }
}
