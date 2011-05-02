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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.*;

import org.apache.commons.io.FileUtils;

/**
 * Command line server administative program
 */
public class ServerAdmin {

  private AccountsDB accountsDb = null;


  /**
   * Delete account and update user database
   */
  private void deleteAccount(){

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    accountsDb.showAccounts();

    Server.printMessage_("Which account would you like to delete?\naccount number> ");

    String input = null;
    try { input = br.readLine(); } catch (Exception e) {}

    AccountsDB.Account thisAccount = accountsDb.getAccountByID(input);

    if (thisAccount == null) {
      System.out.println("account " + input + " does not exist.");
      return;
    }

    System.out.println("Are you sure you want to delete " + thisAccount);
    System.out.print("y/n> ");

    try { input = br.readLine(); } catch (Exception e) {}

    if (input.equals("y")) {

      // delete the data directory
      File userDir = new File(Server.GetAbsoluteDataDirectory(thisAccount));
      
      try {
        FileUtils.deleteDirectory(userDir);
      } catch (IOException ex) {
        Server.printWarning("There was a problem deleting the user directory " + ex.getMessage());
      }
      
      // update the database
      if(accountsDb.deleteAccount(thisAccount.id)) 
        System.out.println("Account deleted");
      else
        System.out.println("Unable to delete account from database");
    }

  }

  /**
   * Add account and update database
   */
  private void addAccount() {

    // gather user input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String email = null;
    String password = null;

    Server.printMessage("Add a new account.");
    Server.printMessage_("email> ");

    try {  email = br.readLine();    } catch (Exception e) { }
    
    Server.printMessage_("password> ");
    try {  password = br.readLine();    } catch (Exception e) { }
    

    // validate the entered fields
    //String id = accountsDb.getNewID();
    
    if (!accountsDb.validatePotentialAccount(email, password)){
      Server.printWarning("New account is invalid or conflicts with an existing one.");
      return;
    }
    

    // update the database
    String salt = null, encryptedPassword = null;

    try{
      salt = Common.generateSalt();
      encryptedPassword = Common.encryptPassword(password, salt);
    } catch (Exception e) {
      Server.printWarning("Password encryption error " + e.getMessage());
    }

    AccountsDB.Account account = accountsDb.addAccount(email, encryptedPassword, salt);
      
    if (account == null) {
      System.out.println("Error: Unable to add account to database");
      return;
    }
    
    // create the data directory
    File userDir = new File(Server.GetAbsoluteDataDirectory(account));

    if (userDir.exists())
      Server.printWarning("Warning: data directory already exists " + userDir);
    else if (!userDir.mkdir())
      Server.printWarning("Warning: There was a problem when creating the data directory " + userDir);
  }
  
  
  private void showEncryptedPassword(){

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String inputPassword = null, inputSalt = null, encryptedPassword = null;

    Server.printMessage_("Enter a password to encrypt> ");
    try { inputPassword = br.readLine(); } catch (Exception e) {}
    
    Server.printMessage_("Enter salt> ");
    try { inputSalt = br.readLine(); } catch (Exception e) {}
    
    try {
      encryptedPassword = Common.encryptPassword(inputPassword, inputSalt);
    } catch (Exception e) {}

    Server.printMessage("Encrypted password: " + encryptedPassword);

  }

  /**
   * Constructor. Main loop and menu items.
   * @param dbFile
   */
  public ServerAdmin(String configFile) {

    Server.LoadConfig(configFile);

    accountsDb = new AccountsDB(Server.accountsDbfile);

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    
    Server.printMessage("Starting ServerAdmin command line utility...");
    
    char choice = 0;
    String input = null;
    
    // menu
    while (choice != 'q') {
      Server.printMessage("  l) List accounts");
      //Server.printMessage("  p) Show encyrpted password");
      Server.printMessage("  a) Add account");
      Server.printMessage("  d) Delete account");
      Server.printMessage("  q) Quit");
      Server.printMessage_("  > ");
      
      try {
        input = br.readLine();
      } catch (Exception e) {
        System.err.println(e);
      }

      if (input.length() != 1)
        continue;

      choice = input.charAt(0);

      switch (choice) {
        case 'l':
          accountsDb.showAccounts();
          break;
        case 'd':
          deleteAccount();
          break;
        case 'a':
          addAccount();
          break;
        case 'p':
          showEncryptedPassword();
          break;
      }
    }
    //dbConnection.close();

  }

  /**
   * Handle command line args
   * @param args
   */
  public static void main(String[] args) {

    Options options = new Options();
    options.addOption("c", "config", true, "configuration file");
//    options.addOption("d", "database", true, "accounts database file");
    options.addOption("a", "apphome", true, "application home directory");
    options.addOption("h", "help", false, "show help screen");
    options.addOption("V", "version", false, "print the Mybox version");

    CommandLineParser line = new GnuParser();
    CommandLine cmd = null;

    try {
      cmd = line.parse(options, args);
    } catch( ParseException exp ) {
      System.err.println( exp.getMessage() );

      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(ServerAdmin.class.getName(), options );
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
        Client.printErrorExit(e.getMessage());
      }

      Server.updatePaths();
    }

    String configFile = Server.defaultConfigFile;
//    String accountsDBfile = Server.defaultAccountsDbFile;

    if (cmd.hasOption("c")) {
      configFile = cmd.getOptionValue("c");
    }
    
    File fileCheck = new File(configFile);
    if (!fileCheck.isFile())
      Server.printErrorExit("Config not found: " + configFile + "\nPlease run ServerSetup");

//    if (cmd.hasOption("d")){
//      accountsDBfile = cmd.getOptionValue("d");
//    }
//
//    fileCheck = new File(accountsDBfile);
//    if (!fileCheck.isFile())
//      Server.printErrorExit("Error account database not found: " + accountsDBfile);

    ServerAdmin server = new ServerAdmin(configFile);

  }

}
