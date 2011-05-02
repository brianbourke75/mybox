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
import java.sql.*;

/**
 * The server side account database. Uses SQLite.
 */
public class AccountsDB {

  private String dbLocation = null;
  private Connection dbConnection = null;


  public AccountsDB(String location) {
    dbLocation = location;

    // load the sqlite-JDBC driver
    try {
      Class.forName("org.sqlite.JDBC");
      dbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
      dbConnection.setAutoCommit(true);
    } catch (Exception e) {
      System.out.println("Unable to load SQLite driver " + e.getMessage());
      System.exit(1);
    }

    // check to see that the file can be loaded
    File f = new File(dbLocation);
    if (!f.exists())
      Server.printErrorExit("Accounts database file " + dbLocation + " not found.");
  }


  /**
   * Set up the database. Create it if it does not exist.
   * @param location of database file
   * @return false if there was an error
   */
  public static boolean Setup(String location) {

    try {
      Class.forName("org.sqlite.JDBC");
      Connection dbConnection = DriverManager.getConnection("jdbc:sqlite:" + location);
      dbConnection.setAutoCommit(true);

      Statement statement = dbConnection.createStatement();
      statement.setQueryTimeout(30);

      statement.executeUpdate("create table if not exists accounts (id integer primary key, email text unique, password text not null, salt text not null, created text not null, quota integer default null)");

    } catch (Exception e) {

      System.out.println("AccountsDB error during creation " + e.getMessage());
      return false;
    }

    return true;
  }


  /**
   * Remove the account from the database
   * @param id
   * @return
   */
  public boolean deleteAccount(String id) {

    try {
      Statement statement = dbConnection.createStatement();
      int affectedRows = statement.executeUpdate("delete from accounts where id='"+ id +"';");

      if (affectedRows != 1) {
        System.out.println("There was an error when removing the account from the database");
        return false;
      }

    } catch (SQLException ex) {
      Logger.getLogger(AccountsDB.class.getName()).log(Level.SEVERE, null, ex);
    }

    return true;
  }

  /**
   * Makes sure a new account can be inserted, before it is actually added to the database
   * @param email
   * @param rawPassword
   * @return true if valid
   */
  public boolean validatePotentialAccount(String email, String rawPassword) {

    Account accountByEmail = getAccountByEmail(email);

    if (accountByEmail != null) // unique in database
      return false;

    if (email.length() < 2 || rawPassword.length() < 2) // TODO: make these constraints harder
      return false;
    
    return true;
  }

  /**
   * Add account to database
   * @param email
   * @param encryptedPassword The raw password encrypted with the salt
   * @param salt
   * @return null if the account was not added
   */
  public Account addAccount(String email, String encryptedPassword, String salt) {

    Account account = null;

    try {
      PreparedStatement prep = dbConnection.prepareStatement("insert or ignore into accounts (email, password, salt, created) values(?,?,?,?);");

      prep.setString(1, email);
      prep.setString(2, encryptedPassword);
      prep.setString(3, salt);

      java.sql.Timestamp timestamp = new java.sql.Timestamp((new java.util.Date()).getTime());

      prep.setTimestamp(4, timestamp);
      prep.execute();

    } catch (Exception e) {
      System.out.println("Unable to add account to database " + e.getMessage());
      //return null;
    }

    // find the ID
    account = getAccountByEmail(email);
      
    return account;
  }

  /**
   * Get the number of entries in the accounts table
   * @return
   */
  public int AccountsCount() {

    int count = 0;

    try {
      Statement statement = dbConnection.createStatement();
      ResultSet rs = statement.executeQuery("select count(id) from accounts;");

      while (rs.next()) {
        count = rs.getInt(1);
      }

      rs.close();
    } catch (Exception e) {
      //
    }

    return count;
  }

  /**
   * Print a list of the accounts in the database
   */
  public void showAccounts() {
    
    Server.printMessage("id\temail");
    
    try {
      Statement statement = dbConnection.createStatement();
      ResultSet rs = statement.executeQuery("select * from accounts;");

      while (rs.next()) {
        Server.printMessage(rs.getInt("id") +"\t" + rs.getString("email"));
      }
      
      rs.close();
    } catch (Exception e) {
      //
    }

  }

  /**
   * Get an account from the database via a known ID
   * @param id
   * @return null if not found
   */
  public Account getAccountByID(String id) {

    Account account = null;
    
    try {
      Statement statement = dbConnection.createStatement();
      ResultSet rs = statement.executeQuery("select * from accounts where id='"+ id +"';");

      while (rs.next()) {
        
        int quota = rs.getInt("quota");
        if (quota == 0)
          quota = Server.defaultQuota;  // or should this be Server.baseQuota?
        
        account = new Account(rs.getString("id"),  rs.getString("email"), rs.getString("password"), 
                rs.getString("salt"), quota);
        
        break;
      }
      
      rs.close();
    } catch (Exception e) {
      System.out.println("There was an error fetching the account " + e.getMessage());
    }
    
    return account;

  }


  /**
   * Get an account from the database via a known email
   * @param email
   * @return null if not found
   */
  public Account getAccountByEmail(String email) {
    
    Account account = null;
    
    try {
      Statement statement = dbConnection.createStatement();
      ResultSet rs = statement.executeQuery("select * from accounts where email='"+ email +"';");

      while (rs.next()) {
        
        int quota = rs.getInt("quota");
        if (quota == 0)
          quota = Server.defaultQuota;  // or should this be Server.baseQuota?
        
        account = new Account(rs.getString("id"),  rs.getString("email"), rs.getString("password"), 
                rs.getString("salt"), quota);
        
        break;
      }
      
      rs.close();
    } catch (Exception e) {
      System.out.println("There was an error fetching the account: " + e.getMessage());
    }
    
    return account;
    
  }


  /**
   * Structure for accounts in the database
   */
  public class Account {

    // TODO: make all fields readonly

    String id = null;  //unique in DB       // TODO: make this an int to match database
    String email = null; //unique in DB
    String password = null;
    String salt = null;

    int quota = Server.defaultQuota;
    //String serverdir = null;  //not in db

    public Account(String id, String email, String password, String salt, int quota) {

      if (email.isEmpty() || password.isEmpty())
        Server.printErrorExit("Unable to create incomplete user");

      if (id != null)
        this.id = id;

      this.email = email;
      this.quota = quota;

      this.salt = salt;
      this.password = password;
    }

    @Override
    public String toString() {
      return "(id="+id+", email="+email+")";
    }
  }
}
