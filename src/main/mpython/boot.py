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
import os
import sys
import time
import webrepl

import metatron.util.graphitty as graphitty
from metatron.mqtt_space import MqttSpace
from metatron.router import Router
from metatron.soc.device.gpio import Gpio
from metatron.soc.device.pwm import Pwm
from metatron.soc.esp32.wemos_d1_mini import WemosD1Mini
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import mach
from metatron.util.translators import PythonTranslator
webrepl.start(password="mtron")
mach["router"] = Router()
mach["translator"] = PythonTranslator()
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
secrets = {}
try:
    secrets = json.load(open("secrets.json"))
except FileNotFoundError:
    LOG.error("secrets.json not found")
except json.JSONDecodeError as e:
    LOG.error("secrets.json is invalid json: {}", e)
except Exception as e:
    LOG.error("unexpected error loading secrets: {}", e)

gc.collect()
##########################################################
wlan = network.WLAN(network.STA_IF)
wlan.active(True)
wlan.config(dhcp_hostname=secrets['host'])
wlan.connect(secrets['ssid'], secrets['password'])
LOG.info("connecting to {{y}}{}{{X}} wifi", secrets['ssid'])
while not wlan.isconnected():
    pass
LOG.info("connected to {{y}}{}{{X}} as {{y}}{}\n\t{}", secrets['ssid'], wlan.config('hostname'), str(wlan.ifconfig()))
##########################################################

soc = None

def main_thread_function():
    global soc
    try:
        mqtt_space = MqttSpace(f("microtron/#"), f("/sys/space/mqtt"))
        mach["router"].register(mqtt_space)
        mqtt_space.start()
        LOG.info("connected to {{y}}{}{{X}} broker", mqtt_space.client.server)
        mqtt_space.connect_esphome({"ip": wlan.ifconfig()[0],
                                    "platform": sys.platform,
                                    "version": os.uname().release,
                                    "name": "microtron",
                                    "friendly_name": "microtron"})
    except OSError:
        LOG.error("unable to connect to {{y}}{}{{X}} broker", mach["broker"])

    #####################################################################################################
    soc_vid = f(wlan.config('hostname'))
    soc = WemosD1Mini(vid=soc_vid)
    soc.attach(Gpio(range(0, 35), soc_vid))
    soc.attach(Pwm(soc_vid))
    #####################################################################################################
    gc.collect()
    try:
        while True:
            mqtt_space.loop()
    except OSError:
        LOG.error("attempting reconnection to {{y}}{}{{X}} broker", mqtt_space.client.server)
        time.sleep(10)
        machine.reset()


LOG.info("metatron boot process complete")
_thread.stack_size(6 * 1024)
_thread.start_new_thread(main_thread_function, ())
