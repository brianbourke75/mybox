package net.mybox.mybox.mbfs;

import java.io.*;
import java.net.Socket;
import java.util.*;


public class FileClient {

  private static final int port = 4711;
  private static final String host = "localhost";

  private static String dir = "/home/jono/mbfs_test/dir_client";

  private OutputStream outStream = null;
  private DataOutputStream dataOutStream = null;
  private InputStream inStream = null;
  private DataInputStream dataInStream = null;

  public LinkedList<MyFile> outQueue = new LinkedList<MyFile>();
  
  public HashMap<String, MyFile> serverFileList = new HashMap<String, MyFile>();

  private String receivedResponseCommand = null;
  
  private synchronized void handleInput(String operation) {

    receivedResponseCommand = operation;
    System.out.println("input operation: " + operation);

    if (operation.equals("s2c")) {
      try {
        String file_name = ByteStream.toString(inStream) + "-copy";
        System.out.println("getting file: " + file_name);
        File file=new File(dir + "/" + file_name);
        ByteStream.toFile(inStream, file);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }
    } else if (operation.equals("requestServerFileList_respose")) {
      ;
      // receive giant serialized string blob and save to local hash table
    }

  }

  public synchronized void checkQueue() {
    System.out.println("Checking queue " + outQueue.size());

    if (outQueue.size() > 0) {
      if (outQueue.peek().action.equals("c2s"))
        sendFile(outQueue.pop());
      else if (outQueue.peek().action.equals("clientWants"))
        requestFile(outQueue.pop());
    }
  }

  private void requestFile(MyFile myFile) {
    System.out.println("requesting file " + myFile.name);

    try{
      dataOutStream.writeUTF("clientWants");
      ByteStream.toStream(outStream, myFile.name);
    } catch (Exception e) {
      System.out.println("error requesting file");
    }

    checkQueue();
  }


  public boolean blockingDiscussion(String messageToServer) {

    // TODO: set the timeout very high
    
    int pollseconds = 1;
    int pollcount = 10;

    String expectedReturnCommand = messageToServer + "_response";

    System.out.println("initiating blocking discussion for: " + messageToServer);
    try {
      dataOutStream.writeUTF(messageToServer);
    } catch (Exception e) {
      //
    }

    for (int i=0; i<pollcount; i++){
      try{Thread.sleep(pollseconds * 1000);}catch(Exception ee){}
      if (expectedReturnCommand.equals(receivedResponseCommand)) {
        receivedResponseCommand = null;
        System.out.println("Response recieved. Unblocking.");
        return true;
      }
    }

    receivedResponseCommand = null;

    return false;
  }

  private void sendFile(MyFile myFile) {

    System.out.println("sending file " + myFile.name);

    File fileObj = new File(dir + "/" + myFile.name);

    try {
      dataOutStream.writeUTF(myFile.action);
      ByteStream.toStream(outStream, myFile.name);
      ByteStream.toStream(outStream, fileObj);
    } catch (Exception e) {
      System.out.println("error sending file");
    }

    checkQueue();
  }

  
  private void startClientInputListenerThread() {

    // start a new thread for getting input from the server (anonymous ClientInThread)
    new Thread(new Runnable() {
      public void run() {

        while (true) {
          try {
            System.out.println("Thread waiting to readUTF");
            handleInput(dataInStream.readUTF());
          } catch (Exception e) {
            System.out.println("Listening error in ClientInThread: " + e.getMessage());
          }
        }

      }
    }).start();
  }

  public FileClient() {
    
    try {
      Socket socket = new Socket(host, port);
      outStream = socket.getOutputStream();
      inStream = socket.getInputStream();
    }
    catch (Exception ex) {
      //
    }

    dataOutStream = new DataOutputStream(outStream);
    dataInStream = new DataInputStream(inStream);

    startClientInputListenerThread();

//    checkQueue();
    
  }
  
  public static void main(String[] args) {
    
    FileClient fileClient = new FileClient();

    // full sync test

    fileClient.outQueue.push(new MyFile("clientfile1", 10, "file", 123, "c2s"));
    fileClient.outQueue.push(new MyFile("clientfile2", 10, "file", 123, "c2s"));

    boolean listReturned = fileClient.blockingDiscussion("requestServerFileList");

    // get remote file list
    fileClient.outQueue.push(new MyFile("serverfile1", 10, "file", 123, "clientWants"));
    fileClient.outQueue.push(new MyFile("serverfile2", 10, "file", 123, "clientWants"));
    
    fileClient.checkQueue();

  }
}
