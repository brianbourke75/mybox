package net.mybox.mybox.mbfs;

import java.io.*;
import java.net.Socket;
import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class FileClient {

  private static final int port = 4711;
  private static final String host = "localhost";

  private static String dir = "/home/jono/mbfs_test/dir_client";

  private OutputStream outStream = null;
  private DataOutputStream dataOutStream = null;
  private InputStream inStream = null;
  private DataInputStream dataInStream = null;

  public LinkedList<MyFile> outQueue = new LinkedList<MyFile>();
  
  public HashMap<String, MyFile> serverFileList = new HashMap<String, MyFile>();  // should nullify when done since global
  public HashMap<String, MyFile> clientFileList = new HashMap<String, MyFile>();
  
  private String receivedResponseCommand = null;
  
  private synchronized void handleInput(String operation) {

    //receivedResponseCommand = operation;
    System.out.println("input operation: " + operation);

    if (operation.equals("s2c")) {
      try {
        String file_name = ByteStream.toString(inStream);// + "-copy";
        System.out.println("getting file: " + file_name);
        File file=new File(dir + "/" + file_name);
        ByteStream.toFile(inStream, file);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }
    } else if (operation.equals("requestServerFileList_response")) {

      try {
        String jsonString = ByteStream.toString(inStream);
        serverFileList = decodeFileList(jsonString);
        System.out.println("Recieved jsonarray: " + jsonString);
      } catch (Exception e) {
        System.out.println("Exception while recieving jsonArray");
      }
    }

    receivedResponseCommand = operation;  // set this at the end so it is known that it finished
  }


  public static HashMap<String, MyFile> decodeFileList(String input) {

    HashMap<String, MyFile> result = new HashMap<String, MyFile>();

    JSONParser parser = new JSONParser();
    ContainerFactory containerFactory = new ContainerFactory(){
      @Override
      public List creatArrayContainer() {
        return new LinkedList();
      }
      @Override
      public Map createObjectContainer() {
        return new LinkedHashMap();
      }
    };

    try {
      List thisList = (List)parser.parse(input, containerFactory);

      Iterator iter = thisList.iterator();
      while(iter.hasNext()){
        String serialized = (String)iter.next();
        MyFile myFile = MyFile.fromSerial(serialized);
//        System.out.println("Saving to hash: " + myFile);
        result.put(myFile.name, myFile);
      }
    }
    catch(Exception pe){
      System.out.println(pe);
    }

    return result;
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
  
  
  private void populateLocalFileList() {
    System.out.println("getting local file list");

    try {

      // get the local file list

      File thisDir = new File(dir);

      File[] files = thisDir.listFiles(); // TODO: use some FilenameFilter

      JSONArray jsonArray = new JSONArray();
      
      for (File thisFile : files) {
        MyFile myFile = new MyFile(thisFile.getName());
        myFile.modtime = thisFile.lastModified();
        
        if (thisFile.isFile())
          myFile.type = "file";
        else if (thisFile.isDirectory())
          myFile.type = "directory";

        clientFileList.put(myFile.name, myFile);
      }

    } catch (Exception e) {
      System.out.println("Error populating local file list");
    }
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

  public void twoWayMergeTest() {
    // assumes client and server file lists are populated

    for (String fname : serverFileList.keySet()) {
      MyFile myFile = serverFileList.get(fname);
      myFile.action = "clientWants";
      outQueue.push(myFile);
    }

    for (String fname : clientFileList.keySet()) {
      MyFile myFile = clientFileList.get(fname);
      myFile.action = "c2s";
      outQueue.push(myFile);
    }

    // TODO: invalidate client and server lists here?
  }
  
  public static void main(String[] args) {
    
    FileClient fileClient = new FileClient();

    // full sync test

    // get client (local) file list
    fileClient.populateLocalFileList();
    
//    fileClient.outQueue.push(new MyFile("clientfile1", 10, "file", "c2s"));
//    fileClient.outQueue.push(new MyFile("clientfile2", 10, "file", "c2s"));

    // get server file list
    boolean listReturned = fileClient.blockingDiscussion("requestServerFileList");

//    fileClient.outQueue.push(new MyFile("serverfile1", 10, "file", "clientWants"));
//    fileClient.outQueue.push(new MyFile("serverfile2", 10, "file", "clientWants"));

    // call CollectUpdates to label the actions accordingly

    // hack until CollectUpdates is ready
    fileClient.twoWayMergeTest();
    
    fileClient.checkQueue();

  }
}
