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

import metatron.util.graphitty
from metatron.obj import Rec
from metatron.soc.device.device import Device
from metatron.soc.device.wifi import Wifi
from metatron.util.furi import f
from metatron.util.graphitty import LOG, string, strip
from metatron.util.mach import router


class SoC(Rec):
    def __init__(self, tid, vid=None):
        Rec.__init__(self, {}, tid, vid)
        self.loopers = []
        metatron.util.graphitty.log_behavior = lambda level, s, *args: router().write(self.vid.extend('log'),
                                                                                      f"[{level}] {strip(string(s, *args))}")
        self['status'] = 'online'
        if self.vid is not None:
            router().get_space(self.vid).subscribe(self.vid.extend('status'),
                                                   lambda key, value: machine.reset() if value == 'offline' else "")

    def attach(self, device: Device):
        key = device.tid.name()
        if key in self.__dict__:
            LOG.warn("overriding already existing {{y}}{}{{X}} at {{y}}{}{{X}}", device.tid, device.tid.name())
        setattr(self, key, device)
        if hasattr(device, "loop"):
            self.loopers.append(device)
        LOG.info("device {{y}}{}{{g}}::{{m}}T{{X}} loaded as {{b}}{}", device.tid, device.name)

    def loop(self):
        router().loop()
        for looper in self.loopers:
            looper.loop()

class Architecture:

    def __init__(self, secrets: dict):
        self.secrets = secrets
        self.soc = None
        self.wlan = Wifi.connect(secrets['ssid'], secrets['password'], secrets['host'])
        self.soc_vid = f(secrets['host'])

    def loop(self):
        for looper in self.loopers:
            looper.loop()

    def __repr__(self):
        return self.soc.__repr__()

    def __srt__(self):
        return self.__repr__()

