#  Metatron: A Distributed Computing Language and Virtual Machine
#   Copyright (C) 2025- PhaseShift Studio, LLC
# 
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
# 
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.

import time
from simple import MQTTClient
import ubinascii
import machine
import micropython
import network
import esp
import _thread
from colors import *
import graphitty

print(graphitty.string("""
{{b}}MicroPython \r
{{y}} _____ ______   _______  _________  ________  _________  ________  ________  ________ \r
{{r}}|\   _ \  _   \|\  ___ \|\___   ___\\   __  \|\___   ___\\   __  \|\   __  \|\   ___  \ \r
{{y}}\ \  \\\__\ \  \ \   __/\|___ \  \_\ \  \|\  \|___ \  \_\ \  \|\  \ \  \|\  \ \  \\ \  \ \r
{{r}} \ \  \\|__| \  \ \  \_|/__  \ \  \ \ \   __  \   \ \  \ \ \   _  _\ \  \\\  \ \  \\ \  \ \r
{{y}}  \ \  \    \ \  \ \  \_|\ \  \ \  \ \ \  \ \  \   \ \  \ \ \  \\  \\ \  \\\  \ \  \\ \  \ \r
{{r}}   \ \__\    \ \__\ \_______\  \ \__\ \ \__\ \__\   \ \__\ \ \__\\ _\\ \_______\ \__\\ \__\ \r
{{y}}    \|__|     \|__|\|_______|   \|__|  \|__|\|__|    \|__|  \|__|\|__|\|_______|\|__| \|__| \r
                                                        {{g}}A PhaseShift Studio Production{{X}}
"""))


import secrets

esp.osdebug(None)
import gc
gc.collect()

ssid = 'Rodkins-2G'
password = 'puppymama'
mqtt_server = 'chibi.local'
#mqtt_user = 'REPLACE_WITH_YOUR_MQTT_USERNAME'
#mqtt_pass = 'REPLACE_WITH_YOUR_MQTT_PASSWORD'

#EXAMPLE IP ADDRESS
#mqtt_server = '192.168.1.144'
client_id = ubinascii.hexlify(machine.unique_id())
topic_sub = b'zigbee2mqtt/office/lamp_light/#'
topic_pub = b'hello'

last_message = 0
message_interval = 5
counter = 0

station = network.WLAN(network.STA_IF)

station.active(True)
station.connect(ssid, password)

while station.isconnected() == False:
    pass

print('Connection successful')
print(station.ifconfig())

# Complete project details at https://RandomNerdTutorials.com/micropython-programming-with-esp32-and-esp8266/

def sub_cb(topic, msg):
    print((topic, msg))

def connect_and_subscribe():
    global client_id, mqtt_server, topic_sub
    client = MQTTClient(client_id, mqtt_server)
    client.set_callback(sub_cb)
    client.connect()
    client.subscribe(topic_sub)
    print('Connected to %s MQTT broker, subscribed to %s topic' % (mqtt_server, topic_sub))
    return client

def restart_and_reconnect():
    print('Failed to connect to MQTT broker. Reconnecting...')
    time.sleep(10)
    machine.reset()

try:
    client = connect_and_subscribe()
except OSError as e:
    restart_and_reconnect()

def th_func():
    try:
        while True:
            client.check_msg()
    except OSError as e:
        restart_and_reconnect()

_thread.start_new_thread(th_func, ())
