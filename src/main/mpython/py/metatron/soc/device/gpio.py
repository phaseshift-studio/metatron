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
from machine import Pin

from metatron.furi import f
from metatron.obj import Int, jnt
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import translator, router

GPIO_TID = f("/soc/gpio")


class Gpio(Device):
    def __init__(self, pin_range: range, soc_vid, name="gpio"):
        Device.__init__(self, soc_vid, {}, GPIO_TID, name)

    def start(self) -> 'Gpio':
        if self.soc_vid is not None:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"),
                                                       lambda vid, value: Gpio._set_gpio(self, int(vid.name()), value,
                                                                                         False))
        return self

    @staticmethod
    def _set_gpio(device, pin, value, do_log=True):
        value = jnt(0) if value is None else translator().to_obj(value)
        # if pin not in device.pvm.keys() or device.pvm[pin] != value:
        Pin(translator().from_obj(pin), Pin.OUT).value(translator().from_obj(value))
        device.pvm[pin] = value
        if do_log:
            LOG.debug("gpio {{y}}{}{{X}} set to {{b}}{}", pin, value)

    def __getitem__(self, key):
        key = key if isinstance(key, Int) else Int(key)
        value = Int(Pin(key.pvm).value())
        self.pvm[key] = value
        return value

    def __setitem__(self, key, value):
        Gpio._set_gpio(self, key, value)
        if self.soc_vid is not None:
            router().write(self.soc_vid.extend(self.name).extend(str(key)), value)
