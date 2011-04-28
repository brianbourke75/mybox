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

import java.io.*;
import java.net.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.json.simple.parser.*;
import org.apache.commons.codec.binary.Base64;


/**
 * A class for holding common methods used by the client and server
 */
public class Common {

  /**
   * Signal commands that are made over the network
   */
  public enum Signal {

    c2s ((byte)0),
    clientWantsToSend ((byte)1),
    clientWantsToSend_response ((byte)2),
    clientWants ((byte)3),
    deleteOnServer ((byte)4),
    renameOnServer ((byte)5),
    createDirectoryOnServer ((byte)6),
    requestServerFileList ((byte)7),
    requestServerFileList_response ((byte)8),
    attachaccount ((byte)9),
    attachaccount_response ((byte)10),
    s2c ((byte)11),
    deleteOnClient ((byte)12),
    renameOnClient ((byte)13),
    createDirectoryOnClient ((byte)14),
    yes ((byte)15),
    no ((byte)16);

    private final byte index;

    Signal(byte index) {
        this.index = index;
    }

    public byte index() {
        return index;
    }

    public static Signal get(byte b) {
      return Signal.values()[b];
    }


  }

  public static int defaultCommunicationPort = 4444;
  public static final String appVersion = "0.1.0";

  // determine running application directories are
  private static String incDir = null;
  private static String appHome = null;

  private static final Base64 base64 = new Base64(76, new byte[]{'.'});

  static {
    File jarfile = new File(Common.class.getProtectionDomain().getCodeSource().getLocation().getPath().replaceAll("%20", " "));
    
    try {
      updatePaths(jarfile.getParent() + "/..");
    } catch (Exception e) {}

    // this will throw for classes, which is why you have to pass the additional arg for class files

  }
  
  /**
   * Updates the application home and include directory path.
   * Note that Client and Server should update their own paths if this is called
   * @param newAppHome
   * @throws FileNotFoundException
   */
  public static void updatePaths(String newAppHome) throws FileNotFoundException {

    File getAbsPath = new File(newAppHome); // note: this will work even if the file does not exist
    newAppHome = getAbsPath.getAbsolutePath();
    
    // we allow these to be set and determine correctness after excaption for initialization purposes
    appHome = newAppHome;
    incDir = newAppHome + "/inc";

//    Server.updatePaths();
//    Client.updatePaths();

  }

  /**
   * Get the app home directory
   * @return
   */
  public static String getAppHome() {
    return appHome;
  }
  
  /**
   * Gets the path to the inc directory which contains system specific binaries
   * @return
   */
  public static String getPathToInc(){
    return incDir;
  }


