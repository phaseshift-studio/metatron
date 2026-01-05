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
import os
import sys
import time
import webrepl

import metatron.util.graphitty as graphitty
import metatron.util.homeassistant
from metatron.mqtt_space import MqttSpace
from metatron.router import Router
from metatron.soc.device.gpio import Gpio
from metatron.soc.device.memory import Memory
from metatron.soc.device.pwm import Pwm
from metatron.soc.device.wifi import Wifi
from metatron.soc.esp32.wemos_d1_mini import WemosD1Mini
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import mach, router
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
{{g}}            /___/ {{b}}/_/ /_/\___/\__/\__,_/\__/_/   \____/_/ /_/{{X}}"""))
print(graphitty.string("\t\t\t{{b}}{}{{X}}\n", os.uname().machine))

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

wlan = Wifi.connect(secrets['ssid'], secrets['password'], secrets['host'])
soc = None

# from metatron.mserver import MServer

def make_read_lambda(index):
    return lambda s: s.pwm[index]

def make_write_lambda(index):
    return lambda s, v: s.pwm.__setitem__(index, v)

def main_thread_function():
    global soc
    try:
        mqtt_space = MqttSpace(f(f"{secrets['host']}/#"), f("/sys/space/mqtt"))
        router().register(mqtt_space)
        mqtt_space.start()
        LOG.info("connected to {{y}}{}{{X}} broker", mqtt_space.client.server)
        # mqtt_space.connect_esphome({"ip": wifi.ipaddr(),
        #                            "platform": sys.platform,
        #                            "version": os.uname().release,
        #                            "name": secrets['host'],
        #                            "friendly_name": secrets['host']})
    except OSError:
        LOG.error("unable to connect to {{y}}{}{{X}} broker", mach["broker"])
    #####################################################################################################
    soc_vid = f(secrets['host'])
    soc = WemosD1Mini(vid=soc_vid)
    soc.attach(Wifi(wlan,soc_vid))
    soc.attach(Memory(soc_vid))
    soc.attach(Gpio(range(0, 35), soc_vid))
    soc.attach(Pwm(soc_vid))
    #####################################################################################################
    ha = metatron.util.homeassistant.HomeAssistant(soc, secrets.get("homeassistant", {}).get("prefix", "homeassistant"))
    ha.connect()
    ha.register(soc.vid.extend('wifi/signal')).sensor().diagnostic().on_read(
        lambda s: f"{s.wifi.strength():.0f}").device_class("signal_strength").unit_of_measurement('dBm').create()
    ha.register(soc.vid.extend('memory/free')).sensor().diagnostic().on_read(
        lambda s: f"{s.memory['free']}").device_class("data_size").unit_of_measurement("B").create()
    ha.register(soc.vid.extend('memory/alloc')).sensor().diagnostic().on_read(
        lambda s: f"{s.memory['alloc']}").device_class("data_size").unit_of_measurement("B").create()
    counter = 0
    for i in [5, 23, 19, 18]:
        (ha.register(soc.vid.extend(f'pwm/light_{counter}')).
         number().
         config().
         on_read(make_read_lambda(i)).
         on_write(make_write_lambda(i)).
         icon("mdi:light-flood-up").
         device_class("power_factor").
         unit_of_measurement('pwm').
         mode("slider").
         min_max(0, 255).create())
        counter = counter + 1
    ha.announce()
    ha.update()
    #####################################################################################################
    gc.collect()
    while True:
        try:
            mqtt_space.loop()
            ha.loop()
        except Exception as ex:
            print("resetting due to main loop error", ex)
            machine.reset()

LOG.info("metatron boot process complete")
if "stack_kb" in secrets.keys():
    _thread.stack_size(secrets["stack_kb"] * 1024)
_thread.start_new_thread(main_thread_function, ())

server = MServer()
server.start()
