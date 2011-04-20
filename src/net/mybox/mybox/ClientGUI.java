/**
    Mybox version 0.1.0
    https://github.com/mybox/mybox

    Copyright (C) 2011  Jono Finger (jono@foodnotblogs.com)

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not it can be found here:
    http://www.gnu.org/licenses/gpl-2.0.html
 */

package net.mybox.mybox;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.URL;
import org.apache.commons.cli.*;

/**
 * The GUI version of the client
 */
public class ClientGUI extends java.awt.Frame {

  private static Client client = null;
  
  private static Image icon_syncing = createImage("box_blue.png", "icon");
  private static Image icon_ready = createImage("box_green.png", "icon");
  private static Image icon_error = createImage("box_red.png", "icon");
  private static Image icon_disconnected = createImage("box_blank.png", "icon");

  private static boolean debugMode = false;

  static MenuItem pauseItem = new MenuItem("Pause Syncing");
  static MenuItem syncnowItem = new MenuItem("Sync Now");
  static MenuItem connectionItem = new MenuItem("Connect");  // for debugging

  static {
    pauseItem.setEnabled(false);
    syncnowItem.setEnabled(false);
  }
  
  
  final TrayIcon trayIcon = new TrayIcon(icon_disconnected);// = icon_disconnected;// = new TrayIcon(createImage("box_blank.png", "tray icon"));

  /** Creates new form ClientAWT */
  public ClientGUI(String configFile) {

    // try to make the swing components look better
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {

    }

    this.setTitle("Mybox Preferences");

    initComponents();
    jTabbedPrefs.setSelectedIndex(1); // show the messages first

    client = new Client();
    client.config(configFile);
    client.clientGui = this;


    placeTrayIconAWT();

    if (!debugMode)
      client.start();

    this.setVisible(debugMode);
  }

  public void printMessage(String message) {
    textAreaMessages.append(message + "\n");
  }

  public void printMessage_(String message) {
    textAreaMessages.append(message);
  }

  public static void printErrorExit(String message) {

    System.err.println("clientawt error: " + message);// temp hack

    // TODO: fix this so the dialog pops up
    Dialog dialog = new Dialog((ClientGUI)client.clientGui, message);
    //dialog.run();

    // should write this to a log
    System.exit(1);
  }

  /**
   * Set the status icon on the system tray
   * @param status
   */
  public void setStatus(ClientStatus status) {
    if (status == ClientStatus.SYNCING || status == ClientStatus.CONNECTING) {
      trayIcon.setImage(icon_syncing);
    } else if (status == ClientStatus.READY) {
      trayIcon.setImage(icon_ready);
      
      if (!pauseItem.isEnabled())
        pauseItem.setEnabled(true);
      
      if (!syncnowItem.isEnabled())
        syncnowItem.setEnabled(true);

      valueUser.setText(client.GetEmail());
      valueServer.setText(client.GetServer());
      valuePort.setText(client.GetServerPort()+"");

      connectionItem.setLabel("Disconnect");

    } else if (status == ClientStatus.ERROR) {
      trayIcon.setImage(icon_error);
    } else if (status == ClientStatus.PAUSED) {
      trayIcon.setImage(icon_disconnected);

      if (syncnowItem.isEnabled())
        syncnowItem.setEnabled(false);
    }
    else {//DISCONNECTED
      trayIcon.setImage(icon_disconnected);

      valueUser.setText("");
      valueServer.setText("");
      valuePort.setText("");

      if (pauseItem.isEnabled())
        pauseItem.setEnabled(false);

      if (syncnowItem.isEnabled())
        syncnowItem.setEnabled(false);

      connectionItem.setLabel("Connect");
    }
  }

  // Create an image from a resource path
  protected static Image createImage(String path, String description) {
    URL imageURL = ClientGUI.class.getResource("Resources/"+path);

    if (imageURL == null) {
      System.err.println("Resource not found: " + path);
      return null;
    } else {
      return (new ImageIcon(imageURL, description)).getImage();
    }
  }


