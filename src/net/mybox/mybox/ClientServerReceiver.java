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

import java.net.*;
import java.io.*;


// DEPRICATED


/**
 * The Client uses this class to listen to messages from the server
 */
public class ClientServerReceiver extends Thread {

  private Socket socket = null;
  private Client client = null;
  private DataInputStream streamIn = null;
  private Thread thread = null; // is this being used incorrectly?

  public ClientServerReceiver(Client _client, Socket _socket) {
    client = _client;
    socket = _socket;
    open();
    start();
  }
  
  public void start() {
    thread = new Thread(this);
    thread.start();
  }

  public void open() {
    try {
      streamIn = new DataInputStream(socket.getInputStream());
    } catch (IOException ioe) {
      client.printErrorExit("Error getting input stream: " + ioe);
      client.stop();
    }
  }

  public void close() {

    System.out.println("ClientServerReciever is being closed");
    
    try {
      thread = null;
      socket = null;

      if (streamIn != null) {
        streamIn.close();
      }
    } catch (IOException ioe) {
      System.out.println("Error closing input stream: " + ioe);
    }

    System.out.println("close finished");
    
  }

  public void run() {
    while (thread != null) {
      try {
//        client.handleMessageFromServer(streamIn.readUTF());
      } catch (Exception ioe) {
        
        // if the disconnection was lost
        //if (thread == null) {
        //  System.out.println("Listening error in ClientServerReceiver: " + ioe.getMessage());

        // if the connection was not deliberately closed, reconnect
        if (thread != null) {
          //client.stop();
          client.start();
          break;
        }

      }
    }
  }
}
