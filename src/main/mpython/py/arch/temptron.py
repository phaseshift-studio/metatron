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
from metatron.space.mqtt_space import MqttSpace
from metatron.soc.device.gpio import Gpio
from metatron.soc.device.memory import Memory
from metatron.soc.device.pwm import Pwm
from metatron.soc.device.wifi import Wifi
from metatron.soc.esp32.wemos_d1_mini import WemosD1Mini
from metatron.soc.soc import Architecture
from metatron.util.common import make_pwm_read_lambda, make_pwm_write_lambda
from metatron.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import router


class Temptron(Architecture):
    def __init__(self, secrets: dict):
        LOG.info("loading secrets configuration")
        wlan = Wifi.connect(secrets['ssid'], secrets['password'], secrets['host'])
        try:
            self.mqtt_space = MqttSpace(f(f"{secrets['host']}/#"), f("/sys/space/mqtt"))
            router().register(self.mqtt_space)
            self.mqtt_space.start()
            LOG.info("connected to {{y}}{}{{X}} broker", self.mqtt_space.client.server)
            # self.mqtt_space.connect_esphome({"ip": wifi.ipaddr(),
            #                            "platform": sys.platform,
            #                            "version": os.uname().release,
            #                            "name": secrets['host'],
            #                            "friendly_name": secrets['host']})
            # webrepl.start(password="mtron")
        except OSError:
            LOG.error("unable to connect to {{y}}{}{{X}} broker ({{y}}resetting machine{{X}})", secrets["broker"])
            machine.reset()
        #####################################################################################################
        soc_vid = f(secrets['host'])
        self.soc = WemosD1Mini(vid=soc_vid)
        self.soc.attach(Wifi(wlan, soc_vid))
        self.soc.attach(Memory(soc_vid))
        self.soc.attach(Gpio(range(0, 35), soc_vid))
        self.soc.attach(Pwm(soc_vid))
        #####################################################################################################
        self.ha = metatron.util.homeassistant.HomeAssistant(self.soc, secrets.get("homeassistant", {}).get("prefix",
                                                                                                           "homeassistant"))
        self.ha.connect()
        self.ha.register(self.soc.vid.extend('wifi/signal')).sensor().diagnostic().on_read(
            lambda s: f"{s.wifi.strength():.0f}").device_class("signal_strength").unit_of_measurement('dBm').create()
        self.ha.register(self.soc.vid.extend('memory/free')).sensor().diagnostic().on_read(
            lambda s: f"{s.memory['free']}").device_class("data_size").unit_of_measurement("B").create()
        self.ha.register(self.soc.vid.extend('memory/alloc')).sensor().diagnostic().on_read(
            lambda s: f"{s.memory['alloc']}").device_class("data_size").unit_of_measurement("B").create()
        counter = 0
        for i in [5, 23, 19, 18]:
            (self.ha.register(self.soc.vid.extend(f'pwm/light_{counter}')).
             number().
             config().
             on_read(make_pwm_read_lambda(i)).
             on_write(make_pwm_write_lambda(i)).
             icon("mdi:light-flood-up").
             device_class("power_factor").
             unit_of_measurement('pwm').
             mode("slider").
             min_max(0, 255).create())
            counter = counter + 1
        self.ha.announce()
        self.ha.update()

    def loop(self):
        self.mqtt_space.loop()
        self.ha.loop()

    def __repr__(self):
        return self.soc.__repr__()

    def __srt__(self):
        return self.__repr__()
