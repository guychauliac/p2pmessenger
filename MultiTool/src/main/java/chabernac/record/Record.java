package chabernac.record;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

public class Record {
    public static final int NUMERIC = 1;
    public static final int ALPHANUMERIC = 2;
    
    private Vector myFields = null;
    private Hashtable myContent = null;
    private int myTotalLength = 0;
    
    public Record(){
        myFields = new Vector();
        myContent = new Hashtable();
        fillRecordName();
        defineFields();
    }
    
    public void fillRecordName(){
    	setField("RECORD", 5, ALPHANUMERIC);
    	String theClass = getClass().getName();
    	theClass = theClass.substring(theClass.lastIndexOf('.') + 1);
    	setValue("RECORD", theClass);
    }
    
    protected void defineFields(){}
    
    public final void setField(String aName, int aLength, int aType){
        Field theField = new Field(aName, aLength, aType);
        myFields.addElement(theField);
        myContent.put(aName, theField);
        myTotalLength += aLength;
    }
    
    public final void setContent(byte[] aByteArray){
        int curPosition = 0;
        Iterator theIterator = myFields.iterator();
        Field theField = null;
        while(theIterator.hasNext()){
            theField = (Field)theIterator.next();
            //Logger.log(Record.class,"Trying to fill field: " + theField.getName());
            if(curPosition + theField.getLength() > aByteArray.length) return;
            byte[] theBytes = theField.getValue();
            System.arraycopy(aByteArray, curPosition, theBytes, 0, theField.getLength());
            curPosition += theField.getLength();
        }
    }
    
    public final byte[] getContent(){
        int curPosition = 0;
        byte[] theBytes = new byte[myTotalLength];
        Iterator theIterator = myFields.iterator();
        Field theField = null;
        while(theIterator.hasNext()){
            theField = (Field)theIterator.next();
            byte[] theSource = theField.getValue();
            System.arraycopy(theSource, 0, theBytes, curPosition, theField.getLength());
            curPosition += theField.getLength();
        }
        return theBytes;
    }
    
    public long getLength(){
    	return myTotalLength;
    }
    
    
    public final void setValue(String aKey, double aValue){
        setValue(aKey, Double.toString(aValue));   
    }
    
    public final void setValue(String aKey, long aValue){
        setValue(aKey, Long.toString(aValue));
    }
    
    public final void setValue(String aKey, String aValue){
        setValue(aKey, aValue.getBytes());
    }
    
    public final void setValue(String aKey, byte[] aValue){
        getField(aKey).setValue(aValue);
    }
    
    public final byte[] getValue(String aKey){
        return getField(aKey).getValue();
    }
    
    private final Field getField(String aKey){
        return (Field)myContent.get(aKey);
    }
    
    public final String getStringValue(String aKey){
        //System.out.println("Key: " + new String(getValue(aKey)));
        return new String(getValue(aKey)).trim();
    }
    
    public final long getLongValue(String aKey){
        return Long.parseLong(getStringValue(aKey));
    }
    
    public final int getIntValue(String aKey){
    	return Integer.parseInt(getStringValue(aKey));
    }
    
    public final double getDoubleValue(String aKey){
        return Double.parseDouble(getStringValue(aKey));
    }
    
    public String toString(){
    	String theString = "<record>\n";
    	for(int i=0;i<myFields.size();i++){
    		theString += myFields.elementAt(i).toString() + "\n";
    	}
    	theString += "</record>\n";
    	return theString;
    }
    
    
    private class Field{
        private static final byte SPACE = ' ';
        private static final byte ZERO = '0';
        
        private String myName;
        private int myLength;
        private byte[] myValue;
        private int myType;
        private byte myClearToken;
        
        public Field(String aName, int aLength, int aType){
            myName = aName;
            myLength = aLength;
            myType = aType;
            myValue = new byte[aLength];
            setClearToken();
            clear();
        }
        
        public String getName(){ return myName; }
        public int getLength(){ return myLength; }
        public byte[] getValue(){ return myValue; }
        
        public void setValue(byte[] aValue){
            //System.out.println("Value: " + new String(aValue));
            //System.out.println("Offset: " + getOffset(aValue.length));
            //System.out.println("Length: " + (aValue.length < myLength ? aValue.length : myLength));
            System.arraycopy(aValue, 0, myValue, getOffset(aValue.length), aValue.length < myLength ? aValue.length : myLength);
        }
        
        private int getOffset(int aLength){
            if(myType == ALPHANUMERIC) return 0;
            if(aLength > myLength) return 0;
            return (myLength - aLength);
        }
        
        private void setClearToken(){
            if(myType == NUMERIC) myClearToken = ZERO;
            else myClearToken = SPACE;
        }
        
        private void clear(){
            for(int i=0;i<myValue.length;i++){ myValue[i] = myClearToken; }
        }
        
        public String toString(){
        	String theString = "<field name='";
        	theString += myName;
        	theString += "' value='";
        	theString += new String(myValue);
        	theString += "'/>";
        	return theString;
        }
    }

}

