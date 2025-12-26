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
from metatron.obj import Rec
from metatron.util.mach import router


class Device(Rec):
    def __init__(self, soc_vid, pvm: dict, tid, name:str):
        Rec.__init__(self, pvm, tid)
        self.soc_vid = soc_vid
        self.name = name
        #if self.soc_vid is not None:
        #    try:
        #        router().get_space(soc_vid).subscribe(soc_vid.extend(name).extend('get'),lambda s,k: self.broadcast(k))
        #    except Exception as e:
        #        pass

    def broadcast(self,key):
        if self.vid is not None:
            router().write(self.vid, self.pvm if key == "" else self.pvm[key])
