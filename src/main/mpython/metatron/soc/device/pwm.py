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
from machine import PWM
from machine import Pin

import time
from metatron.obj import Rec, Int
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import mach

PWM_TID = f("/soc/pwm")


class Pwm(Rec):
    def __init__(self, soc_vid, vid=None):
        self.soc_vid = soc_vid
        pins = {}
        LOG.info("pwm loaded")
        Rec.__init__(self, pins, PWM_TID, vid)

    def fade(self, key, start=0,end=1023, interval=16, sleep_ms=50):
        key = key if isinstance(key, Int) else Int(key)
        for duty_cycle in range(start, end, interval if start < end else -interval):
            self[key] = duty_cycle
            time.sleep_ms(sleep_ms)
        self[key] = end

    def __getitem__(self, key):
        key = key if isinstance(key, Int) else Int(key)
        value = Int(PWM(Pin(key.pvm)).duty())
        self.pvm[key] = value
        return value

    def __setitem__(self, key, value):
        value = value if isinstance(value, Int) else Int(value)
        PWM(Pin(key if isinstance(key, int) else key.pvm, Pin.OUT)).duty(value.pvm)
        self.pvm[key] = value
        if self.soc_vid is not None:
            mach['router'].write(self.soc_vid.extend('pwm').extend(str(key)), value)
    