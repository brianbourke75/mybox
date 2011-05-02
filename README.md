Mybox
=====
[https://github.com/mybox/mybox](https://github.com/mybox/mybox)  
version 0.1.0 by Jono


Introduction
------------
Mybox is a centralized file hosting and synchronization system. The goal is for it to be an open source alternative to Dropbox. The software consists of a server and client component. One server can host multiple accounts and each account can be used on multiple computers, where all files are automatically kept in sync across those computers.

See the [wiki](https://github.com/mybox/mybox/wiki) for more details on [usage](https://github.com/mybox/mybox/wiki/Usage), [development](https://github.com/mybox/mybox/wiki/Development) and [the motivation](https://github.com/mybox/mybox/wiki/Project-Goals) behind this project.


Project Status
--------------
This is a work in progress. The client and server are operational, but unfinished. At this stage, our focus is on the core libraries so the most usable setup would be to use the command line version of Mybox in Linux. It should also be noted that socket encryption has been removed in the latest version but will be added back later.


Quickstart
----------
Nothing needs to be installed. Mybox can be run in user mode without administrative privileges.


### Build project ###

      $ git clone git://github.com/mybox/mybox.git
      $ wget --no-check-certificate https://github.com/downloads/mybox/mybox/mybox-dev-includes-0.1.0.tar.gz  
      $ mkdir mybox/inc && tar -xzf mybox-dev-includes-0.1.0.tar.gz -C mybox/inc
      $ cd mybox
      $ ant jar
    
Then copy the Mybox application directory (mybox/) to the client and server machines.


### On the server machine ###

Configure the server

      $ bash mybox.bash ServerSetup

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

If you want the GUI to launch when you start a desktop session, add the following to your session startup script

      bash path/to/myboxapp/mybox.bash


