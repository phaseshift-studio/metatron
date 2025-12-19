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

import _thread
import esp
import json
import machine
import network
import sys
import time

import metatron.util.graphitty as graphitty
from metatron.mqtt_space import MqttSpace
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.obj import *
from metatron.util.translators import PythonTranslator
from metatron.util.args import args
from metatron.router import Router
from metatron.mfluent import m

args["router"] = Router()
args["translator"] = PythonTranslator() 

esp.osdebug(None)
import gc

print(graphitty.string("""
{{g}}        /^\/^\                                                     
{{g}}      _|__|  {{w}}O{{g}}|                                                    
{{r}}\/ {{g}} /{{y}}~{{g}}     \_/ \                                                  
{{r}} \_{{g}}|__________/ \  {{y}}{{~}}PhaseShift Studio Presents{{X}}                                                 
{{g}}     \_______    \               __        __                   
{{g}}             `\   \__ ___  ___  / /_____ _{{y}}/ /__________  ____   
{{g}}              |   __ `__ \/ _ \/ {{y}}__/ __ `/ __/ ___/ __ \/ __ \  
{{g}}             /   / / / / {{c}}/  __/ /_/ /_/ / /_/ /  / /_/ / / / /  
{{g}}            /___/ {{b}}/_/ /_/\___/\__/\__,_/\__/_/   \____/_/ /_/{{X}}                                                             
"""))

sys.ps1 = graphitty.string("{{m}}mtron{{g}}>{{X}} ")
sys.ps2 = graphitty.string("{{m}}     {{g}}>{{X}} ")

LOG.info("loading secrets configuration")
secrets = json.load(open("secrets.json"))

gc.collect()
##########################################################


###### WIFI CONNECTION ######
station = network.WLAN(network.STA_IF)
station.active(True)
station.connect(secrets['ssid'], secrets['password'])
LOG.info("connecting to {{y}}{}{{X}} wifi", secrets['ssid'])
while not station.isconnected():
    pass
LOG.info("connected to {{y}}{}{{X}} as {{y}}{}", secrets['ssid'], str(station.ifconfig()))


#############################

mqtt_space: MqttSpace | None = None

def _callback(furi, obj):
    global mqtt_space
    mqtt_space._callback(furi, obj)


def connect_and_subscribe():
    global mqtt_space
    mqtt_space = MqttSpace(f("zigbee2mqtt/office/lamp_light/#"), f("/sys/router/space/mqtt"))
    args["router"].register(mqtt_space)
    mqtt_space.start(_callback)
    LOG.info("connected to {{y}}{}{{X}} broker", mqtt_space.client.server)
    return mqtt_space


def restart_and_reconnect():
    global mqtt_space
    LOG.error("attempting reconnection to {{y}}{}{{X}} broker", mqtt_space.client.server)
    time.sleep(10)
    machine.reset()


LOG.info("metatron boot process complete")
def main_thread_function():
    mqtt_space = connect_and_subscribe()
    try:
        while True:
            mqtt_space.loop()
    except OSError as e:
        restart_and_reconnect()


_thread.start_new_thread(main_thread_function, ())
