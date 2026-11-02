#  metatron: a distributed virtual machine and language
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
from machine import Pin

from max6675 import MAX6675
from metatron.soc.device.device import Device
from metatron.soc.device.gpio import Gpio


class mMAX6675(Device):
    def __init__(self, pvm: dict[dict(sck=Gpio, cs=Gpio, so=Gpio)], soc_vid, tid, name: str):
        Device.__init__(self, soc_vid, pvm, tid, name)
        self.sck = Pin(pvm["sck"].pvm, Pin.OUT)
        self.cs = Pin(pvm["cs"].pvm, Pin.OUT)
        self.so = Pin(pvm["so"].pvm, Pin.IN)
        self.sensor = MAX6675(self.sck, self.cs, self.so)

    def read_temperature(self):
        return self.sensor.read()
