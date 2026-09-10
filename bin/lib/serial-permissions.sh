#!/bin/sh
# bin/lib/serial-permissions.sh — grant a user access to the host's serial
# devices (the groups metatron's serial / IoT spaces need).
#
# Usage:
#   bin/lib/serial-permissions.sh <user>     # e.g. bin/lib/serial-permissions.sh $USER
#   (log out and back in for the new group membership to take effect)
# -------------------------------------------------------------------
sudo usermod -a -G uucp "$1"
sudo usermod -a -G dialout "$1"
sudo usermod -a -G tty "$1"
sudo usermod -a -G lock "$1"
