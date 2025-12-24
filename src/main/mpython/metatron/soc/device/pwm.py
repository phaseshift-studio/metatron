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
import time
from machine import PWM
from machine import Pin

from metatron.obj import Int
from metatron.soc.device.device import Device
from metatron.util.furi import f
from metatron.util.mach import mach
from metatron.util.graphitty import LOG

PWM_TID = f("/soc/pwm")


class Pwm(Device):
    def __init__(self, soc_vid, vid=None):
        Device.__init__(self, soc_vid, {}, PWM_TID, vid)
        has_id = soc_vid is not None
        if has_id:
            for i in range(0,35):
                mach['router'].write(soc_vid.extend('pwm').extend(str(i)),None)
        if has_id:
            mach['router'].get_space(soc_vid).subscribe(soc_vid.extend("pwm").extend("+"),
                                                        lambda key, value: Pwm._set_pwm(self, int(key.name()), value,False))

    def fade(self, key, start=0, end=1023, interval=16, sleep_ms=50):
        key = key if isinstance(key, Int) else Int(key)
        for duty_cycle in range(start, end, interval if start < end else -interval):
            Pwm._set_pwm(self,key,duty_cycle,False)
            time.sleep_ms(sleep_ms)
        self[key] = end

    @staticmethod
    def _set_pwm(device, pin, duty, do_log = True):
        duty = mach['translator'].to_obj(duty)
        if pin not in device.pvm.keys() or  device.pvm[pin] != duty:
            PWM(Pin(mach['translator'].from_obj(pin), Pin.OUT)).duty(mach['translator'].from_obj(duty))
            device.pvm[pin] = duty
            if do_log:
                LOG.debug("pwm {{y}}{}{{X}} set to {{b}}{}", pin, duty)

    def __getitem__(self, key):
        key = key if isinstance(key, Int) else Int(key)
        value = Int(PWM(Pin(key.pvm)).duty())
        self.pvm[key] = value
        return value

    def __setitem__(self, key, value):
        Pwm._set_pwm(self, key,value)
        if self.soc_vid is not None:
            mach['router'].write(self.soc_vid.extend('pwm').extend(str(key)), value)
