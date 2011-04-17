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

/**
 * This client uses this thread to wait for directory updates to finish before syncing.
 * This way syncs happen in groups rather then issuing a directory sync for each updated file.
 */
public class GatherDirectoryUpdates extends Thread {
  
  private Client client = null;

  private int SleepSeconds;
  private boolean active = true;

  public GatherDirectoryUpdates(Client _client, int seconds) {
    client = _client;
    SleepSeconds = seconds;
    start();
  }

  public void run(){

    // TODO: perhaps use wait/notify instead?

    // sleep the thread for some time
    try{
      GatherDirectoryUpdates.sleep(1000*SleepSeconds);
    }
    catch (Exception e){
      System.out.println(e.getMessage());
    }

    // if it gets here, the waiting has finished and it is time to sync
    if (active)
      client.waitHasFinished();

  }

  public void deactivate() {
    active = false;
  }
}
