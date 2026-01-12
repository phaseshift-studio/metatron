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

from metatron.furi import f
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG

SPI_TID = f("/soc/spi")


class Spi(Device):
    def __init__(self, sck_pin: int, sda_pin: int, mosi_pin: int, miso_pin: int, soc_vid, name="spi"):
        Device.__init__(self, soc_vid, machine.SoftSPI(baudrate=100000, polarity=0, phase=0, sck=sck_pin, mosi=mosi_pin, miso=miso_pin), SPI_TID, name)


def start(self) -> 'I2c':
    for device in self.pvm.scan():
        LOG.info("located i2c device at {{y}}{}", hex(device))
    return self
