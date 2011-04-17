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

import com.jcraft.jsch.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.commons.cli.*;
import org.json.simple.*;
import org.json.simple.parser.*;


/**
 * The status of the client
 */
enum ClientStatus {
  CONNECTING,DISCONNECTED,READY,SYNCING,PAUSED,ERROR
}

/**
 * The executable client connects to a running server
 */
public class Client {
  
  // object state
  private Socket serverConnectionSocket = null;

  private Thread clientInputListenerThread = null;
  private DirectoryListener dirListener = null;
  private GatherDirectoryUpdates gatherDirectoryUpdatesThread = null;
  private boolean waiting = false;
  public static Object clientGui = null;
  private String lastReceivedOperation = null;

  // user settings
  private ClientAccount account = new ClientAccount();

  // static system specific settings
  private final String ServerDir = "Mybox";
  public static final String defaultClientDir = System.getProperty("user.home") + "/Mybox";
  public static final String defaultConfigDir = System.getProperty("user.home") + "/.mybox";
  public static final String defaultConfigFile = defaultConfigDir + "/mybox_client.conf";
  public static final String logFile = defaultConfigDir + "/mybox_client.log";
  public static final String lastSyncFile = defaultConfigDir + "/lasysync.txt";




  private static String localDir = defaultClientDir;

  private OutputStream outStream = null;
  private DataOutputStream dataOutStream = null;
  private InputStream inStream = null;
  private DataInputStream dataInStream = null;

  // TODO: make this a list of only name=>action items instead of myfiles
  // actually queue should eventually only be a list of files to send
  public LinkedList<MyFile> outQueue = new LinkedList<MyFile>();
  
  // filenames that are expected to be recieved. removed once that are finished downloading
  public Set<String> incoming = new HashSet<String>();

  private long lastSync = 0;  // TODO: set this / store this somewhere in DB/filesystem

  // serverFileList
  public HashMap<String, MyFile> S = new HashMap<String, MyFile>();  // should nullify when done since global
  // clientFileList
  public HashMap<String, MyFile> C = new HashMap<String, MyFile>();

  // filename(s) => actions
  public HashMap<String, String> Cgather = new HashMap<String, String>();



  static {
    updatePaths();
  }




  // FILE CLIENT METHODS


  private synchronized boolean updateLastSync() {
    lastSync = (new Date()).getTime();

    try {
      FileOutputStream fos = new FileOutputStream(lastSyncFile);
      DataOutputStream dos = new DataOutputStream(fos);
      dos.writeLong(lastSync);
      dos.close();
    }
    catch (Exception e) {
      return false;
    }
    
    return true;
  }
  
  private boolean getLastSync() {

    try {
      FileInputStream fin = new FileInputStream(lastSyncFile);
      DataInputStream din = new DataInputStream(fin);
      lastSync = din.readLong();
      din.close();
    }
    catch (Exception e) {
      return false;
    }

    return true;
  }

