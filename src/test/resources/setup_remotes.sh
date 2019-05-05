#!/bin/bash

nohup python simple_server.py -p 8001 -d ./http1 > output.log 2>&1 &
echo $! >> servers.pid
nohup python simple_server.py -p 8002 -d ./http2 > output.log 2>&1 &
echo $! >> servers.pid

echo "Servers starteds in pids: `cat servers.pid`"
ps xa | grep python

tail -200f output.log

echo "Killin... "
kill -9 `cat servers.pid`
rm servers.pid