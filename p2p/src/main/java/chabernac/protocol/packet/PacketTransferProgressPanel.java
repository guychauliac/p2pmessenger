/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.packet;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.text.NumberFormat;

import javax.swing.JPanel;

import org.apache.log4j.Logger;

import chabernac.protocol.packet.AbstractTransferState.State;
import chabernac.tools.Percentage;

public class PacketTransferProgressPanel extends JPanel {
  private static Logger LOGGER = Logger.getLogger(PacketTransferProgressPanel.class);
  private static final long serialVersionUID = -2109149737877351501L;
  private final AbstractTransferState myTransferState;
  final static int HEIGHT = 25;
  private final static int CELLS = 20;
  private final static int ROUNDING_RADIUS = 5;
  private final static Font FONT = new Font("Serif", Font.BOLD, 14);
  private final static NumberFormat FORMAT = NumberFormat.getInstance();
  private final static Color CELL_COLOR = new Color(140,250,140);

  static{
    FORMAT.setMaximumFractionDigits( 0 );
    FORMAT.setMinimumFractionDigits( 0 );
  }

  public PacketTransferProgressPanel( AbstractTransferState aHandler ) {
    super();
    myTransferState = aHandler;
    addListeners();
  }

  private void addListeners(){
    myTransferState.addTransferStateListener(new PacketTransferListener() );
  }


  public Dimension getPreferredSize(){
    return new Dimension(super.getPreferredSize().width, HEIGHT);
  }

  public void paint(Graphics aGraphics){
    BufferedImage theImage = new BufferedImage( getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);

    Graphics g = theImage.getGraphics();

    int theWidth = getWidth();

    g.setColor( new Color(238,238,238) );
    g.fillRect(  0, 0, theImage.getWidth(), theImage.getHeight() );

    double theCellWidth = Math.floor((double)theWidth / (double)CELLS);

    AbstractTransferState.State theState = myTransferState.getState();

    Percentage thePercentage = new Percentage( 0, 0 );
    try {
      if(theState != State.PENDING && theState != State.CANCELLED){
        thePercentage = myTransferState.getPacketTransferState().getPercentageComplete();  
      }

      double theNrOfCompletedCells = (double)CELLS * (double)thePercentage.getDenominator() / (double)thePercentage.getDivisor();
      int theIntNrOfCompletedCells = (int)Math.floor(theNrOfCompletedCells);
      int theHeightOfCurrentCell = (int)Math.floor((theNrOfCompletedCells - theIntNrOfCompletedCells) * (HEIGHT - 2));

      for(int i=0;i<CELLS;i++){
        if(i<theIntNrOfCompletedCells){
          g.setColor( CELL_COLOR );
          g.fillRoundRect( (int)Math.floor(i * theCellWidth + 1), 1, (int)Math.floor(theCellWidth - 2), HEIGHT - 2, ROUNDING_RADIUS, ROUNDING_RADIUS);
        } else if(i==theIntNrOfCompletedCells){
          g.setColor( CELL_COLOR );
          g.fillRoundRect( (int)Math.floor(i * theCellWidth + 1), HEIGHT - 2 - theHeightOfCurrentCell, (int)Math.floor(theCellWidth - 2), theHeightOfCurrentCell, ROUNDING_RADIUS, ROUNDING_RADIUS);

        }
        g.setColor( Color.gray );
        g.drawRoundRect( (int)Math.floor( i * theCellWidth + 1), 1, (int)Math.floor(theCellWidth - 2), HEIGHT - 2, ROUNDING_RADIUS, ROUNDING_RADIUS);
      }


      g.setColor( Color.black );
      g.setFont( FONT );

      String theString = myTransferState.getTransferDescription() + " " + FORMAT.format( thePercentage.getPercentage() * 100) + " %";

      theString += " " + theState.name();

      /*
      if(theState.getState() == FileTransferState.State.PAUSED){
        theString += " PAUZED";
      }  else if(theState.getState() == FileTransferState.State.FAILED){
        theString += " FAILED";
      }
       */

      g.drawString( theString, 10, HEIGHT - 4 );
    } catch ( Exception e ) {
      LOGGER.error("an error occured while painting transfer progress panel", e);
    }

    if(theState == State.STOPPED){
      theImage = blur( theImage );
      g = theImage.getGraphics();
    }

    aGraphics.drawImage( theImage, 0, 0, null );
  }

  private BufferedImage blur(BufferedImage anImage){
    float data[] = { 0.0625f, 0.125f, 0.0625f,
        0.125f , 0.25f , 0.125f,
        0.0625f, 0.125f, 0.0625f };
    //    float data[] = { 0.120f, 0.120f, 0.120f,
    //                     0.120f , 0.165f , 0.120f,
    //                     0.120f, 0.120f, 0.120f };

    Kernel kernel = new Kernel(3, 3, data);
    ConvolveOp convolve = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
    return convolve.filter(anImage, null);
  }

  //  private class FilePacketVisualizer extends MouseAdapter {
  //    private FilePacketVisualizerFrame myVisualizer = null;
  //
  //    @Override
  //    public void mouseClicked( MouseEvent aE ) {
  //      if(myVisualizer == null){
  //        myVisualizer =new FilePacketVisualizerFrame( myHandler );
  //      }
  //      myVisualizer.setVisible( true );
  //    }
  //  }

  private class PacketTransferListener implements iTransferStateListener {

    @Override
    public void transferStateChanged(TransferState aState) {
      repaint();
    }
  }

}
