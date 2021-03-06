/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.filetransfer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.UnknownHostException;

import org.apache.log4j.Logger;

import chabernac.protocol.AbstractProtocolTest;
import chabernac.protocol.P2PServerFactoryException;
import chabernac.protocol.ProtocolContainer;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.iP2PServer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import chabernac.protocol.routing.UnknownPeerException;

public class FileTransferProtocolTest extends AbstractProtocolTest {
  private static Logger LOGGER = Logger.getLogger(FileTransferProtocolTest.class);
  
//  static{
//    BasicConfigurator.resetConfiguration();
//    BasicConfigurator.configure();;
//  }

  public void testFileTransfer() throws InterruptedException, UnknownHostException, IOException, FileTransferException, ProtocolException, UnknownPeerException, P2PServerFactoryException{

    //p1 <--> p2 <--> p3 peer 1 cannot reach peer 3
    ProtocolContainer theProtocol1 = getProtocolContainer( -1, false, "1");
    iP2PServer theServer1 = getP2PServer( theProtocol1, RoutingProtocol.START_PORT);
    RoutingProtocol theRoutingProtocol1 = (RoutingProtocol)theProtocol1.getProtocol( RoutingProtocol.ID );

    ProtocolContainer theProtocol2 = getProtocolContainer( -1, false, "2");
    iP2PServer theServer2 = getP2PServer( theProtocol2, RoutingProtocol.START_PORT + 1);
    RoutingProtocol theRoutingProtocol2 = (RoutingProtocol)theProtocol2.getProtocol( RoutingProtocol.ID );

    ProtocolContainer theProtocol3 = getProtocolContainer( -1, false, "3");
    File theFileToWrite = new File("in.temp");
    TestFileHandler theFileHandler = new TestFileHandler(theFileToWrite);
    iP2PServer theServer3 = getP2PServer( theProtocol3, RoutingProtocol.START_PORT + 2);
    ((FileTransferProtocol)theProtocol3.getProtocol( FileTransferProtocol.ID )).setFileHandler( theFileHandler );
    RoutingProtocol theRoutingProtocol3 = (RoutingProtocol)theProtocol3.getProtocol( RoutingProtocol.ID );

    ((RoutingProtocol)theProtocol1.getProtocol( RoutingProtocol.ID )).getLocalUnreachablePeerIds().add( "3" );
    ((RoutingProtocol)theProtocol3.getProtocol( RoutingProtocol.ID )).getLocalUnreachablePeerIds().add( "1" );

    File theTempFile = createTempFile();

    assertNotNull( theTempFile );
    assertTrue( theTempFile.length() > 0 );

    try{
      assertTrue( theServer1.start() );
      assertTrue( theServer2.start() );
      assertTrue( theServer3.start() );
      
      theRoutingProtocol1.scanLocalSystem();
      theRoutingProtocol2.scanLocalSystem();
      theRoutingProtocol3.scanLocalSystem();
      
      Thread.sleep( SLEEP_AFTER_SCAN );
      
      for(int i=0;i<5;i++){
        theRoutingProtocol1.exchangeRoutingTable();
        theRoutingProtocol2.exchangeRoutingTable();
        theRoutingProtocol3.exchangeRoutingTable();
      }

      
      LOGGER.debug( "Sleeping" );
      Thread.sleep( SLEEP_AFTER_SCAN );
      LOGGER.debug( "Done Sleeping" );

      RoutingTable theRoutingTable1 = ((RoutingProtocol)theProtocol1.getProtocol( RoutingProtocol.ID )).getRoutingTable();
      RoutingTable theRoutingTable3 = ((RoutingProtocol)theProtocol3.getProtocol( RoutingProtocol.ID )).getRoutingTable();

      FileTransferProtocol theFileTransferProtocol = (FileTransferProtocol)theProtocol1.getProtocol( FileTransferProtocol.ID );
      RoutingTableEntry thePeer3 = theRoutingTable1.getEntryForPeer( theRoutingTable3.getLocalPeerId() );
      assertNotNull( thePeer3.getPeer() );
      assertTrue(thePeer3.isReachable());
      LOGGER.debug( "Sending file" );
      theFileTransferProtocol.sendFile( theTempFile, theRoutingTable3.getLocalPeerId() );
      LOGGER.debug( "Done Sending file" );

//      Thread.sleep( 10000 );

      assertTrue( theFileToWrite.exists() );
      assertEquals( theTempFile.length(), theFileToWrite.length());

      assertEquals( theTempFile.getName(), theFileHandler.getAcceptedFile());
      assertEquals( theTempFile.length(), theFileHandler.getTotalBytes());
      assertEquals( theTempFile.length(), theFileHandler.getTotalBytes());
      assertNull(  theFileHandler.getInterruptedFile() );
      assertEquals( theFileToWrite, theFileHandler.getSavedFile());

    } finally {
      theServer1.stop();
      theServer2.stop();
      theServer3.stop();
      if(theTempFile.exists()){
        theTempFile.delete();
      }
      if(theFileToWrite.exists()){
        theFileToWrite.delete();
      }
    }

  }

  private File createTempFile() throws FileNotFoundException{
    File theFile = new File("test.temp");
    PrintWriter theWriter = null;
    theWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(theFile)));
    for(int i=0;i<100;i++){
      theWriter.println("Line: " + i);
    }
    theWriter.flush();
    theWriter.close();
    return theFile;
  }

  private class TestFileHandler implements iFileHandler{
    private File myFileToWrite = null;
    private File mySavedFile = null;

    private String myAcceptedFile = null;
    private long myTranferredBytes = 0;
    private long myTotalBytes = 0;

    private File myInterruptedFile = null;

    public TestFileHandler(File aFileToWrite){
      myFileToWrite = aFileToWrite;
    }

    @Override
    public File acceptFile( String aFileName ) {
      myAcceptedFile = aFileName;
      return myFileToWrite;
    }

    @Override
    public void fileSaved( File aFile ) {
      mySavedFile = aFile;
    }

    @Override
    public void fileTransfer( File anAfile, long aBytesReceived, long aTotalBytes ) {
      myTranferredBytes = aBytesReceived;
      myTotalBytes = aTotalBytes;
    }

    @Override
    public void fileTransferInterrupted( File aFile ) {
      myInterruptedFile = aFile;
    }

    public File getSavedFile() {
      return mySavedFile;
    }

    public String getAcceptedFile() {
      return myAcceptedFile;
    }

    public long getTranferredBytes() {
      return myTranferredBytes;
    }

    public long getTotalBytes() {
      return myTotalBytes;
    }

    public File getInterruptedFile() {
      return myInterruptedFile;
    }
  }

}
