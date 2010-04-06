/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class IOTools {
  private static Logger LOGGER = Logger.getLogger(IOTools.class); 
  
  public void writeObjectToFileAsXML(File aFile, Object anObject){
    //TODO implement writeObjectToFile
  }
  
  public Object loadObjectFromFileAsXML(File aFile){
    //TODO implement loadObjectFromFile
    return null;
  }
  
  public static void copyStream(InputStream anInputStream, OutputStream anOutputStream) throws IOException{
    copyStream( anInputStream, anOutputStream, null );
  }
  
  public static void copyStream(InputStream anInputStream, OutputStream anOutputStream, iTransferListener aListener) throws IOException{
    byte[] theBytes = new byte[1024];
    int length;
    long theBytesTransferred = 0;
    while((length = anInputStream.read(theBytes)) != -1){
      anOutputStream.write(theBytes, 0, length);
      if(aListener != null){
        theBytesTransferred += length;
        aListener.bytesTransfered( theBytesTransferred );
      }
    }
    anOutputStream.flush();
  }
  
  public static List<String> loadFileAsList(File aFile){
    List<String> theList = new ArrayList< String >();
    if(!aFile.exists()) return theList;
    
    BufferedReader theReader = null;
    try{
      theReader = new BufferedReader(new FileReader(aFile));
      String theLine = null;
      while((theLine = theReader.readLine()) != null){
        theList.add(theLine);
      }
    } catch ( FileNotFoundException e ) {
      LOGGER.error("Could not open fixed ip list file", e);
    } catch ( IOException e ) {
      LOGGER.error("Could not read fixed ip list file", e);
    } finally {
      if(theReader != null){
        try {
          theReader.close();
        } catch ( IOException e ) {
          LOGGER.error("Could not close fixed ip list file", e);
        }
      }
    }
    return theList;
  }
}
