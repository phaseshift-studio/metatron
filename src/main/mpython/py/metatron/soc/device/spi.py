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
import machine
from machine import Pin

from metatron.furi import f
from metatron.soc.device.device import Device

SPI_TID = f("/soc/spi")


class Spi(Device):
    def __init__(self, sck_pin: int, mosi_pin: int, miso_pin: int, soc_vid, name: str = "spi"):
        Device.__init__(self, soc_vid, {}, SPI_TID, name)
        #Pin(miso_pin, Pin.IN, Pin.PULL_DOWN)
        self.pvm = machine.SoftSPI(baudrate=100000, polarity=0, phase=0, sck=sck_pin, mosi=mosi_pin, miso=miso_pin)

    def start(self):
        Device.start(self)
        self.pvm.init()
        return self