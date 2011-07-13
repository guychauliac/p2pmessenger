/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.asyncfiletransfer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import chabernac.io.Base64ObjectStringConverter;
import chabernac.io.iObjectStringConverter;
import chabernac.protocol.Protocol;
import chabernac.protocol.ProtocolException;
import chabernac.protocol.message.AsyncMessageProcotol;
import chabernac.protocol.message.Message;
import chabernac.protocol.routing.AbstractPeer;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.RoutingTable;
import chabernac.thread.DynamicSizeExecutor;

public class AsyncFileTransferProtocol extends Protocol {
  private static final Logger LOGGER = Logger.getLogger( AsyncFileTransferException.class );
  
  public static final String ID = "AFP";

  private static enum Command{ACCEPT_FILE, RESEND_PACKET, ACCEPT_PACKET, END_FILE_TRANSFER};
  private static enum Response{FILE_ACCEPTED, PACKET_OK, NOK, UNKNOWN_ID, END_FILE_TRANSFER_OK};

  private static final int PACKET_SIZE = 1024;
  private static final int MAX_RETRY = 8;

  private iObjectStringConverter<FilePacket> myObjectPerister = new Base64ObjectStringConverter<FilePacket>();

  private Map<String, FilePacketIO> myFilePacketIO = new HashMap<String, FilePacketIO>();

  private iAsyncFileTransferHandler myHandler = null;
  private List<iAsyncFileTransferListener> myListeners = new ArrayList<iAsyncFileTransferListener>();
  
  private ExecutorService myService = DynamicSizeExecutor.getTinyInstance();

  public AsyncFileTransferProtocol( ) {
    super( ID );
  }

  @Override
  public String getDescription() {
    return "Async file transfer protocol";
  }

  private AsyncMessageProcotol getMessageProtocol() throws ProtocolException{
    return (AsyncMessageProcotol)findProtocolContainer().getProtocol( AsyncMessageProcotol.ID);
  }

  public RoutingTable getRoutingTable() throws ProtocolException{
    return ((RoutingProtocol)findProtocolContainer().getProtocol( RoutingProtocol.ID)).getRoutingTable();
  }


  @Override
  public String handleCommand( String aSessionId, String anInput ) {
    try{
      if(anInput.startsWith( Command.ACCEPT_FILE.name() )){
        String[] theParams = anInput.substring( Command.ACCEPT_FILE.name().length() + 1 ).split( " " );
        
        String theFileName = theParams[0];
        String theUUId = theParams[1];
        int thePacketSize = Integer.parseInt( theParams[2] );
        int theNrOfPackets = Integer.parseInt( theParams[3] );
        
        File theFile = myHandler.acceptFile( theFileName, theUUId );
        FilePacketIO theIO = FilePacketIO.createForWrite( theFile, theUUId, thePacketSize, theNrOfPackets );
        myFilePacketIO.put( theUUId, theIO );
        
        //create a 
        return Response.FILE_ACCEPTED.name();
      } else if(anInput.startsWith( Command.ACCEPT_PACKET.name() )){
        String thePack = anInput.substring(Command.ACCEPT_PACKET.name().length() + 1 );
        FilePacket thePacket = myObjectPerister.getObject( thePack );
        
        if(!myFilePacketIO.containsKey( thePacket.getId() )){
          return Response.UNKNOWN_ID.name();
        }
        
        FilePacketIO theIO = myFilePacketIO.get(thePacket.getId());
        theIO.writePacket( thePacket );
        
        myHandler.fileTransfer( theIO.getFile().getName(), thePacket.getId(), theIO.getPercentageWritten());
        
        return Response.PACKET_OK.name();
      } else if(anInput.startsWith( Command.END_FILE_TRANSFER.name() )){
        String[] theParams = anInput.substring( Command.END_FILE_TRANSFER.name().length() + 1 ).split( " " );
        
        String theUUId = theParams[0];
        FilePacketIO theIO = myFilePacketIO.get(theUUId);
        if(theIO.isComplete()){
          notifyIncomingFile( theIO.getFile() );
          return Response.END_FILE_TRANSFER_OK.name();
        } else {
          String theIncompletePacktets = "";
          for(int i=0;i<theIO.getWrittenPackets().length;i++){
            if(!theIO.getWrittenPackets()[i]){
              theIncompletePacktets += i + " ";
            }
          }
          return theIncompletePacktets;
        }
      }
    }catch(Exception e){
      LOGGER.error( "Error occured in ayncfiletransferprotocol", e );
      return Response.NOK.name();
    }

    // TODO Auto-generated method stub
    return null;
  }
  