  private synchronized void handleInput(String operation) {

    System.out.println("input operation: " + operation);

    if (operation.equals("s2c")) {
      try {
        String file_name = ByteStream.toString(inStream);
        String fileTimeString = ByteStream.toString(inStream);

        long fileTime = Long.valueOf(fileTimeString);

        System.out.println("getting file: " + file_name);
        File file=new File(localDir + "/" + file_name);
        ByteStream.toFile(inStream, file);
        file.setLastModified(fileTime);
        incoming.remove(file_name);
        for (String in : incoming)  System.out.print("  " + in);
      }
      catch (Exception e){
        System.out.println("Operation failed: " + e.getMessage());
      }
    } else if (operation.equals("requestServerFileList_response")) {

      try {
        String jsonString = ByteStream.toString(inStream);
        S = decodeFileList(jsonString);
        System.out.println("Recieved jsonarray: " + jsonString);
      } catch (Exception e) {
        System.out.println("Exception while recieving jsonArray");
      }
    } else if (operation.equals("attachaccount_response")) {

      String jsonString = null;
      
      try {
        jsonString = ByteStream.toString(inStream);
      } catch (Exception e) {
        //
      }

      HashMap map = Common.jsonDecode(jsonString);

      if (!((String)map.get("status")).equals("success"))
        printWarning("Unable to attach account. Server response: "+ map.get("error"));
      else {
        account.salt= (String)map.get("salt");
        System.out.println("set account salt to: " + account.salt);
        
        // inline check of mybox versions
        if (!Common.appVersion.equals((String)map.get("serverMyboxVersion")))
          printErrorExit("Client and Server Mybox versions do not match");
      }

    }
    else {
      printMessage("unknown command from server");
    }
    
    //if (command.endsWith("_response")) 
    //  receivedResponseCommand = command;

    lastReceivedOperation = operation;
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
        result.put(myFile.name, myFile);
      }
    }
    catch(Exception pe){
      System.out.println(pe);
    }

    return result;
  }

  public synchronized void checkQueue() {

    if (outQueue.size() > 0) {
//      if (outQueue.peek().action.equals("c2s"))
        sendFile(outQueue.pop());
//      else if (outQueue.peek().action.equals("clientWants"))
//        requestFile(outQueue.pop().name);
//      else if (outQueue.peek().action.equals("deleteOnServer"))
//        deleteOnServer(outQueue.pop().name);

      checkQueue();
    }
  }

  private void sendFile(MyFile myFile) {

    System.out.println("sending file " + myFile.name);

    File fileObj = new File(localDir + "/" + myFile.name);

    try {
      sendCommandToServer(myFile.action);
      ByteStream.toStream(outStream, myFile.name);
      ByteStream.toStream(outStream, fileObj.lastModified() +"");
      ByteStream.toStream(outStream, fileObj);
    } catch (Exception e) {
      System.out.println("error sending file");
    }
  }

  
  private void deleteLocal(String name) {
    System.out.println("Deleting local file " + name);

    File thisFile = new File(localDir + "/" + name);
    if (thisFile.exists())
      thisFile.delete();
      
  }
  

  private void deleteOnServer(String name) {
    System.out.println("Telling server to delete file " + name);

    try{
      sendCommandToServer("deleteOnServer");
      ByteStream.toStream(outStream, name);
    } catch (Exception e) {
      System.out.println("error requesting server file delete");
    }
  }
  
  private void renameOnServer(String oldName, String newName) {
    System.out.println("Telling server to rename file " + oldName + " to " + newName);

    try{
      sendCommandToServer("renameOnServer");
      ByteStream.toStream(outStream, oldName);
      ByteStream.toStream(outStream, newName);
    } catch (Exception e) {
      System.out.println("error requesting server file rename");
    }
  }

  private void requestFile(String name) {
    System.out.println("Requesting file " + name);

    try{
      sendCommandToServer("clientWants");
      ByteStream.toStream(outStream, name);
      incoming.add(name);
      for (String in : incoming)  System.out.println("  " + in);
    } catch (Exception e) {
      System.out.println("error requesting file");
    }
  }

  private void showQueue() {
    System.out.println("Showing queue");
    for (MyFile myFile : outQueue) {
      System.out.println(myFile);
    }
  }




  private void populateLocalFileList() {
    try {

      File thisDir = new File(localDir);

      File[] files = thisDir.listFiles(); // TODO: use some FilenameFilter

      //System.out.println("Listing local files: " + files.length);

      JSONArray jsonArray = new JSONArray();

      for (File thisFile : files) {
        //System.out.println(" " + thisFile.getName());
        MyFile myFile = new MyFile(thisFile.getName());
        myFile.modtime = thisFile.lastModified();

        if (thisFile.isFile())
          myFile.type = "file";
        else if (thisFile.isDirectory())
          myFile.type = "directory";

        C.put(myFile.name, myFile);
      }

      //System.out.println("local file list: " + C.size() + " files");

    } catch (Exception e) {
      System.out.println("Error populating local file list " + e.getMessage());
    }

  }


  private void startClientInputListenerThread() {
    // start a new thread for getting input from the server (anonymous ClientInThread)

    //if (clientInputListenerThread != null)
    //  return;


    clientInputListenerThread =

    new Thread(new Runnable() {
      @Override
      public void run() {

        while (true) {
          try {
            System.out.println("Client waiting for command");
            handleInput(dataInStream.readUTF());
          } catch (IOException ioe) {
            System.out.println("Listening error in ClientInThread: " + ioe.getMessage());
            clientInputListenerThread = null;
            attemptConnection(5);
            // TODO: make this reconnection work
          }
        }

      }
    });

    clientInputListenerThread.start();
  }


  private void GatherSync() {

    // TODO: handle directories
    
    // Cgather: name(s) => action
    
    //printMessage("disabling listener");
    //disableDirListener();

    setStatus(ClientStatus.SYNCING);

    printMessage("Gather sync started  " + Common.now() );
    
    for (String fname : Cgather.keySet()) {
      if (Cgather.get(fname).equals("renamed")) {
        String[] names = fname.split(" -> ");
        renameOnServer(names[0], names[1]);
      } else if (Cgather.get(fname).equals("deleted")) {
        deleteOnServer(fname);
      } else if (Cgather.get(fname).equals("modified") || Cgather.get("key").equals("created")) {

        if (incoming.contains(fname)) {
          System.out.println("Ignoring update to " + fname + " since it seems to have come from server");
        }
        else {
          MyFile myFile = new MyFile(fname, 0, "file", "c2s");
          outQueue.addFirst(myFile);
        }
        
      }
    }
    
    
    checkQueue();

    updateLastSync();

    System.out.println("Setting lasySync to " + lastSync);

    Cgather.clear();

    //printMessage("enableing listener since sync is done");
    //enableDirListener();
      
    printMessage("Sync finished " + Common.now() );

    setStatus(ClientStatus.READY);
  }

  public void FullSync() {

    printMessage("disabling listener");
    disableDirListener(); // hack while incoming set gets figured out

    setStatus(ClientStatus.SYNCING);

    printMessage("Full sync started  " + Common.now() );


    // TODO: if lastSysc is 0 or bogus, favorite file creations over deletions

    // TODO: update all time comparisons to respect server/client time differences

    populateLocalFileList();
    boolean listReturned = serverDiscussion("requestServerFileList", null);

    System.out.println("comparing C=" + C.size() + " to S="+ S.size());

//    LinkedList<MyFile> DoLocal = new LinkedList<MyFile>();
//    LinkedList<MyFile> TellServer = new LinkedList<MyFile>();
    LinkedList<MyFile> SendToServer = new LinkedList<MyFile>();

    // TODO: handle directories
    // strange case = when there is the same name item but it is a file on the client and dir on server

    for (String name : C.keySet()) {

      MyFile c = C.get(name);

      if (S.containsKey(name)) {
        MyFile s = S.get(name);

        System.out.println(" lastsync="+lastSync+" c.modtime=" + c.modtime + " s.modtime=" + s.modtime);

        if (c.modtime != s.modtime) {
          if (c.modtime > lastSync) {
            if (s.modtime > lastSync) {
              System.out.println(name + " = conflict type 1");
            } else {
              System.out.println(name + " = transfer from client to server");
              c.action = "c2s";
              SendToServer.addFirst(c);
            }
          } else {
            if (s.modtime > c.modtime) {
              System.out.println(name + " = transfer from server to client 1");
//              s.action = "clientWants";
              requestFile(s.name);
//              TellServer.addFirst(s);
            } else {
              System.out.println(name + " = conflict type 2");
            }
          }
        }
        S.remove(name);
      } else {
        if (c.modtime > lastSync) {
          System.out.println(name + " = transfer from client to server");
          c.action = "c2s";
          SendToServer.addFirst(c);
        }
        else {
          System.out.println(name + " = remove from client");
//          c.action = "deleteOnClient";
//          DoLocal.addFirst(c);

          deleteLocal(name);
        }
      }
    }

    for (String name : S.keySet()) {  // which is now S-C
      MyFile s = S.get(name);

      if (s.modtime > lastSync) {
        System.out.println(name + " = transfer from server to client 2");
        requestFile(s.name);
        //s.action = "clientWants";
        //TellServer.addFirst(s);
      } else {
        System.out.println(name + " = remove from server");
        deleteOnServer(s.name);
//        s.action = "deleteOnServer";
//        TellServer.addFirst(s);
      }
    }

//    System.out.println("DoLocal");
//    for (MyFile item : DoLocal) {
//
//      if (item.action.equals("deleteOnClient")) {
//        File thisFile = new File(localDir + "/" + item.name);
//        if (thisFile.exists())
//          thisFile.delete();
//      }
//      System.out.println(item);
//    }
//
//    System.out.println("TellServer");
//    for (MyFile item : TellServer) {
//      outQueue.addLast(item);
//      System.out.println(item);
//    }

    System.out.println("SendToServer");
    for (MyFile item : SendToServer) {

      if (incoming.contains(item.name)) {
        System.out.println("Ignoring update to " + item.name + " since it seems to have come from server");
      } else {
        outQueue.addFirst(item);
        System.out.println(item);
      }

    }

    checkQueue();

    updateLastSync(); // perhaps this shoud happen after all inbound have finished

    System.out.println("Setting lasySync to " + lastSync);

    C.clear();
    S.clear();

    printMessage("enableing listener since sync is done");
    enableDirListener();
    
    printMessage("Sync finished " + Common.now() );

    setStatus(ClientStatus.READY);
  }










  /**
   * Set paths to local files based on the home application path
   */
  public static void updatePaths() {

    String incPath = Common.getPathToInc();

    String osName = System.getProperty("os.name").toLowerCase();

    if (osName.equals("linux")) {
//      clientUnisonCommand = incPath + "/unison-2.27.57-linux";
//      sshCommand = incPath + "/ssh-ident.bash";
    } else if (osName.startsWith("windows")) {
//      clientUnisonCommand = incPath + "/unison-2.27.57-win_gui.exe";
//      sshCommand = "inc" + "\\ssh-ident.bat";
//      sshCommand = incPath + System.getProperty("file.separator") + "ssh-ident.bat";
      // TODO: make this path absolute. might be a unison argument parsing bug
    } else if (osName.startsWith("mac os x")) {
//      clientUnisonCommand = incPath + "/unison-2.40.61-macXX";
//      sshCommand = incPath + "/ssh-ident.bash";
    } else {
      throw new RuntimeException("Unsupported operating system: " + osName);
    }

  }

  // accessors

  public String GetClientDir() {
    return account.directory;
  }
  
  public String GetEmail() {
    return account.email;
  }

  public String GetServer() {
    return account.serverName;
  }

  public int GetServerPort() {
    return account.serverPort;
  }


  // helpers


  private static void log(String message) {
    // TODO: change this to be a static PrintWriter opened at construction time
    PrintWriter out = null;

    try {
      out = new PrintWriter(new FileWriter(logFile, true));
    } catch (Exception e) {
      System.out.println("Unable to open log file: " + e);
    }

    out.println(message);

    out.close();
  }

  public static void printErrorExit(String message) {

    log(message);
    if (clientGui == null) {
      System.out.println(message);
      System.exit(1);
    }
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printErrorExit(message);
  }

  public static void printMessage_(String message) {
    log(message);
    if (clientGui == null)
      System.out.print(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage_(message);
  }

  public static void printMessage(String message) {
    log(message);
    if (clientGui == null)
      System.out.println(message);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).printMessage(message);
  }

  public static void printWarning(String message) {
    log(message);
    System.out.println(message);
  }

  // methods

  /**
   * Set the status of the client. Mostly used for GUIs
   * @param status
   */
  private void setStatus(ClientStatus status) {
    if (clientGui == null)
      System.out.println("STATUS: "+ status);
    else if (clientGui instanceof ClientGUI)
      ((ClientGUI)clientGui).setStatus(status);
  }

  /**
   * Read the client config file and set this class's members accordingly
   * @param configFile
   */
  private void readConfig(String configFile) {

    Properties properties = new Properties();

    try {
      properties.load(new FileInputStream(configFile));
    } catch (IOException e) {
      // do something
    }

    // get values

    account.serverName = properties.getProperty("serverName");  // returns NULL when not found
    account.serverPort = Integer.parseInt(properties.getProperty("serverPort"));
    account.email = properties.getProperty("email");
    account.directory = properties.getProperty("directory");
    account.salt = properties.getProperty("salt");

    // check values

    if (account.serverName == null || account.serverName.isEmpty())
      printErrorExit("Unable to determine host from settings file");

    if (account.email == null || account.email.isEmpty())
      printErrorExit("Unable to determine email");

    if (account.directory == null)
      account.directory = defaultClientDir;

    File dirTest = new File(account.directory);
    if (!dirTest.exists())
      printErrorExit("Directory " + account.directory +" does not exist");
  }

  /**
   * Pause the directory listener and synchronization
   */
  public void pause() {
    printMessage("pausing sync listeners/timers");
    disableDirListener();
    setStatus(ClientStatus.PAUSED);
  }

  /**
   * Unpause the directory listener and synchronization
   */
  public void unpause() {
    enableDirListener();
    FullSync();
  }

  /**
   * Enable the directory listener
   */
  public void enableDirListener() {
    printMessage("Listening on directory " + account.directory);
    if (dirListener == null)
      dirListener = new DirectoryListener(account.directory, this);
    else
      dirListener.listen();
  }

  /**
   * Disable the directory listener
   */
  public void disableDirListener() {
    if (dirListener != null)
      dirListener.pause();
  }


  /**
   * Send a message to the server
   * @param command
   */
  public void sendCommandToServer(String command) {
    try {
      dataOutStream.writeUTF(command);
      //dataOutStream.flush();
    } catch (Exception e) {
      printWarning("Sending error: " + e.getMessage());
    }
  }

  /**
   * Called by the WaitGather thread to indicate the waiting has finished and a sync can begin.
   */
  public void waitHasFinished() {
    waiting = false;

    Client.printMessage("GatherDirectoryUpdates has finished");

    GatherSync();

    // this happens when the sync is done since sync() waits
//    sendMessageToServer("syncfinished");
  }
  
  /**
   * Called whenever a file in the directory is updated
   * @param action
   * @param items
   */
  public void directoryUpdate(String action, String items) {
    printMessage("DirectoryUpdate " + action + " " + items);

    if (items.charAt(items.length()-1) == '/')  // hack: remove trailing slash from jnotify?
      items = items.substring(0, items.length()-2);

    if (Cgather.containsKey(items) && Cgather.get(items).equals(action))
      ; // dont bother adding it to the list since it is already there
    else
      Cgather.put(items, action); // this should overwrite items that area already in the list

    if (gatherDirectoryUpdatesThread != null && waiting)
      gatherDirectoryUpdatesThread.deactivate();

    gatherDirectoryUpdatesThread = new GatherDirectoryUpdates(this, 2);
    waiting = true;
  }

  /**
   * This is a helper function for ClientSetup that makes a quick connection to the server
   * just so the ssh user can be determined.
   * @param serverName
   * @param serverPort
   * @param email
   * @return The account
   */
  public ClientAccount startGetAccountMode(String serverName, int serverPort, String email) {

    if (serverName == null)
      printErrorExit("Client not configured");
    
    account.serverName = serverName;
    account.serverPort = serverPort;
    account.email = email;

    account.salt = "0"; // temp hack

    printMessage("Establishing connection to port "+ serverPort +". Please wait ...");

    try {
      serverConnectionSocket = new Socket(account.serverName, account.serverPort);
      printMessage("Connected: " + serverConnectionSocket);
      dataOutStream = new DataOutputStream(serverConnectionSocket.getOutputStream());
    } catch (UnknownHostException uhe) {
      printErrorExit("Host unknown: " + uhe.getMessage());
    } catch (IOException ioe) {
      printErrorExit("Unexpected exception: " + ioe.getMessage());
    }

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);
//    jsonOut.put("password", password);

