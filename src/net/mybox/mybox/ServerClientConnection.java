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
import java.util.*;
import org.json.simple.*;

/**
 * This object is a thread instance mantained by the server that represents a connected client
 */
public class ServerClientConnection extends Thread {

  private Server parent = null;
  private Socket socket = null;
  private int handle = -1;
  public int failedRequestCount = 0;

  // TODO: make externally read only
  public ServerDB.Account account = null;

  private InputStream inStream = null;
  private DataInputStream dataInStream = null;
  private OutputStream outStream = null;
  private DataOutputStream dataOutStream = null;
  
  private static LinkedList<MyFile> outQueue = new LinkedList<MyFile>();

  private static String localDir = null;


  public ServerClientConnection(Server _server, Socket _socket) {
    //super();

    parent = _server;
    socket = _socket;
    handle = socket.getPort();

    try {
      inStream = socket.getInputStream();
      outStream = socket.getOutputStream();
    } catch (Exception e) {
      System.out.println("Error setting socket streams");
    }

    dataInStream = new DataInputStream(inStream);
    dataOutStream = new DataOutputStream(outStream);

    startServerInputListenerThread();

  }


  private synchronized void handleInput(Common.Signal input) {

    Server.printMessage("(" + Common.now() + ") Client " + handle + ": " + input.toString());

    if (input == (Common.Signal.c2s)) {
      try {
        String fileName = ByteStream.toString(inStream);
        String fileTimeString = ByteStream.toString(inStream);

        // TODO: create underlying directory if the file needs it

        long fileTime = Long.valueOf(fileTimeString);
        System.out.println("getting file: " + fileName + " modtime " + fileTime);
        File file=new File(localDir + "/" + fileName);
        ByteStream.toFile(inStream, file);
        file.setLastModified(fileTime);

        parent.spanCatchupOperation(handle, account.id, input, fileName);
      }
      catch (Exception e){
        System.out.println("c2s operation failed: " + e.getMessage());
      }
    } else if (input ==(Common.Signal.clientWantsToSend)) {
      try {
        String fileName = ByteStream.toString(inStream);
        String fileTimeString = ByteStream.toString(inStream);
        long fileTime = Long.valueOf(fileTimeString);

        System.out.println("clientWantsToSend: " + fileName + " modtime " + fileTime);
        File file=new File(localDir + "/" + fileName);

        sendCommandToClient(Common.Signal.clientWantsToSend_response);
        ByteStream.toStream(outStream, fileName);

        if (file.isFile() && file.lastModified() == fileTime) {
          ByteStream.toStream(outStream, "no");
        } else {
          ByteStream.toStream(outStream, "yes");
        }
      }
      catch (Exception e){
        System.out.println("c2s operation failed: " + e.getMessage());
      }
    } else if (input == (Common.Signal.clientWants)) {
      try {
        String fileName = ByteStream.toString(inStream);
        System.out.println("client requesting from server: " + fileName);
        File file = new File(localDir + "/" + fileName);
        if (file.exists()) {
          outQueue.push(new MyFile(fileName, 10, "file", "s2c"));
          checkQueue();
        }
      } catch (Exception e) {
        System.out.println("clientWants operation failed: " + e.getMessage());
      }
    } else if (input == (Common.Signal.deleteOnServer)) {  // handles files and directories
      try {
        String fileName = ByteStream.toString(inStream);
        System.out.println("client requested deletion on server: " + fileName);
        File item = new File(localDir + "/" + fileName);
        if (item.isDirectory())
          Common.deleteLocalDirectory(item);
        else if(item.exists())  // assume it is a file
          item.delete();
        else
          System.out.println("unable to find item on server to delete");

        parent.spanCatchupOperation(handle, account.id, input, fileName);
      } catch (Exception e) {
        System.out.println("deleteOnServer operation failed: " + e.getMessage());
      }
    } else if (input == (Common.Signal.renameOnServer)) {  // handles files and directories
      try {
        String oldName = ByteStream.toString(inStream);
        String newName = ByteStream.toString(inStream);
        System.out.println("client requested rename on server: (" + oldName + ") to (" + newName + ")");
        File oldFile = new File(localDir + "/" + oldName);
        File newFile = new File(localDir + "/" + newName);
        if (oldFile.exists()) {
          oldFile.renameTo(newFile);
        }

        parent.spanCatchupOperation(handle, account.id, input, oldName + "->" + newName);

      } catch (Exception e) {
        System.out.println("renameOnServer operation failed: " + e.getMessage());
      }
    } else if (input == (Common.Signal.createDirectoryOnServer)) {
      try {
        String name = ByteStream.toString(inStream);
        System.out.println("client requesting create directory on server: " + name);
        Common.createLocalDirectory(localDir + "/" + name);

        parent.spanCatchupOperation(handle, account.id, input, name);
      } catch (Exception e) {
        System.out.println("createDirectoryOnServer operation failed: " + e.getMessage());
      }
    } else if (input == (Common.Signal.requestServerFileList)) {
      sendServerFileList();
    } else if (input == (Common.Signal.attachaccount)) {

      String args = null;

      try {
        args = ByteStream.toString(inStream);
      } catch (Exception e) {
        //
      }
        HashMap attachInput = Common.jsonDecode(args);
        String email = (String)attachInput.get("email");
//        String password = (String)attachInput.get("password");

        JSONObject jsonOut = new JSONObject();
        jsonOut.put("serverMyboxVersion", Common.appVersion);
        
        if (attachAccount(email)) {
          jsonOut.put("status", "success");
          jsonOut.put("quota", account.quota);
          jsonOut.put("salt", account.salt);

          parent.updateMultiMap(account.id, handle);
        } else {
          jsonOut.put("status", "failed");
          jsonOut.put("error", "invalid account");
        }

        try {
          sendCommandToClient(Common.Signal.attachaccount_response);
          ByteStream.toStream(outStream, jsonOut.toJSONString());
        }
        catch (Exception e) {
          //
        }

        Server.printMessage("attachaccount_response: " + jsonOut);
      
    } else {
      Server.printMessage("unknown command: "+ input);
      failedRequestCount++;
    }

  }


