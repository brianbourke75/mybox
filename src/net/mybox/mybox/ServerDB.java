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
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * The server side database. Which mainly holds users.
 * Note: This currently only supports XML file databases.
 */
public class ServerDB {

  private String dbLocation = null;

  // helper members used for Xpath queries accross functions
  private XPathExpression expr = null;
  private XPathFactory xFactory = XPathFactory.newInstance();
  private XPath xpath = xFactory.newXPath();

  public ServerDB(String location) {
    dbLocation = location;

    // check to see that the file can be loaded
    File f = new File(dbLocation);
    if (!f.exists())
      Server.printErrorExit("DB file " + dbLocation + " not found.");
  }


  /**
   * Create a new blank database file in the app home directory
   * @throws IOException
   */
  public static void createNewDB() throws IOException {

    String fileName = Common.getAppHome() + "/mybox_server_db.empty.xml";

    // generate the blank database
    String xmlString =
      "<server>\n" +
      " <accounts>\n" +
      " </accounts>\n"+
      "</server>";

    Server.printMessage("Creating new database in " + fileName);

    FileWriter outFile = new FileWriter(fileName);
    PrintWriter out = new PrintWriter(outFile);

    out.println(xmlString);
    out.close();
  }

  /**
   * Load a XML file into a Document variable
   * @param fileName
   * @return
   */
  public Document loadXml() {

    Document xmlDB = null;

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = null;

    try {
      builder = factory.newDocumentBuilder();
    } catch (Exception e) {

    }

    try {
      xmlDB = builder.parse(dbLocation);
    } catch (Exception e) {
      Server.printErrorExit("Unable to parse settings file");
    }

    return xmlDB;
  }


  /**
   * Save the XML doc to a file
   * @param doc
   * @param filename
   * @return
   */
  public static boolean writeXmlFile(Document doc, String filename) {
    try {

      /*
        // Write the DOM document to the file
        Transformer xformer = TransformerFactory.newInstance().newTransformer();
        xformer.setOutputProperty(OutputKeys.INDENT, "yes");
        
        // Prepare the DOM document for writing
        Source source = new DOMSource(doc);
        
        // Prepare the output file
        File file = new File(filename);
        Result result = new StreamResult(file);
        
        xformer.transform(source, result);
        */

        // Write the DOM document to the file
        Transformer xformer = TransformerFactory.newInstance().newTransformer();
        xformer.setOutputProperty(OutputKeys.INDENT, "yes");  // TODO: fix indentation issue
        Source source = new DOMSource(doc);
        StreamResult result = new StreamResult(new StringWriter());
        xformer.transform(source, result);

        try {
          FileWriter outFile = new FileWriter(filename);
          PrintWriter out = new PrintWriter(outFile);
          out.println(result.getWriter().toString());
          out.close();
        }
        catch (Exception e) {
          Client.printErrorExit("Unable to write config file");
        }

    } catch (Exception e) {
      return false;
    }

    return true;
  }


  /**
   * Generate a new id for an account which will be entered into the database
   * @return
   */
  public String getNewID() {
    
    Document xmlDB = loadXml();// Server.loadSettings(dbLocation);

    NodeList accounts = null;

    try {  expr = xpath.compile("/server/accounts/account");} catch (Exception e) {}
    try {  accounts = (NodeList)expr.evaluate(xmlDB, XPathConstants.NODESET); } catch (Exception e) {}

    int maxid = 0;

    // find the maximum id in the database
    for (int u=0; u<accounts.getLength(); u++) {
      Node account = accounts.item(u);

      String id = null;
      try {   id = (String)xpath.evaluate("id", account, XPathConstants.STRING); } catch (Exception e) {}

      int idint = Integer.parseInt(id);
      
      if (idint > maxid)
        maxid = idint;
    }
    
    return Integer.toString(maxid+1);
  }

  /**
   * Remove the account from the database
   * @param id
   * @return
   */
  public boolean deleteAccount(String id) {

    Document xmlDB = loadXml();

    Node account = null;

    try {  expr = xpath.compile("/server/accounts/account[id='"+id+"']");} catch (Exception e) {}
    try {  account = (Node)expr.evaluate(xmlDB, XPathConstants.NODE); } catch (Exception e) {}

    if (account == null)
      return false; // account does not exist

    account.getParentNode().removeChild(account);
    
    writeXmlFile(xmlDB, dbLocation);

    return true;
  }

  /**
   * Makes sure a new account can be inserted, before it is actually added to the database
   * @param id
   * @param email
   * @param rawPassword
   * @return true if valid
   */
  public boolean validateNewAccount(String id, String email, String rawPassword) {

    Account accountById = getAccountByID(id);
    Account accountByEmail = getAccountByEmail(email);

//    System.out.println("Account exists? " + accountById + ", " + accountByEmail);

    if (accountByEmail != null || accountById != null) // id and email are unique in database
      return false;

    if (email.length() < 2 || rawPassword.length() < 2) // these constraints could be harder
      return false;
    
    return true;
  }