//    client = new ClientServerReceiver(this, serverConnectionSocket);

//    if (!serverDiscussion("attachaccount " + jsonOut) || account.serverPOSIXaccount == null)
//      printErrorExit("Unable to determine server POSIX account");

    return account;
  }

  /**
   * Send a message to the server and wait for its response. Waiting polls once a second for 10 seconds.
   * Note that this does not handle the response, it only waits for it.
   * @param messageToServer
   * @return false if no response
   */
  public boolean serverDiscussion(String messageToServer, String argument) {

    int pollseconds = 1;
    int pollcount = 10;

    String expectedReturnCommand = messageToServer + "_response";  //iArgs[0] + "_response";

    System.out.println("serverDiscussion starting with expected return: " + expectedReturnCommand);
    System.out.println("serverDiscussion sending message to server: " + messageToServer);

    sendCommandToServer(messageToServer);
    if (argument != null)
      try {
        ByteStream.toStream(outStream, argument);
      }
      catch (Exception e) {
        //
      }

    for (int i=0; i<pollcount; i++){
      try{Thread.sleep(pollseconds * 1000);}catch(Exception ee){}
      if (expectedReturnCommand.equals(lastReceivedOperation)) {
        System.out.println("serverDiscussion returning true with: " + lastReceivedOperation);
        lastReceivedOperation = null;
        return true;
      }
    }

    System.out.println("serverDiscussion returning false");
    lastReceivedOperation = null;

    return false;
  }




  /**
   * Attempt to connect to the server port and attach the user
   * @param pollInterval The amount of seconds between socket connection retries
   */
  public void attemptConnection(int pollInterval) {

    if (account.serverName == null)
      printErrorExit("Client not configured");

    setStatus(ClientStatus.CONNECTING);

    printMessage("Establishing connection to "+ account.serverName +":"+ account.serverPort+" ...");

    // repeatedly attempt to connect to the server
    while (true) {
      try {
        serverConnectionSocket = new Socket(account.serverName, account.serverPort);
        //dataOutStream = new DataOutputStream(serverConnectionSocket.getOutputStream());

        outStream = serverConnectionSocket.getOutputStream();
        inStream = serverConnectionSocket.getInputStream();

        dataOutStream = new DataOutputStream(outStream);
        dataInStream = new DataInputStream(inStream);

        break; // reachable if there is no exception thrown
      } catch (IOException ioe) {
        printMessage("There was an error reaching server. Will try again in " + pollInterval + " seconds...");
        try{Thread.sleep(1000*pollInterval);}catch(Exception ee){}
      }
    }
    
    printMessage("Connected: " + serverConnectionSocket);

    startClientInputListenerThread();

    
//    client = new ClientServerReceiver(this, serverConnectionSocket);

    JSONObject jsonOut = new JSONObject();
    jsonOut.put("email", account.email);

//    jsonOut.put("password", password);
    
    // TODO: make sure if the attachAccount fails, no files can be transfered
    
    if (!serverDiscussion("attachaccount", jsonOut.toString()))
      printErrorExit("Unable to attach account");

    // checking that the mybox versions are the same happens in handleMessageFromServer

    loadNativeLibs();

    getLastSync();

    enableDirListener();
    
    printMessage("Client ready. Startup sync.");
    setStatus(ClientStatus.READY);

    // perform an initial sync to catch all the files that have changed while the client was off
    FullSync();
  }

  public void start() {
    attemptConnection(5);
  }

  public void stop() {

    printMessage("Stopping client");
    setStatus(ClientStatus.DISCONNECTED);

    try {
/*
      if (client != null) {
        client.close();
        client = null;
      }
  */
      if (serverConnectionSocket != null) {
        serverConnectionSocket.close();
        serverConnectionSocket = null;
      }
      
      if (dataOutStream != null) {
        dataOutStream.close();
      }
    } catch (IOException ioe) {
      printWarning("Error closing ...");
    }

  }

  /**
   * Load the native libraries. Most notably JNotify.
   */
  private void loadNativeLibs() {

    String incPath = Common.getPathToInc();
    if (incPath == null)
      printErrorExit("Unable to open the include directory: " + incPath);
    
    java.lang.System.setProperty("java.library.path", incPath);

    try {
      java.lang.reflect.Field fieldSysPath = ClassLoader.class.getDeclaredField( "sys_paths" );
      fieldSysPath.setAccessible( true );
      fieldSysPath.set( null, null );
    } catch (Exception e) {
      printErrorExit("There was an error setting the java library path");
    }

  }

  /**
   * Constructor
   */
  public Client() {
    setStatus(ClientStatus.DISCONNECTED);
  }

  public void config(String configFile) {
    readConfig(configFile);
  }


  /**
   * Handle the command line args and instantiate the Client
   * @param args
   */
  public static void main(String args[]) {

    Options options = new Options();
    options.addOption("c", "config", true, "configuration file");
    options.addOption("a", "apphome", true, "application home directory");
    options.addOption("h", "help", false, "show help screen");
    options.addOption("V", "version", false, "print the Mybox version");

    CommandLineParser line = new GnuParser();
    CommandLine cmd = null;

    try {
      cmd = line.parse(options, args);
    } catch( Exception exp ) {
      System.err.println( exp.getMessage() );
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( Client.class.getName(), options );
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

    if (cmd.hasOption("a")) {
      String appHomeDir = cmd.getOptionValue("a");
      try {
        Common.updatePaths(appHomeDir);
      } catch (FileNotFoundException e) {
        printErrorExit(e.getMessage());
      }

      updatePaths();
    }

    String configFile = defaultConfigFile;

    if (cmd.hasOption("c")) {
      configFile = cmd.getOptionValue("c");
      File fileCheck = new File(configFile);
      if (!fileCheck.isFile())
        printErrorExit("Specified config file does not exist: " + configFile);
    } else {
      File fileCheck = new File(configFile);
      if (!fileCheck.isFile())
        printErrorExit("Default config file does not exist: " + configFile);
    }

    Client client = new Client();
    client.config(configFile);
    client.start();
  }

  
  // Public static functions for everyone!


  /**
   * Read a file into a string
   * @param aFile
   * @return
   */
  static public String getContents(File aFile) {
    //...checks on aFile are elided
    StringBuilder contents = new StringBuilder();

    try {
      //use buffering, reading one line at a time
      //FileReader always assumes default encoding is OK!
      BufferedReader input =  new BufferedReader(new FileReader(aFile));
      try {
        String line = null; //not declared within while loop
        /*
        * readLine is a bit quirky :
        * it returns the content of a line MINUS the newline.
        * it returns null only for the END of the stream.
        * it returns an empty String if two newlines appear in a row.
        */
        while (( line = input.readLine()) != null){
          contents.append(line);
          contents.append(System.getProperty("line.separator"));
        }
      }
      finally {
        input.close();
      }
    }
    catch (IOException ex){
      // do something
    }

    return contents.toString();
  }


  /**
   * Transfer a public key file to a remote machine.
   * @param host, where the remote file is to go
   * @param serverPOSIXaccount, account on remote system
   * @param password, raw version of the password on the remote accout
   * @param pubkeyfilename, the local filename of the public key
   * @return
   */
  public static SysResult transferPublicKey(String host, String serverPOSIXaccount, String password, String pubkeyfilename) {

    File pubkeyfile = new File(pubkeyfilename); // TODO: check that the file exists or is not expty
    String pubkey = Client.getContents(pubkeyfile).trim();

    // TODO: make sure .ssh directory exists on server. mkdir -m 700 .ssh

    // note that the password does not have to be encrypted because SSH should be doing that itself

    SysResult result = Client.runSshCommand(host, serverPOSIXaccount, password, "echo \""+ pubkey +"\" >> ~/.ssh/authorized_keys");
    return result;
  }

  public static SysResult keyGen() {
    String sshDir = System.getProperty("user.home") + "/.ssh";
    //CommCommon.syscommand("mkdir -p "+sshDir);
    //CommCommon.syscommand("chmod 700 "+sshDir);

    return keyGen(sshDir + "/mybox_rsa");
  }

  /**
   * Create a public/private keypair with a given path/basename
   * @param filename
   * @return a SysResult where output is the input filename
   */
  public static SysResult keyGen(String filename) {

    SysResult result = new SysResult();

    Common.syscommand(new String[]{"rm", "-f", filename + ".pub"}); // pseudo non-deterministic ubuntu hack
    Common.syscommand(new String[]{"ssh-add","-l"}); // this is stupid

    // generating with the below method gives "Agent admitted failure to sign using the key." when logging in

    int type=KeyPair.RSA;
    String comment="generated by mybox";
    String passphrase="";

    result.output = filename;

    JSch jsch=new JSch();

    try{
//      KeyPair kpair=KeyPair.genKeyPair(jsch, type);
      KeyPair kpair=KeyPair.genKeyPair(jsch, type, 2048);
      kpair.setPassphrase(passphrase);
      kpair.writePrivateKey(filename);
      kpair.writePublicKey(filename + ".pub", comment);
      //System.out.println(kpair.getPublicKeyBlob().toString());
      //System.out.println("Finger print: "+kpair.getFingerPrint());
      kpair.dispose();
      result.worked = true;

      Common.syscommand(new String[]{"chmod", "600", filename});
    }
    catch(Exception e){
      System.err.println("keygen: " + e);
      result.worked = false;
    }

    return result;
  }

  public static boolean setKnownHosts(String host) {
    try {
      JSch jsch=new JSch();

      // use bogus account because we dont want to log into the server with a real account.
      // we only want to generate the local known_hosts file
      String serverPOSIXaccount = "bogus";
      String password = "bogus";

      System.out.println("setting known hosts file");

      // TODO: add mkdir .ssh and touch known_hosts for OS X
      // also chmod 600 .ssh/mybox_rsa

      Session session=jsch.getSession(serverPOSIXaccount, host, 22);

      java.util.Properties config = new java.util.Properties();
      config.setProperty("StrictHostKeyChecking", "no");
      session.setConfig(config);

      UserInfo ui=new SshUserInfo();
      session.setUserInfo(ui);
      session.setPassword(password);

      session.setConfig("HashKnownHosts",  "yes");

      jsch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");

      session.connect();

      // TODO: get return values from SshUserInfo for wrong passwords etc.
      // System.out.println("Connect returned "+returned);

      session.disconnect();

      System.out.println("known hosts set");
    }
    catch(Exception e) {
      System.out.println(e);
      return false;
    }
    return true;
  }

  /**
   * Run remote command over SSH using the JSch library
   *
   * @param host
   * @param serverPOSIXaccount
   * @param passwordOrIdentFile
   * @param command
   * @return
   */
  public static SysResult runSshCommand(String host, String serverPOSIXaccount,
          String passwordOrIdentFile, String command) {

    SysResult result = new SysResult();
    try{
      JSch jsch=new JSch();

      System.out.println("sshCommand "+ serverPOSIXaccount + ":" + passwordOrIdentFile + "@" + host + " "+ command);

      Session session=jsch.getSession(serverPOSIXaccount, host, 22);

      UserInfo ui=new SshUserInfo();
      session.setUserInfo(ui);
      
      File identfile = new File(passwordOrIdentFile);
      
      if (identfile.exists()) // if it is a private key file, use it
        jsch.addIdentity(passwordOrIdentFile);
      else  // else treat the string as a password
        session.setPassword(passwordOrIdentFile);
      
      jsch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");
      session.connect();

      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);

      //channel.setInputStream(System.in);
      channel.setInputStream(null);

      //channel.setOutputStream(System.out);

      //FileOutputStream fos=new FileOutputStream("/tmp/stderr");
      //((ChannelExec)channel).setErrStream(fos);
      ((ChannelExec)channel).setErrStream(System.err);

      InputStream in=channel.getInputStream();

      channel.connect();

      byte[] tmp=new byte[1024];
      while(true){
        while(in.available()>0){
          int i=in.read(tmp, 0, 1024);
          if(i<0)break;
          result.output += new String(tmp, 0, i);
        }
        if(channel.isClosed()){
          result.returnCode = channel.getExitStatus();
          break;
        }
        try{Thread.sleep(1000);}catch(Exception ee){}
      }

      result.worked = true;
      result.output = result.output.trim();
      channel.disconnect();
      session.disconnect();
    }
    catch(Exception e){
      result.worked = false;
      System.out.println(e);
    }
    return result;
  }


  /**
   * Class needed for SSH authentication via Jsch
   */
  public static class SshUserInfo implements UserInfo {
    public String getPassword(){ return null; }
    public boolean promptYesNo(String message){

      printMessage("Prompt yes/no: " + message); // perhaps printWarning
      //Client.quitError("Prompt yes/no: " + message);

      return false; // implies NO
    }

    public String getPassphrase(){ return null; }
    public boolean promptPassphrase(String message){ return true; }
    public boolean promptPassword(String message){
//      Client.quitError("ssh password prompt failure: " + message);
      printMessage("ssh password prompt failure: " + message);
      return false; // implies error?
    }
    public void showMessage(String message){
      printMessage("ssh show message: " + message);
    }

  }

}
