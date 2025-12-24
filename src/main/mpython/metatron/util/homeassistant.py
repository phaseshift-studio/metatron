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

import uhome

import metatron.soc.soc
from metatron.soc.soc import SoC
from metatron.util.mach import mach


class HomeAssistant:
    def __init__(self, soc: SoC):
        self.soc = soc
        self.device = uhome.Device(soc.vid.name())
        self.entities= {}
        
    def connect(self):
        self.device.connect(mach['router'].get_space(self.soc.vid).client)

    def register_sensor(self, name:str, device, update, **kwargs):
        self.entities[device.tid.name()] = [uhome.Sensor(self.device, name, **kwargs),update]
        
    def register_number(self, name:str, device,update, **kwargs):
        self.entities[device.tid.name()] = [uhome.Number(self.device, name, **kwargs),update]
        
    def update(self,device=None):
        if device is None:
            self.device.discover_all()
        else:
            registration = self.entities[device.tid.name()]
            registration[0].publish(registration[1](device))
