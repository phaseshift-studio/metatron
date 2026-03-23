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

from metatron.furi import f, fURI
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import router

MPY_TID = f("/soc/mpy")


class MPy(Device):
    def __init__(self, soc_vid, name="mpy"):
        Device.__init__(self, soc_vid, {}, MPY_TID, name)

    def start(self) -> 'MPy':
        if self.soc_vid is not None:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"), lambda vid, value: MPy.evaluate(vid, str(value)))
        return self

    @staticmethod
    def evaluate(source, expr: str):
        sourcef = source if isinstance(source, fURI) else f(str(source))
        router().write(sourcef.extend("in"), expr)
        LOG.info("evaluating {{y}}{}{{g}} => '{{X}}{}{{g}}'", str(sourcef.extend("in")), expr)
        result = eval(expr)
        LOG.debug("result {{y}}{}{{g}} => {{X}}{}", sourcef, result)
        router().write(sourcef.extend("out"), result)
