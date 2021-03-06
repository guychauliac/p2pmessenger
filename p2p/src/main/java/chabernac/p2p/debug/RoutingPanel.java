/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.p2p.debug;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;

import org.apache.log4j.Logger;

import chabernac.protocol.ProtocolException;
import chabernac.protocol.routing.AbstractPeer;
import chabernac.protocol.routing.IRoutingProtocolMonitor;
import chabernac.protocol.routing.RoutingProtocol;
import chabernac.protocol.routing.RoutingTable;
import chabernac.protocol.routing.RoutingTableEntry;
import chabernac.protocol.routing.RoutingTableModel;
import chabernac.thread.DynamicSizeExecutor;
import chabernac.tools.SimpleNetworkInterface;
import chabernac.utils.NetTools;

public class RoutingPanel extends JPanel {
    private static Logger LOGGER = Logger.getLogger(RoutingTable.class);
    private static final long serialVersionUID = 8719080293187156681L;
    private RoutingProtocol myRoutingProtocol = null;
    private ExecutorService myExecutorService = DynamicSizeExecutor.getTinyInstance();
    private JTextArea myInfoArea = new JTextArea();
    private RoutingTableModel myModel = null;
    private JTable myTable = null;

    public RoutingPanel(RoutingProtocol aRoutingProtocol){
        myRoutingProtocol = aRoutingProtocol;
        buildGUI();
    }

    private void buildGUI(){
        setLayout( new BorderLayout() );
        buildNorthPanel();
        buildCenterPanel();
        buildSouthPanel();
        addListeners();
        setBorder( new TitledBorder("Routing Table") );
    }

    private void addListeners(){
        myRoutingProtocol.setRoutingProtocolMonitor( new RoutingProtocolMonitor() );
    }

    private void buildSouthPanel() {
        JScrollPane theScrollPane = new JScrollPane(myInfoArea);
        myInfoArea.setRows( 5 );
        add(theScrollPane, BorderLayout.SOUTH);
    }

    private void buildCenterPanel() {
        myModel = new RoutingTableModel(myRoutingProtocol.getRoutingTable());
        myTable = new JTable(myModel); 
        ColorRenderer theRenderer = new ColorRenderer();
        myTable.setDefaultRenderer(String.class, theRenderer);
        myTable.setDefaultRenderer(Integer.class, theRenderer);
        JScrollPane theScrollPane = new JScrollPane(myTable);
        add(theScrollPane, BorderLayout.CENTER);
        myTable.addMouseListener(new BlockPeerMouseListener());
    }

    private void buildNorthPanel() {
        JPanel theButtonPanel = new JPanel();
        theButtonPanel.setLayout( new GridLayout(-1,3) );

        theButtonPanel.add( new JButton(new StartLocalSystemScan()) );
        theButtonPanel.add( new JButton(new StartRemoteSystemScan()) );
        theButtonPanel.add( new JButton(new DetectRemoteSystem()) );
        theButtonPanel.add( new JButton(new ExchangeRoutingTable()) );
        theButtonPanel.add( new JButton(new SendUDPAnnouncement()) );
        theButtonPanel.add( new JButton(new StopAction()) );
        theButtonPanel.add( new JButton(new StartAction()) );
        theButtonPanel.add( new JButton(new ScanSuperNodesAction()) );
        theButtonPanel.add( new JButton(new ShowRoutingTableEntryHistory()) );
        theButtonPanel.add( new JButton(new PrintRoutingTable()) );
        theButtonPanel.add(new JButton(new CompareHosts()));
        theButtonPanel.add(new JButton(new PrintLocalNetworkInterfaces()));

        add(theButtonPanel, BorderLayout.NORTH);
    }

    public class StartLocalSystemScan  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -8773364986791187155L;

