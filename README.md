Mybox
=====
[https://github.com/mybox/mybox](https://github.com/mybox/mybox)  
version 0.1.0 by Jono


Introduction
------------
Mybox is a centralized file hosting and synchronization system. The goal is for it to be an open source version of Dropbox. The software consist of a server and client component. One remote server can host multiple accounts and each account can be used on multiple computers, where all files will be automatically kept in sync across those computers.

See the [wiki](https://github.com/mybox/mybox/wiki) for more details on [usage](https://github.com/mybox/mybox/wiki/Usage), [development](https://github.com/mybox/mybox/wiki/Development) and [the motivation](https://github.com/mybox/mybox/Project-Goals) behind this project.


Project Status
--------------
This is a work in progress. The client and server are operational, but unfinished. While the client has been run in Windows and Max OS X, it has not been fully tested there. At this stage it is recommended that the client and server be run in Linux. Also, since the focus so far has been on user experience, the administrative tools are lacking.


Quickstart
----------
Nothing needs to be installed. Mybox can be run in user mode. The only program that needs elevated privileges is ServerAdmin since it manages local POSIX accounts.


### Set up executables ###

      $ git clone https://mybox@github.com/mybox/mybox.git  
      $ wget https://github.com/downloads/mybox/mybox/mybox-dev-includes-0.1.0.tar.gz  
      $ mkdir mybox/inc && tar -xzf mybox-dev-includes-0.1.0.tar.gz -C mybox/inc
      $ cd mybox
      $ ant jar
    
Then copy the Mybox home application directory to the client and server machines
    

### On the server machine ###

Configure the server

      $ bash mybox.bash ServerSetup
        
Set up the new user database

      $ mv mybox_server_db.empty.xml mybox_server_db.xml

Create a user

      $ bash mybox.bash ServerAdmin
      
Start the Server process

      $ bash mybox.bash Server


### On the client machine ###

Run the setup program to configure the client

      $ bash mybox.bash ClientSetup

Run the client

      $ bash mybox.bash Client
      
You should now have a ~/Mybox directory which is your synchronized directory.

