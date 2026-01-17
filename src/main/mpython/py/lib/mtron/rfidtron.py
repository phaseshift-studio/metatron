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
import _thread
import esp
import json
import machine
import os
import sys
import time
import webrepl
from machine import Pin, SoftSPI

from lib.mfrc522 import MFRC522
from metatron.furi import f
from metatron.soc.device.gpio import Gpio
from metatron.soc.device.i2c import I2c
from metatron.soc.device.memory import Memory
from metatron.soc.device.pwm import Pwm
from metatron.soc.device.ssd1306 import Ssd1306
from metatron.soc.device.wifi import Wifi
from metatron.soc.esp32.wemos_d1_mini import WemosD1Mini
from metatron.soc.soc import Architecture
from metatron.space.mqtt_space import MqttSpace
from metatron.util.graphitty import LOG
from metatron.util.mach import router


class RFIDtron(Architecture):
    def __init__(self, secrets: dict):
        Architecture.__init__(self, secrets)
        router().add_space(MqttSpace(f(f"{secrets['host']}/#")).start())
        #####################################################################################################
        self.soc = WemosD1Mini(vid=self.soc_vid)
        self.soc.attach(Wifi(wlan=self.wlan, secrets=self.secrets, soc_vid=self.soc_vid).start())
        self.soc.attach(Memory(soc_vid=self.soc_vid).start())
        self.soc.attach(Gpio(pin_range=range(0, 35), soc_vid=self.soc_vid).start())
        self.soc.attach(Pwm(soc_vid=self.soc_vid).start())
        self.soc.attach(I2c(scl_pin=22, sda_pin=21, soc_vid=self.soc_vid).start())
        self.soc.attach(
            Ssd1306(i2c=self.soc.i2c, addr=0x3c, height=64, width=128, soc_vid=self.soc_vid, name="oled").start())
        self.soc.attach(SoftSPI(baudrate=100000, polarity=0, phase=0,
                                sck=self.soc.gpio.pin(18),
                                mosi=self.soc.gpio.pin(23),
                                miso=self.soc.gpio.pin(19)).start())
        #####################################################################################################
        LOG.info("initializing {{b}}RFID{{X}} reader")
        reader = MFRC522(spi=self.soc.spi, cs=self.soc.gpio.pin(5)).init()
        LOG.info("turning on antenna {{y}}{}{{X}}", reader)
        reader.antenna_on()