  private void launchFileBrowser() {

    Desktop desktop = null;

    if (Desktop.isDesktopSupported()){
      File dir = new File(client.GetClientDir());
      desktop = Desktop.getDesktop();

      try {
        desktop.open(dir);
      }
      catch (Exception ex) {
        System.err.println(ex.getMessage());
      }
    }

  }

  private void placeTrayIconAWT() {

    //Check the SystemTray support
    if (!SystemTray.isSupported()) {
      System.out.println("SystemTray is not supported");
      return;
    }
    final PopupMenu popup = new PopupMenu();
    
    final SystemTray tray = SystemTray.getSystemTray();

    // Create a popup menu components
    MenuItem aboutItem = new MenuItem("About Mybox");
    MenuItem opendirItem = new MenuItem("Open Directory");
    MenuItem prefsItem = new MenuItem("Preferences");
    MenuItem exitItem = new MenuItem("Quit Mybox");
    


    //Add components to popup menu

    popup.add(opendirItem);
    popup.add(pauseItem);
    popup.add(syncnowItem);

    popup.addSeparator();
    popup.add(prefsItem);
    popup.add(aboutItem);
    popup.add(connectionItem);
    popup.add(exitItem);

    trayIcon.setImageAutoSize(true);
    trayIcon.setToolTip("Mybox");


    trayIcon.setPopupMenu(popup);

    try {
      tray.add(trayIcon);
    } catch (AWTException e) {
      System.out.println("TrayIcon could not be added.");
      return;
    }

    trayIcon.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        launchFileBrowser();
      }
    });

    aboutItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        //JOptionPane.showMessageDialog(null, "Mybox!");

        Dialog dialog = new Dialog((ClientGUI)client.clientGui, "Mybox...");
        dialog.setVisible(true);

        //MessageDialog dialog = new InfoMessageDialog(null, "Mybox", "... is awesome!");//ErrorMessageDialog(null, "Error", message);
        //dialog.setTitle("About Mybox");
        //dialog.run();
        //dialog.hide();
      }
    });

    prefsItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        //window.showAll();
        ((ClientGUI)client.clientGui).setVisible(true);
      }
    });

    syncnowItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        client.FullSync();
      }
    });

    opendirItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        launchFileBrowser();
      }
    });

    pauseItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (pauseItem.getLabel().equals("Pause Syncing")) {
          client.pause();
          pauseItem.setLabel("Unpause Syncing");
        } else if (pauseItem.getLabel().equals("Unpause Syncing")) {
          client.unpause();
          pauseItem.setLabel("Pause Syncing");
        }
      }
    });

    connectionItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (connectionItem.getLabel().equals("Connect")) {
          client.start();
        } else if (connectionItem.getLabel().equals("Disconnect")) {
          client.stop();
        }
      }
    });

    exitItem.addActionListener(new ActionListener() {

      public void actionPerformed(ActionEvent e) {
        tray.remove(trayIcon);
        System.exit(0);
      }
    });
  }



    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    buttonClose = new java.awt.Button();
    jTabbedPrefs = new javax.swing.JTabbedPane();
    panelAccount = new java.awt.Panel();
    labelUser = new java.awt.Label();
    label2 = new java.awt.Label();
    label3 = new java.awt.Label();
    labelServer = new java.awt.Label();
    labelPort = new java.awt.Label();
    valueUser = new java.awt.Label();
    valueServer = new java.awt.Label();
    valuePort = new java.awt.Label();
    textAreaMessages = new java.awt.TextArea();

    addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitForm(evt);
      }
    });

    buttonClose.setLabel("Close");
    buttonClose.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        buttonCloseActionPerformed(evt);
      }
    });

    labelUser.setText("User name");

    label2.setText("label2");

    label3.setText("label3");

    labelServer.setText("Server");

    labelPort.setText("Server port");

    valueUser.setText(null);

    valueServer.setText(null);

    valuePort.setText(null);

    javax.swing.GroupLayout panelAccountLayout = new javax.swing.GroupLayout(panelAccount);
    panelAccount.setLayout(panelAccountLayout);
    panelAccountLayout.setHorizontalGroup(
      panelAccountLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(panelAccountLayout.createSequentialGroup()
        .addGap(43, 43, 43)
        .addGroup(panelAccountLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(panelAccountLayout.createSequentialGroup()
            .addComponent(labelPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap())
          .addGroup(panelAccountLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelAccountLayout.createSequentialGroup()
              .addComponent(labelServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addContainerGap())
            .addGroup(panelAccountLayout.createSequentialGroup()
              .addComponent(labelUser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addContainerGap(322, Short.MAX_VALUE)))))
      .addGroup(panelAccountLayout.createSequentialGroup()
        .addGap(80, 80, 80)
        .addGroup(panelAccountLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
          .addComponent(valueServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(valueUser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(331, Short.MAX_VALUE))
      .addGroup(panelAccountLayout.createSequentialGroup()
        .addGap(80, 80, 80)
        .addComponent(valuePort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(331, Short.MAX_VALUE))
    );
    panelAccountLayout.setVerticalGroup(
      panelAccountLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(panelAccountLayout.createSequentialGroup()
        .addGap(51, 51, 51)
        .addComponent(labelUser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(valueUser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(15, 15, 15)
        .addComponent(labelServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(valueServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(19, 19, 19)
        .addComponent(labelPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(valuePort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(70, Short.MAX_VALUE))
    );

    jTabbedPrefs.addTab("Account", panelAccount);
    jTabbedPrefs.addTab("Messages", textAreaMessages);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(buttonClose, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jTabbedPrefs, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 445, Short.MAX_VALUE))
        .addContainerGap())
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jTabbedPrefs, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 21, Short.MAX_VALUE)
        .addComponent(buttonClose, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap())
    );

    pack();
  }// </editor-fold>//GEN-END:initComponents

    /** Exit the Application */
    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
      this.setVisible(false);
      //System.exit(0);
    }//GEN-LAST:event_exitForm

    private void buttonCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonCloseActionPerformed
      this.setVisible(false);
      //System.exit(0);
    }//GEN-LAST:event_buttonCloseActionPerformed

    static String configFile = null;

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {

      Options options = new Options();
      options.addOption("c", "config", true, "configuration directory (default=~/.mybox)");
      options.addOption("a", "apphome", true, "application home directory");
      options.addOption("d", "debug", false, "enable debug mode");
      options.addOption("h", "help", false, "show help screen");
      options.addOption("V", "version", false, "print the Mybox version");

      CommandLineParser line = new GnuParser();
      CommandLine cmd = null;

      String configDir = Client.defaultConfigDir;

      try {
        cmd = line.parse(options, args);
      } catch( Exception exp ) {
        System.err.println( exp.getMessage() );
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "Client", options );
        return;
      }


      if (cmd.hasOption("h")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( Client.class.getName(), options );
        return;
      }

      if (cmd.hasOption("V")) {
        Client.printMessage("version " + Common.appVersion);
        return;
      }

      if (cmd.hasOption("d")) {
        debugMode = true;
      }

      if (cmd.hasOption("a")) {
        String appHomeDir = cmd.getOptionValue("a");
        try {
          Common.updatePaths(appHomeDir);
        } catch (FileNotFoundException e) {
          printErrorExit(e.getMessage());
        }

        Client.updatePaths();
      }

      if (cmd.hasOption("c")) {
        configDir = cmd.getOptionValue("c");
      }

      Client.setConfigDir(configDir);

      
      java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          new ClientGUI(configFile);
        }
      });
    }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private java.awt.Button buttonClose;
  private javax.swing.JTabbedPane jTabbedPrefs;
  private java.awt.Label label2;
  private java.awt.Label label3;
  private java.awt.Label labelPort;
  private java.awt.Label labelServer;
  private java.awt.Label labelUser;
  private java.awt.Panel panelAccount;
  private java.awt.TextArea textAreaMessages;
  private java.awt.Label valuePort;
  private java.awt.Label valueServer;
  private java.awt.Label valueUser;
  // End of variables declaration//GEN-END:variables

}
