package net.mybox.mybox.mbfs;

import java.io.*;
import java.net.*;
import java.util.*;

public class FileServer {

  private static final int port = 4711;

  private static Socket socket;
  
  private static InputStream inStream = null;
  private static DataInputStream dataInStream = null;
  private static OutputStream outStream = null;
  private static DataOutputStream dataOutStream = null;

  private static String dir = "/home/jono/mbfs_test/dir_server";

  private static LinkedList<MyFile> outQueue = new LinkedList<MyFile>();
  // TODO: is the queue needed here, or will syncronized handleInput queue up automagically?

  private synchronized void handleInput(String operation) {

    System.out.println("input operation: " + operation);

    if (operation.equals("c2s")) {
      try {
        String fileName = ByteStream.toString(inStream) + "-copy";
        System.out.println("getting file: " + fileName);
        File file=new File(dir + "/" + fileName);
        ByteStream.toFile(inStream, file);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }
    } else if (operation.equals("clientWants")) {
      try {
        String fileName = ByteStream.toString(inStream);
        System.out.println("client requesting from server: " + fileName);
        File file = new File(dir + "/" + fileName);
        if (file.exists()) {
          outQueue.push(new MyFile(fileName, 10, "file", 123, "s2c"));
          checkQueue();
        }
      } catch (Exception e) {
        //
      }
    } else if (operation.equals("requestServerFileList")) {
      sendServerFileList();
    }

  }

  private void sendServerFileList() {
    System.out.println("sending file list");

    try {
      dataOutStream.writeUTF("requestServerFileList_response");

      // TODO: finish this part
    } catch (Exception e) {
      //
    }
  }

  private void sendFile(MyFile myFile) {

    System.out.println("sending file " + myFile.name);

    File fileObj = new File(dir + "/" + myFile.name);

    try {
      dataOutStream.writeUTF(myFile.action);
      ByteStream.toStream(outStream, myFile.name);
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
            handleInput(dataInStream.readUTF());
          } catch (IOException ioe) {
            System.out.println("Client disconnected");
            // assume this happens because the client socket disconnected
            return;
          }
        }

      }
    }).start();

  }

  /**
   * Constructor for an instance of a server-client connection
   * @param socket
   */
  public FileServer(Socket socket) {

    try {
      inStream = socket.getInputStream();
      outStream = socket.getOutputStream();
    } catch (Exception e) {
      //
    }

    dataInStream = new DataInputStream(inStream);
    dataOutStream = new DataOutputStream(outStream);
   
    startServerInputListenerThread();      
    
//    outQueue.push(new MyFile("serverfile1", 10, "file", 123, "s2c"));
//    outQueue.push(new MyFile("serverfile2", 10, "file", 123, "s2c"));
//    checkQueue();

  }
  
  public static void main(String[] _) {

    try {
      ServerSocket listener = new ServerSocket(port);
      
      while (true) {
        System.out.println("Waiting for client connection");
        FileServer fileServer = new FileServer(listener.accept());
      }
    }
    catch (java.lang.Exception ex) {
      ex.printStackTrace(System.out);
    }
  }


}
