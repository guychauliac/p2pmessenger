package chabernac.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileResource extends AbstractResource{
  private File myFile = null;
  
  public FileResource(String aLocation){
   super(aLocation); 
   myFile = new File(getLocation());
  }

  public boolean exists() {
    return myFile.exists();
  }

  public InputStream getInputStream() throws FileNotFoundException{
    return new FileInputStream(myFile);
  }

  public File getFile() {
    return myFile;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return new FileOutputStream(myFile);
  }

}