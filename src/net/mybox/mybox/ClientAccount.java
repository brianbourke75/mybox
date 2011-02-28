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
 * Structure for holding the client side account and settings
 */
public class ClientAccount {

    public String serverName = null;
    public int serverPort = Common.defaultCommunicationPort;
    public String email = null;
    public String serverPOSIXaccount = null;
    public String directory = Client.defaultClientDir;
    public String salt = null;
    public String encryptedPassword = null;
}
