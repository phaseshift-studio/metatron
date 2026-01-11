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
from metatron.util.mach import router

I2C_TID = f("/soc/i2c")


class I2c(Device):
    def __init__(self, scl_pin: int, sda_pin: int, soc_vid, name="i2c"):
        Device.__init__(self, soc_vid, machine.I2C(sda=machine.Pin(sda_pin), scl=machine.Pin(scl_pin)), I2C_TID, name)

    def start(self) -> 'I2c':
        devices = self.pvm.scan()
        for device in devices:
            LOG.info("located i2c device at {{y}}{}", hex(device))
        return self
