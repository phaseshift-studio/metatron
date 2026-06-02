#  metatron: a distributed virtual machine and language
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
from lib import uhome
from metatron.furi import fURI, f
from metatron.obj import Obj
from metatron.util.graphitty import LOG
from metatron.util.mach import router


class HomeAssistantSpace(Obj):
    def __init__(self, pattern: fURI, vid: fURI = None):
        Obj.__init__(self, f("/iot/space/ha"), vid)
        self.device = uhome.Device(pattern.name(), discovery_prefix=pattern)
        LOG.info("connecting to {{b}}HomeAssistant{{X}} via {{c}}MQTT{{X}}")
        self.device.connect(router().get_space(vid).client)

    def start(self) -> 'HomeAssistantSpace':
        return self

    def loop(self):
        self.device.loop()