  /**
   * Add account to database
   * @param id
   * @param email
   * @param encryptedPassword The raw password encrypted with the salt
   * @param salt
   * @return true if the account was able to be added
   */
  public boolean addAccount(String id, String email, String encryptedPassword, String salt) {
    
    Document xmlDB = loadXml();// Server.loadSettings(dbLocation);

    // assumes validateNewAccount was called prior to this

    Node accounts = null;

    try {  expr = xpath.compile("/server/accounts");} catch (Exception e) { return false; }
    try {  accounts = (Node)expr.evaluate(xmlDB, XPathConstants.NODE); }
    catch (Exception e) { return false; }

    Element account = xmlDB.createElement("account");

    Element idElement = xmlDB.createElement("id");
    idElement.appendChild(xmlDB.createTextNode(id));
    account.appendChild(idElement);

    Element emailElement = xmlDB.createElement("email");
    emailElement.appendChild(xmlDB.createTextNode(email));
    account.appendChild(emailElement);

    Element pwElement = xmlDB.createElement("password");
    pwElement.appendChild(xmlDB.createTextNode(encryptedPassword));
    account.appendChild(pwElement);

    Element saltElement = xmlDB.createElement("salt");
    saltElement.appendChild(xmlDB.createTextNode(salt));
    account.appendChild(saltElement);

    accounts.appendChild(account);

    writeXmlFile(xmlDB, dbLocation);

    return true;
  }

  /**
   * Print a list of the accounts in the database
   */
  public void showAccounts() {
    
    Document xmlDB = loadXml();

    NodeList accounts = null;

    try {  expr = xpath.compile("/server/accounts/account");} catch (Exception e) {}
    try {  accounts = (NodeList)expr.evaluate(xmlDB, XPathConstants.NODESET); } catch (Exception e) {}

    Server.printMessage("id\temail\t\tserver directory");

    for (int u=0; u<accounts.getLength(); u++) {
      Node account = accounts.item(u);

      String id = null;
      try {   id = (String)xpath.evaluate("id", account, XPathConstants.STRING); } catch (Exception e) {}

      String email = null;
      try {   email = (String)xpath.evaluate("email", account, XPathConstants.STRING); } catch (Exception e) {}

      File dir = new File(Server.serverBaseDir + "/" + id);
      String dirString = "";
      if (dir.isDirectory())
        dirString = dir.getAbsolutePath();

      Server.printMessage(id +"\t" + email + "\t" + dirString);
    }

  }

  /**
   * Get an account from the database via a known ID
   * @param id
   * @return null if not found
   */
  public Account getAccountByID(String id) {

    Document xmlDB = loadXml();

    Node account = null;

    try {  expr = xpath.compile("/server/accounts/account[id='"+id+"']");} catch (Exception e) {}
    try {  account = (Node)expr.evaluate(xmlDB, XPathConstants.NODE); } catch (Exception e) {}

    if (account == null)
      return null;

    String email = null;
    try {   email = (String)xpath.evaluate("email", account, XPathConstants.STRING); } catch (Exception e) {}

    int quota = Server.defaultQuota;
    String quotaString = null;
    try {   quotaString = (String)xpath.evaluate("quota", account, XPathConstants.STRING); } catch (Exception e) {}
    if (!quotaString.isEmpty())
      try {  quota = Integer.parseInt(quotaString); } catch (Exception e) {}

    String password = null;
    try {   password = (String)xpath.evaluate("password", account, XPathConstants.STRING); } catch (Exception e) {}

    String salt = null;
    try {   salt = (String)xpath.evaluate("salt", account, XPathConstants.STRING); } catch (Exception e) {}

    return new Account(id, email, password, salt, quota);
  }


  /**
   * Get an account from the database via a known email
   * @param email
   * @return null if not found
   */
  public Account getAccountByEmail(String email) {

    Document xmlDB = loadXml();

    Node account = null;

    try {  expr = xpath.compile("/server/accounts/account[email='"+email+"']");} catch (Exception e) {}
    try {  account = (Node)expr.evaluate(xmlDB, XPathConstants.NODE); } catch (Exception e) {}

    if (account == null)
      return null;

    String id = null;
    try {   id = (String)xpath.evaluate("id", account, XPathConstants.STRING); } catch (Exception e) {}

    int quota = Server.defaultQuota;
    String quotaString = null;
    try {   quotaString = (String)xpath.evaluate("quota", account, XPathConstants.STRING); } catch (Exception e) {}
    if (!quotaString.isEmpty())
      try {  quota = Integer.parseInt(quotaString); } catch (Exception e) {}

    String password = null;
    try {   password = (String)xpath.evaluate("password", account, XPathConstants.STRING); } catch (Exception e) {}

    String salt = null;
    try {   salt = (String)xpath.evaluate("salt", account, XPathConstants.STRING); } catch (Exception e) {}

    return new Account(id, email, password, salt, quota);
  }


  /**
   * Structure for accounts that already exist in the database
   */
  public class Account {

    // TODO: make all fields readonly

    String id = null;  //unique in DB
    String email = null; //unique in DB
    String password = null; // TODO: store this?
    String salt = null;

    int quota = Server.defaultQuota;
    String serverdir = null;  //not in db
//    String serverPOSIXaccount = null;  //not in db


    public Account(String id, String email, String password, String salt, int quota) {

      if (id.isEmpty() || email.isEmpty() || password.isEmpty())
        Server.printErrorExit("Unable to create incomplete user");

      this.id = id;
      this.email = email;
      this.quota = quota;
//      serverPOSIXaccount = Server.getServerPOSIXaccountName(id);
      //serverdir = "/home/" + serverPOSIXaccount + "/Mybox/";  // TODO: set dynamically based on system

      this.salt = salt;
      this.password = password;

      this.serverdir = Server.serverBaseDir + "/" + id;
    }

    @Override
    public String toString() {
      return "(id="+id+", email="+email+")";
    }
  }
}
