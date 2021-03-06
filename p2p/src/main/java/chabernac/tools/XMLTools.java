/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.tools;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class XMLTools {
  public static String toXML(Object anObject){
    ByteArrayOutputStream theOut = new ByteArrayOutputStream();
    XMLEncoder theEncoder = new XMLEncoder(theOut);
    theEncoder.writeObject( anObject);
    theEncoder.flush();
    theEncoder.close();
    String theString = theOut.toString();
    theString = theString.replaceAll( "\r", "</brr>" );
    theString = theString.replaceAll( "\n", "</brn>" );
    return theString;
  }
  
  public static Object fromXML(String anXML){
    anXML = anXML.replaceAll( "</brr>", "\r" );
    anXML = anXML.replaceAll( "</brn>", "\n" );
    XMLDecoder theDecoder = new XMLDecoder(new ByteArrayInputStream(anXML.getBytes()));
    return theDecoder.readObject();
  }
}