        public StartLocalSystemScan(){
            putValue( Action.NAME, "Local scan" );
        }

        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.scanLocalSystem();
                }
            });
        }
    }  

    public class StartRemoteSystemScan  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = 6011412995328892025L;

        public StartRemoteSystemScan(){
            putValue( Action.NAME, "remote scan" );
        }

        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.scanRemoteSystem( true );
                }
            });
        }
    }

    public class SendUDPAnnouncement  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -596393164465244545L;

        public SendUDPAnnouncement(){
            putValue( Action.NAME, "send UDP announcement" );
        }

        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.sendUDPAnnouncement(true);
                }
            });
        }
    }

    public class ExchangeRoutingTable  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -9165061106192729191L;

        public ExchangeRoutingTable(){
            putValue( Action.NAME, "Exchange routing table" );
        }

        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.exchangeRoutingTable();
                }
            });
        }
    }

    public class DetectRemoteSystem  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -6293979828266668777L;
        public DetectRemoteSystem(){
            putValue( Action.NAME, "Detect remote system" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.detectRemoteSystem();
                }
            });
        }
    }

    public class StopAction  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -4399121816282915298L;
        public StopAction(){
            putValue( Action.NAME, "Stop" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.stop();
                }
            });
        }
    }

    public class StartAction  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -7099808806129502391L;
        public StartAction(){
            putValue( Action.NAME, "Start" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    try {
                        myRoutingProtocol.start();
                    } catch (ProtocolException e) {
                        //TODO should we log the exception somewhere
                    }
                }
            });
        }
    }

    public class ScanSuperNodesAction  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = 6431633852407007558L;
        public ScanSuperNodesAction(){
            putValue( Action.NAME, "Scan super nodes" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            myExecutorService.execute( new Runnable(){
                public void run(){
                    myRoutingProtocol.scanSuperNodes();
                }
            });
        }
    }

    public class ShowRoutingTableEntryHistory  extends AbstractAction{
        /**
         * 
         */
        private static final long serialVersionUID = -8653407321194756153L;
        public ShowRoutingTableEntryHistory(){
            putValue( Action.NAME, "Show history" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            RoutingTableEntryHistoryDialog theDialog = new RoutingTableEntryHistoryDialog(myRoutingProtocol.getRoutingTable());
            theDialog.setVisible( true );
        }
    }

    public class PrintRoutingTable  extends AbstractAction{
        private static final long serialVersionUID = -8653407321194756153L;
        public PrintRoutingTable(){
            putValue( Action.NAME, "Print routing table" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            PrintWriter theWriter = null;
            try{
                theWriter = new PrintWriter( new File("routingtable.txt") );
                theWriter.println(myRoutingProtocol.getRoutingTable().toString());
                theWriter.flush();
            } catch ( FileNotFoundException e ) {
                LOGGER.error("Could not write routing table to txt file", e);
            } finally {
                if(theWriter != null){
                    theWriter.close();
                }
            }
        }
    }
    
    public class CompareHosts  extends AbstractAction{
        private static final long serialVersionUID = -8653407321194756153L;
        public CompareHosts(){
            putValue( Action.NAME, "Compare hosts" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            PrintWriter theWriter = null;
            try{
                theWriter = new PrintWriter( new File("comparehosts.txt") );
                List<RoutingTableEntry> theEntries = myRoutingProtocol.getRoutingTable().getEntries();
                for(int i=0;i<theEntries.size();i++){
                    RoutingTableEntry theEntry = theEntries.get(i);
                    for(int j=i+1;j<theEntries.size();j++){
                        RoutingTableEntry theSecondEntry = theEntries.get(j);
                        if(theEntry.getPeer().isSameEndPointAs( theSecondEntry.getPeer() )){
                            theWriter.println(theEntry.getPeer() + " has same end point as " + theSecondEntry.getPeer()) ;
                        }
                    }
                    
                }
                
                theWriter.flush();
            } catch ( FileNotFoundException e ) {
                LOGGER.error("Could not write routing table to txt file", e);
            } finally {
                if(theWriter != null){
                    theWriter.close();
                }
            }
        }
    }
    
    public class PrintLocalNetworkInterfaces  extends AbstractAction{
        private static final long serialVersionUID = -8653407321194756153L;
        public PrintLocalNetworkInterfaces(){
            putValue( Action.NAME, "Print exposed network interfaces" );
        }
        @Override
        public void actionPerformed( ActionEvent anE ) {
            PrintWriter theWriter = null;
            try{
                theWriter = new PrintWriter( new File("interfaces.txt") );
                for(SimpleNetworkInterface theInterface :  NetTools.getLocalExposedInterfaces()){
                    theWriter.println(theInterface.getName() + " " + theInterface.toString());
                }
                theWriter.flush();
            } catch ( SocketException e ) {
                LOGGER.error("Could not extract local network interfaes", e);
            } catch ( FileNotFoundException e ) {
                LOGGER.error("Could not write local network intefaces to file", e);
            } finally {
                if(theWriter != null){
                    theWriter.close();
                }
            }
        }
    }

    private class RoutingProtocolMonitor implements IRoutingProtocolMonitor{
        private StringBuilder myStringBuilder = new StringBuilder();

        @Override
        public void detectingRemoteSystemStarted() {
            myStringBuilder.insert(0, "Detecting remote system\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void exchangingRoutingTables() {
            myStringBuilder.insert(0, "Exchanging routing tables\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void localSystemScanStarted() {
            myStringBuilder.insert(0, "Scanning local system\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void remoteSystemScanStarted() {
            myStringBuilder.insert(0, "Scanning remote system\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void scanStarted( AbstractPeer aPeer ) {
            //      myStringBuilder.append( "Scan started '" + aPeer.getHosts() + ":" + aPeer.getPort() + "\r\n" );
            //      myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void peerFoundWithScan( AbstractPeer aPeer ) {
            myStringBuilder.insert(0, "Peer found after scan '" + aPeer.getEndPointRepresentation() + "\r\n");
            myInfoArea.setText( myStringBuilder.toString() );
        }

        @Override
        public void sendingUDPAnnouncement() {
            myStringBuilder.insert(0, "Sending udp announcement\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );

        }

        @Override
        public void scanningSuperNodes() {
            myStringBuilder.insert(0, "Scanning super nodes\r\n" );
            myInfoArea.setText( myStringBuilder.toString() );

        }

    }

    private class ColorRenderer extends JLabel implements TableCellRenderer{
        private static final long serialVersionUID = 7571899561399741995L;

        @Override
        public Component getTableCellRendererComponent(JTable anTable,
                                                       Object anValue, boolean isSelected, boolean anHasFocus, int anRow,
                                                       int anColumn) {
            setOpaque( true );
            setBackground( Color.white );

            RoutingTableEntry theEntry = myModel.getRoutingTableEntryAtRow( anRow);

            if(!theEntry.isReachable()) setBackground( new Color(255,200,200) );
            if(theEntry.getHopDistance() == 0) setBackground( new Color(100,100,255) );
            if(theEntry.getHopDistance() > 1 && theEntry.getHopDistance() < RoutingTableEntry.MAX_HOP_DISTANCE ) setBackground( Color.yellow );


            setText(anValue.toString());

            return this;
        }
    }

    private class BlockPeerMouseListener extends MouseAdapter{
        @Override
        public void mouseReleased(MouseEvent anEvent) {
            if(anEvent.getButton() == MouseEvent.BUTTON3){
                RoutingTableEntry theEntry = myModel.getRoutingTableEntryAtRow(myTable.getSelectedRow());
                int theResult = JOptionPane.showConfirmDialog(null, "Remove peer with id '" + theEntry.getPeer().getPeerId() + "' ?");
                if(theResult == JOptionPane.OK_OPTION){
                    myRoutingProtocol.getLocalUnreachablePeerIds().add(theEntry.getPeer().getPeerId());
                    //now put the hop distance to the max so that a new route is calculated
                    myRoutingProtocol.getRoutingTable().removeRoutingTableEntry(theEntry);
                }
            }
        }
    }

}