  /**
   * Get time for timestamping
   * @return
   */
  public static String now() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.format(Calendar.getInstance().getTime());
  }

  /**
  * Recursively walk a directory tree and return a List of all
  * Files found; the List is sorted using File.compareTo().
  *
  * @param aStartingDir is a valid directory, which can be read.
  */
  static public List<MyFile> getFileListing(File aStartingDir) throws FileNotFoundException {
    if (!aStartingDir.isDirectory())
      return null;
    List<MyFile> result = getFileListingNoSort("", aStartingDir);
    //Collections.sort(result);
    return result;
  }

  // PRIVATE //
  static private List<MyFile> getFileListingNoSort(String localParent, File aStartingDir) throws FileNotFoundException {
    List<MyFile> result = new ArrayList<MyFile>();
    File[] filesAndDirs = aStartingDir.listFiles();
    List<File> filesDirs = Arrays.asList(filesAndDirs);
    for(File file : filesDirs) {

      String prefix = "";
      
      if (!localParent.equals(""))
        prefix = localParent + "/";
      
      MyFile myFile = new MyFile(prefix + file.getName());
//        myFile.name = localParent + "/" + file.getName();

      myFile.modtime = file.lastModified();
      
      if (file.isFile()) {
        myFile.type = "file";
        
        result.add(myFile);
      }
      else if (file.isDirectory()) {
        
        myFile.type = "directory";

        result.add(myFile);

      //result.add(file); //always add, even if directory
      //if ( ! file.isFile() ) {
        //must be a directory
        //recursive call!
        List<MyFile> deeperList = getFileListingNoSort(prefix + file.getName(), file);
        result.addAll(deeperList);
      }
    }
    return result;
  }

  public static boolean createLocalDirectory(String path) {

    File dir = new File(path);

    System.out.println("Creating local directory " + path);

    if (!dir.isDirectory()) { // dont bother creating it if it already exists
      if (dir.exists() ) {
        System.out.println("Error creating local directory, because file exists");
        return false;
      }
      else
        return dir.mkdirs();
    }

    return true;
  }

  /**
   * Delete a directory and its contents recursively
   * @param path
   * @return
   */
  public static boolean deleteLocalDirectory(File path) {

    // TODO: should we use apache.io file tools for these functions?

    if( path.exists() ) {
      File[] files = path.listFiles();
      for(int i=0; i<files.length; i++) {
         if(files[i].isDirectory()) {
           deleteLocalDirectory(files[i]);
         }
         else {
           files[i].delete();
         }
      }
    }
    return( path.delete() );
  }

  
  public static boolean deleteLocal(String path) { // file or directory
    System.out.println("Deleting local item " + path);

    File thisFile = new File(path);
    if (thisFile.isDirectory())
      return Common.deleteLocalDirectory(thisFile);
    else if (thisFile.exists())
      return thisFile.delete();

    return false;
  }

  public static boolean renameLocal(String oldPath, String newPath) {
    System.out.println("Renaming local item " + oldPath + " to " + newPath);

    File oldFile = new File(oldPath);
    File newFile = new File(newPath);

    return (oldFile.renameTo(newFile));
    
//      System.out.println("Error durring rename");
  }

  /**
   * Run a system command on the local machine
   * @param command
   * @return
   */
  public static SysResult syscommand(String[] command) {
    Runtime r = Runtime.getRuntime();

    SysResult result = new SysResult();

    System.out.println("syscommand array: " + StringUtils.join(command, " "));

    try {

      Process p = r.exec(command);
      // should use a thread so it can be killed if it has not finished and another one needs to be started
      InputStream in = p.getInputStream();

      InputStream stderr = p.getErrorStream();
      InputStreamReader inreadErr = new InputStreamReader(stderr);
      BufferedReader brErr = new BufferedReader(inreadErr);

      BufferedInputStream buf = new BufferedInputStream(in);
      InputStreamReader inread = new InputStreamReader(buf);
      BufferedReader bufferedreader = new BufferedReader(inread);

      // Read the ls output
      String line;
      while ((line = bufferedreader.readLine()) != null) {
        result.output += line + "\n";
        System.err.print("  output> " + result.output);
        // should check for last line "Contacting server..." after 3 seconds, to restart unison command X times
      }

      result.worked = true;

      // Check for failure
      try {
        if (p.waitFor() != 0) {
          System.err.println("exit value = " + p.exitValue());
          System.err.println("command> " + command);
          System.err.println("output> " + result.output);
          System.err.print("error> ");

          while ((line = brErr.readLine()) != null)
            System.err.println(line);

          result.worked = false;
        }
        result.returnCode = p.waitFor();
      } catch (InterruptedException e) {
        System.err.println(e);
        result.worked = false;
      } finally {
        // Close the InputStream
        bufferedreader.close();
        inread.close();
        buf.close();
        in.close();
      }
    } catch (IOException e) {
      System.err.println(e.getMessage());
      result.worked = false;
    }

    result.output = result.output.trim();

    return result;
  }

  /**
   * Checks to see if a specific port is available.
   *
   * @param port the port to check for availability
   */
  public static boolean portAvailable(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid start port: " + port);
    }

    ServerSocket ss = null;
    DatagramSocket ds = null;
    try {
      ss = new ServerSocket(port);
      ss.setReuseAddress(true);
      ds = new DatagramSocket(port);
      ds.setReuseAddress(true);
      return true;
    } catch (IOException e) {
    } finally {
      if (ds != null) {
        ds.close();
      }

      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          /* should not be thrown */
        }
      }
    }

    return false;
  }

  /**
   * Turn the json input string into a HashMap
   * @param input
   * @return
   */
  public static HashMap jsonDecode(String input) {

    // TODO: make this recursive for nested JSON objects and perhaps arrays as well

    HashMap result = new HashMap();

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
      Map json = (Map)parser.parse(input, containerFactory);
      Iterator iter = json.entrySet().iterator();
      while(iter.hasNext()){
        Map.Entry entry = (Map.Entry)iter.next();
        result.put(entry.getKey(), entry.getValue());
      }
    }
    catch(Exception pe){
      System.out.println(pe);
    }

    return result;
  }


  /**
   * Generate a random salt value to be used for encrypting a password
   * @return
   */
  public static String generateSalt() throws NoSuchAlgorithmException {

    SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

    byte[] bSalt = new byte[8];
    random.nextBytes(bSalt);

    return byteToBase64(bSalt);
  }

  /**
   * Encrypt a raw password with a salt
   * @param password String The password to encrypt
   * @param salt The salt
   * @return The encrypted password
   */
  public static String encryptPassword(String password, String salt)
          throws NoSuchAlgorithmException, UnsupportedEncodingException {

    byte[] bSalt = base64ToByte(salt);

    MessageDigest digest = null;

    digest = MessageDigest.getInstance("SHA-1");

    digest.reset();
    digest.update(bSalt);
    byte[] input = null;

    input = digest.digest(password.getBytes("UTF-8"));

    for (int i = 0; i < 5; i++) { // hash 5 times for good measure
      digest.reset();
      input = digest.digest(input);
    }

    String output = byteToBase64(input);
    return output;
  }

  /**
   * From a base 64 representation, returns the corresponding byte[]
   * @param data String The base64 representation
   * @return byte[]
   */
  public static byte[] base64ToByte(String data) {
    //return base64.decodeBase64(data);
    return base64.decode(data);
    //BASE64Decoder decoder = new BASE64Decoder();
    //byte[] result = decoder.decodeBuffer(data);
    //return result;
  }

  /**
   * From a byte[] returns a base 64 representation
   * @param data byte[]
   * @return String
   * @throws IOException
   */
  public static String byteToBase64(byte[] data) {

    return base64.encodeToString(data);

//    return base64.encodeBase64String(data);

    //return Base64.encodeBase64String(data); // TODO: why does this have a newline
//    BASE64Encoder endecoder = new BASE64Encoder();
//    return endecoder.encode(data);
  }

}
