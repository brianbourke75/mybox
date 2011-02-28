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

/**
 * This object is a thread instance mantained by the server that represents a connected client
 */
public class ServerClientConnection extends Thread {

  private Server server = null;
  private Socket socket = null;
  private int handle = -1;
  private DataInputStream streamIn = null;
  private DataOutputStream streamOut = null;
  public int failedRequestCount = 0;

  // TODO: make externally read only
  public ServerDB.Account account = null;


  public ServerClientConnection(Server _server, Socket _socket) {
    super();
    server = _server;
    socket = _socket;
    handle = socket.getPort();
  }

  public int getHandle() {
    return handle;
  }

  public boolean attachAccount(String email) {

    account = server.serverDb.getAccountByEmail(email);
/*
    String encPass = Common.encryptPass(password, account.salt);
    System.out.println("Trying password input: " + encPass);
    System.out.println("Comparing to database: " + account.password);

    if (!encPass.equals(account.password)) {
      Server.printMessage("Password incorrect");
      return false;
    }
*/
    if (account == null) {
      Server.printMessage("Account does not exist " + email); // TODO: return false?
      return false;
    }
    
    Server.printMessage("Attached account "+ account + " to handle " + handle);
    return true;
  }


  public void send(String msg) {
    try {
      streamOut.writeUTF(msg);
      streamOut.flush();
    } catch (IOException ioe) {
      Server.printMessage(handle + " ERROR sending: " + ioe.getMessage());
      server.removeClient(handle);
      //stop();
    }
  }


  @Override
  public void run() {
    Server.printMessage("Client attached to socket handle " + handle);
    while (true) {
      try {
        server.handleMessageFromClient(handle, streamIn.readUTF());
      } catch (IOException ioe) {
        //System.out.println(ID + " ERROR reading: " + ioe.getMessage());
        server.removeClient(handle);
        break;
      }
    }
  }

  public void open() throws IOException {
    streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    streamOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
  }

  public void close() throws IOException {
    if (socket != null) {
      socket.close();
    }
    if (streamIn != null) {
      streamIn.close();
    }
    if (streamOut != null) {
      streamOut.close();
    }
  }
}
