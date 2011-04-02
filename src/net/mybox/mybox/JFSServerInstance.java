/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.mybox.mybox;

import jfs.server.*;
import jfs.conf.*;
import java.net.*;


public class JFSServerInstance extends Thread {

  String serverDir = null;
  JFSConfig config = null;

  JFSServerThread serverThread;
  Socket socket;

  public JFSServerInstance(JFSServerThread _serverThread, Socket _socket, String _serverDir) {
    serverDir = _serverDir;
    serverThread = _serverThread;
    socket = _socket;
    
    config = JFSConfig.getInstance();
		config.clean();
    config.setServerBase(serverDir);

//    start();
  }

  public void run() {

  }
  
}
