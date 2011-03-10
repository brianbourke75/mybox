#!/bin/bash

# This script is for running mybox in POSIX environments (ie - Linux and OS X)

ARGS=$*
APPHOME="`dirname \"$0\"`"

if [ "$ARGS" = "" ]
then
	ARGS=ClientGUI
fi

java -cp $APPHOME/dist/mybox.jar net.mybox.mybox.$ARGS

