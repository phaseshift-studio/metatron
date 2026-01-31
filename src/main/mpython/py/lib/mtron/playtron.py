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
import uasyncio as asyncio
import webrepl
from machine import Pin, SoftSPI

from metatron.furi import f
from metatron.soc.device.i2c import I2c
from metatron.soc.device.memory import Memory
from metatron.soc.device.mfrc522 import MFRC522
from metatron.soc.device.mpy import MPy
from metatron.soc.device.spi import Spi
from metatron.soc.device.ssd1306 import Ssd1306
from metatron.soc.device.wifi import Wifi
from metatron.soc.esp32.wemos_d1_mini import WemosD1Mini
from metatron.soc.soc import Architecture
from metatron.space.file_space import FileSpace
from metatron.space.mqtt_space import MqttSpace
from metatron.space.mtron_space import mtronSpace
from metatron.util.graphitty import LOG
from metatron.util.mach import router


class Playtron(Architecture):
    def __init__(self, secrets: dict):
        Architecture.__init__(self, secrets)
        router().add_space(MqttSpace(f(f"{secrets['host']}/#")).start())
        router().add_space(FileSpace(f("file:#"),["file:","/"]).start())
        router().add_space(mtronSpace(f("ws://booger.local:8999/#")).start())
        #####################################################################################################
        self.soc = WemosD1Mini(vid=self.soc_vid)
        self.soc.attach(Wifi(wlan=self.wlan, secrets=self.secrets, soc_vid=self.soc_vid).start())
        #self.soc.attach(Memory(soc_vid=self.soc_vid).start())
        # self.soc.attach(Gpio(pin_range=range(0, 35), soc_vid=self.soc_vid).start())
        # self.soc.attach(Pwm(soc_vid=self.soc_vid).start())
        # self.soc.attach(I2c(scl_pin=22, sda_pin=21, soc_vid=self.soc_vid).start())
        # self.soc.attach(Ssd1306(i2c=self.soc.i2c, addr=0x3c, height=64, width=128, soc_vid=self.soc_vid, name="oled").start())
        #####################################################################################################
        # self.soc.attach(Spi(sck_pin=18, mosi_pin=23, miso_pin=19, soc_vid=self.soc_vid, name="spi").start())
        self.soc.attach(MPy(soc_vid=self.soc_vid, name="mpy").start())
        #####################################################################################################
        # self.soc.attach(MFRC522(soc_vid=self.soc_vid, spi=self.soc.spi, cs_pin=5, rst_pin=27, name="rfid").start())
        # LOG.warn("attempting rfid test: {}",self.soc.rfid.test_spi())
        # LOG.warn("attempting rfid read: {}", self.soc.rfid.request(self.soc.rfid.REQIDL))
