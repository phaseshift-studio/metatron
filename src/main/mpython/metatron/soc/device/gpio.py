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

from metatron.obj import Int
from metatron.soc.device.device import Device
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import mach
import machine

GPIO_TID = f("/soc/gpio")

class Gpio(Device):
    def __init__(self, pin_range: range, soc_vid, vid=None):
        has_id = soc_vid is not None    
        pins = {}
        #for i in pin_range:
           # try:
                #pins[i] = Pin(i).value()
                #if has_id:
                #    mach['router'].write(soc_vid.extend('gpio').extend(str(i)), Int(pins[i]))
           # except Exception as e:
           #     LOG.warn("ignoring unsupported pin {}", i)
        Device.__init__(self, soc_vid, pins, GPIO_TID, vid)
        if has_id:
            mach['router'].get_space(soc_vid).subscribe(soc_vid.extend("gpio").extend("+"),
                                                        lambda key, value: Gpio._set_gpio(self, int(key.name()), value))



    @staticmethod
    def _set_gpio(device, pin, value, do_log = True):
        value = mach['translator'].to_obj(value)
        #if pin not in device.pvm.keys() or device.pvm[pin] != value:
        Pin(mach['translator'].from_obj(pin), Pin.OUT).value(mach['translator'].from_obj(value))
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
            mach['router'].write(self.soc_vid.extend('gpio').extend(str(key)), value)
