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

from metatron.obj import Rec, Int
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import mach


class SoC(Rec):
    def __init__(self, tid, vid=None):
        Rec.__init__(self, {}, tid, vid)
        if self.vid is not None:
            mach['router'].write(self.vid.extend('sensor/wifi_signal/state'), Int(network.WLAN().status('rssi')))
            mach['router'].write(self.vid.extend('status'), "online")

    def attach(self, device: Device):
        key = device.tid.name()
        if key in self.__dict__:
            LOG.warn("overriding already existing {{y}}{}{{X}} at {{y}}{}{{X}}", device.tid, device.tid.name())
        setattr(self, key, device)
        LOG.info("device {{y}}{}{{X}} loaded", device.tid)