  private void notifyIncomingFile(final File aFile){
    getExecutorService().execute( new Runnable() {
      @Override
      public void run() {
        for(iAsyncFileTransferListener theListener : myListeners){
          theListener.fileReceived( aFile );
        }
      }});
  }

  private void sendPacket(final AbstractPeer aPeer, final FilePacket aPacket){
    myService.execute( new Runnable(){
      public void run(){
        try{
          Message theMessage = new Message();
          theMessage.setDestination( aPeer );
          theMessage.setProtocolMessage( true );
          theMessage.setMessage( createMessage( Command.ACCEPT_PACKET + " " + myObjectPerister.toString( aPacket ) ) );
          getMessageProtocol().sendAndWaitForResponse( theMessage, 5, TimeUnit.SECONDS );
        }catch(Exception e){
          LOGGER.error("Error occured while sending packet " + aPacket.getPacket(), e);
        }    
      }
    });
  }

  private String sendMessageTo(AbstractPeer aPeer, String aMessage) throws AsyncFileTransferException{
    try{
      Message theMessage = new Message();
      theMessage.setDestination( aPeer );
      theMessage.setMessage( createMessage( aMessage ));
      theMessage.setProtocolMessage( true );
      return getMessageProtocol().sendAndWaitForResponse( theMessage, 5, TimeUnit.SECONDS );
    }catch(Exception e){
      throw new AsyncFileTransferException("Could not send message", e);
    }
  }

  public void sendFile(File aFile, String aPeer) throws AsyncFileTransferException{
    try{
      AbstractPeer theDestination = getRoutingTable().getEntryForPeer( aPeer ).getPeer();

      //create a new FilePacketIO for this file transfer
      FilePacketIO theIO = FilePacketIO.createForRead( aFile, PACKET_SIZE );
      //store it
      myFilePacketIO.put( theIO.getId(), theIO );

      //init file transfer with other peer
      String theResult = sendMessageTo( theDestination, Command.ACCEPT_FILE.name() + " " + 
                                        aFile.getName()  + " " + 
                                        theIO.getId() + " " + 
                                        theIO.getPacketSize() + " " + 
                                        theIO.getNrOfPackets());

      if(theResult.startsWith( Response.FILE_ACCEPTED.name() )){
        //only continue if the file was accepted by the client
        //now loop over all packets and send them to the other peer
        for(int i=0;i<theIO.getNrOfPackets();i++){
          sendPacket( theDestination, theIO.getPacket( i ) );
        }
      }

      String theResponse = null;
      int j=0;
      while( j++ < MAX_RETRY && !(theResponse = sendMessageTo( theDestination, Command.END_FILE_TRANSFER.name()  + " " + theIO.getId())).equalsIgnoreCase(Response.END_FILE_TRANSFER_OK.name())){
        //if we get here not all packets where correctly delivered resend the missed packets
        String[] thePacketsToResend = theResponse.split(" ");
        for(int i=0;i<thePacketsToResend.length;i++){
          sendPacket(theDestination, theIO.getPacket(Integer.parseInt(thePacketsToResend[i])));
        }
      }
      
      if(!theResponse.equalsIgnoreCase(Response.END_FILE_TRANSFER_OK.name())){
        throw new AsyncFileTransferException("Transferring file failed, not all packets where send successfull, missing packet number '" + theResponse + "'");
      }
    }catch(Exception e){
      throw new AsyncFileTransferException("Could not send file to peer '" + aPeer + "'", e);
    }
  }
  
  public void addFileListener(iAsyncFileTransferListener aListener){
    myListeners.add(aListener);
  }
  
  public void removeListener(iAsyncFileTransferListener aListener){
    myListeners.remove( aListener );
  }
  
  public iAsyncFileTransferHandler getHandler() {
    return myHandler;
  }

  public void setHandler( iAsyncFileTransferHandler aHandler ) {
    myHandler = aHandler;
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }
}