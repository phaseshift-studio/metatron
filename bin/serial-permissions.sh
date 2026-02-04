#!/usr/bin/sh

sudo usermod -a -G uucp $1
sudo usermod -a -G dialout $1
sudo usermod -a -G tty $1
sudo usermod -a -G lock $1