  /**
   * 
   * @param operation is the original command from the master client
   * @param arg
   */
  public void sendCatchup(Common.Signal operation, String arg) {
    // TODO: handle file transfers
    
    if (operation == (Common.Signal.c2s)) {
      try {
        System.out.println("catchup s2c to client ("+ handle + "): " + arg);
        
        File localFile = new File(localDir + "/" + arg);
        if (localFile.exists()) {
          sendCommandToClient(Common.Signal.s2c);
          ByteStream.toStream(outStream, arg);
          ByteStream.toStream(outStream, localFile.lastModified() + "");
          ByteStream.toStream(outStream, localFile);
        }
        
      } catch (Exception e) {
        System.out.println("catchup s2c to client failed: " + e.getMessage());
      }
      
    } else if (operation == (Common.Signal.deleteOnServer)) {  // handles files and directories?
      try {
        System.out.println("catchup delete to client ("+ handle + "): " + arg);
        sendCommandToClient(Common.Signal.deleteOnClient);
        ByteStream.toStream(outStream, arg);
      } catch (Exception e) {
        System.out.println("catchup delete to client failed: " + e.getMessage());
      }
    } else if (operation == (Common.Signal.renameOnServer)) {  // handles files and directories?
      try {
        System.out.println("catchup rename to client ("+ handle + "): " + arg);
//        String[] args = arg.split(" -> ");
        sendCommandToClient(Common.Signal.renameOnClient);
        ByteStream.toStream(outStream, arg);
      } catch (Exception e) {
        System.out.println("catchup rename to client failed: " + e.getMessage());
      }
    } else if (operation == (Common.Signal.createDirectoryOnServer)) {
      try {
        System.out.println("catchup createDirectoryOnClient ("+ handle + "): " + arg);
        sendCommandToClient(Common.Signal.createDirectoryOnClient);
        ByteStream.toStream(outStream, arg);
      } catch (Exception e) {
        System.out.println("catchup createDirectoryOnClient failed: " + e.getMessage());
      }
    } else {
      Server.printMessage("unknown command: "+ operation);
      failedRequestCount++;
    } 
    
  }


