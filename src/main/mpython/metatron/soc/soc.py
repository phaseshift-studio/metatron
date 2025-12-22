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

import network
from machine import Pin

from metatron.obj import Rec, Int, Uri
from metatron.soc.device.gpio import GPIO
from metatron.soc.device.pwm import Pwm
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import mach


class SoC(Rec):
    def __init__(self, gpio_range: range, tid, vid=None):
        Rec.__init__(self, {"pwm":Pwm(vid), "gpio": GPIO(gpio_range, vid)}, tid, vid)
        if self.vid is not None:
            mach['router'].get_space(self.vid).subscribe(self.vid.extend("gpio/+"),
                                                         lambda f, o: Pin(int(f.name()), Pin.OUT).value(
                                                             o if isinstance(o, int) else o.pvm))
            mach['router'].write(self.vid.extend('sensor/wifi_signal/state'), Int(network.WLAN().status('rssi')))
            mach['router'].write(self.vid.extend('status'), "online")
