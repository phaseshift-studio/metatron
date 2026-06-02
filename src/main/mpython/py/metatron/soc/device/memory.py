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
import gc
import micropython
import os
import time

from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import router

MEMORY_TID = "/soc/memory"


class Memory(Device):
    def __init__(self, soc_vid, name="memory"):
        Device.__init__(self, soc_vid, {}, MEMORY_TID, name)
        self.free = gc.mem_free()
        self.alloc = gc.mem_alloc()
        self.last_report = None

    def start(self) -> 'Memory':
        if self.soc_vid is not None:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"),
                               lambda vid, value: exec(f"{vid.name()}={value}"))
        return self

    def __getitem__(self, key):
        key = key if isinstance(key, str) else str(key)
        if key == "free":
            return gc.mem_free()
        elif key == "alloc":
            return gc.mem_alloc()
        else:
            LOG.warn("use direct virtual machine access for {{y}}{}", key)
            return None

    def __setitem__(self, key, value):
        key = key if isinstance(key, str) else str(key)
        router().write(self.soc_vid.extend(self.name).extend(key), value)
        exec(f"{key}={value}")

    def loop(self):
        if self.soc_vid is not None and (self.last_report is None or (time.time() - self.last_report) > 10):
            self.last_report = time.time()
            self.free = gc.mem_free()
            self.alloc = gc.mem_alloc()
            router().write(self.soc_vid.extend(self.name).extend("free"), self.free)
            router().write(self.soc_vid.extend(self.name).extend("alloc"), self.alloc)

    def broadcast(self, key):
        micropython.mem_info(False)
        if self.soc_vid is not None:
            stats = {
                "free": gc.mem_free(),
                "alloc": gc.mem_alloc(),
                "filesystem": os.statvfs("/")
            }
            router().write(self.soc_vid.extend(self.name), stats)
