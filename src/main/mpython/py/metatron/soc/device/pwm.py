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

from metatron.furi import f
from metatron.obj import Int, jnt
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import router, translator

PWM_TID = f("/soc/pwm")


class Pwm(Device):
    def __init__(self, soc_vid, name="pwm"):
        Device.__init__(self, soc_vid, {}, PWM_TID, name)

    def start(self) -> 'Pwm':
        has_id = self.soc_vid is not None
        #if has_id:
        #    for i in range(0, 35):
        #        router().write(self.soc_vid.extend(self.name).extend(str(i)), None)
        if has_id:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"), lambda vid, value: Pwm._set_pwm(self, int(vid.name()), value, False))
        return self

    def fade(self, pin, start=0, end=1023, interval=16, sleep_ms=50):
        pin = pin if isinstance(pin, Int) else Int(pin)
        for duty_cycle in range(start, end, interval if start < end else -interval):
            Pwm._set_pwm(self, pin, duty_cycle, False)
            time.sleep_ms(sleep_ms)
        self[pin] = end

    @staticmethod
    def _set_pwm(device, pin, duty, do_log=True):
        duty = jnt(0) if duty is None else translator().to_obj(duty)
        if pin not in device.pvm.keys() or device.pvm[pin] != duty:
            PWM(Pin(translator().from_obj(pin), Pin.OUT)).duty(translator().from_obj(duty))
            device.pvm[pin] = duty
            if do_log:
                LOG.debug("pwm {{y}}{}{{X}} set to {{b}}{}", pin, duty)

    def __getitem__(self, key):
        key = key if isinstance(key, Int) else Int(key)
        value = Int(PWM(Pin(key.pvm)).duty())
        self.pvm[key] = value
        return value

    def __setitem__(self, key, value):
        Pwm._set_pwm(self, key, value)
        if self.soc_vid is not None:
            router().write(self.soc_vid.extend(self.name).extend(str(key)), value)