  private void sendServerFileList() {

    System.out.println("getting local file list for: " + localDir);

    try {
      List<MyFile> files = Common.getFileListing(new File(localDir));
      
      JSONArray jsonArray = new JSONArray();

      for (MyFile thisFile : files) {
        System.out.println(" " + thisFile.name);
        jsonArray.add(thisFile.serialize());
      }

      sendCommandToClient(Common.Signal.requestServerFileList_response);
      ByteStream.toStream(outStream, jsonArray.toJSONString());

      System.out.println("local file list: " + jsonArray.size() + " files");

    } catch (Exception e) {
      System.out.println("Error when getting local file list " + e.getMessage());
    }

  }

  private void sendFile(MyFile myFile) {

    System.out.println("sending file " + myFile.name);

    File fileObj = new File(localDir + "/" + myFile.name);

    try {
      sendCommandToClient(Common.Signal.s2c); //myFile.action
      ByteStream.toStream(outStream, myFile.name);
      ByteStream.toStream(outStream, fileObj.lastModified() + "");
      ByteStream.toStream(outStream, fileObj);
    }catch (Exception e) {
      System.out.println("error sending file");
    }

    checkQueue();
  }


  private synchronized void checkQueue() {
    System.out.println("Checking queue " + outQueue.size());

    if (outQueue.size() > 0) {
      System.out.println("top queue action: " + outQueue.peek().action);
      if (outQueue.peek().action.equals("s2c"))
        sendFile(outQueue.pop());
    }
  }

  private void startServerInputListenerThread() {

    // start a new thread for getting input from the client (anonymous ServerInThread)
    new Thread(new Runnable() {
      public void run() {

        while (true) {
          try {
            System.out.println("Thread waiting to readUTF");
            handleInput(Common.Signal.get(dataInStream.readByte()));
          } catch (IOException ioe) {
            System.out.println("Client disconnected");
            // assume this happens because the client socket disconnected
            return;
          }
        }

      }
    }).start();

  }

  public int getHandle() {
    return handle;
  }

  public boolean attachAccount(String email) {

    account = parent.serverDb.getAccountByEmail(email);
    localDir = account.serverdir;

    if (account == null) {
      Server.printMessage("Account does not exist " + email); // TODO: return false?
      return false;
    }
    
    Server.printMessage("Attached account "+ account + " to handle " + handle);
    Server.printMessage("Local server storage in: " + account.serverdir);
    return true;
  }


  public void sendCommandToClient(Common.Signal signal) {
    try {
      dataOutStream.writeByte(signal.index());
//      dataOutStream.writeUTF(command);
    //  dataOutStream.flush();
    } catch (IOException ioe) {
      Server.printMessage(handle + " ERROR sending: " + ioe.getMessage());
      parent.removeClient(handle);
      //stop();
    }
  }


  @Override
  public void run() {
    Server.printMessage("Client attached to socket handle " + handle);

    /*
    while (true) {
      try {
        parent.handleMessageFromClient(handle, dataInStream.readUTF());
      } catch (IOException ioe) {
        //System.out.println(ID + " ERROR reading: " + ioe.getMessage());
        parent.removeClient(handle);
        break;
      }
    }
     *
     */
  }

  public void open() throws IOException {
//    dataInStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
//    dataOutStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
  }

  public void close() throws IOException {
    if (socket != null) {
      socket.close();
    }
    /*
    if (streamIn != null) {
      streamIn.close();
    }
    if (streamOut != null) {
      streamOut.close();
    }
     */
  }
}
