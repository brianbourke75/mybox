#!/bin/bash

# This script is for running mybox in POSIX environments (ie - Linux and OS X)

ARGS=$*

if [ "$ARGS" = "" ]
then
	ARGS=ClientGUI
fi

java -cp dist/mybox.jar net.mybox.mybox.$ARGS

