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

from machine import Pin

from metatron.obj import Rec, Int
from metatron.util.args import args
from metatron.util.graphitty import LOG

class SoC(Rec):
    def __init__(self, pin_range: range, tid, vid=None):
        pins = {}
        for i in pin_range:
            try:
                pins[i] = Pin(i).value()
            except Exception as e:
                LOG.warn("ignoring unsupported pin {}", i)
        Rec.__init__(self, pins, tid, vid)
        if vid is not None:
            args['router'].get_space(vid).subscribe(vid.extend("+"), lambda f, o: Pin(int(f.name()),Pin.OUT).value(o if isinstance(o,int) else o.pvm))
        

    def __getitem__(self, key):
        key = key if isinstance(key, Int) else Int(key)
        value = Int(Pin(key.pvm).value())
        self.pvm[key] = value
        return value
    
    
    def __setitem__(self, key, value):
        value = value if isinstance(value, Int) else Int(value)
        Pin(key if isinstance(key, int) else key.pvm, Pin.OUT).value(value.pvm)
        self.pvm[key] = value
        if self.vid is not None:
            args['router'].write(self.vid.extend(str(key)),value)
