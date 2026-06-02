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
import machine
import os

from metatron.obj import *
from metatron.util.graphitty import LOG

FILE_SPACE_TID = f("/sys/space/file")

class FileSpace(Obj):
    def __init__(self, pattern: fURI, rewrite: list,vid: fURI = f("/sys/space/file")):
        Obj.__init__(self, FILE_SPACE_TID, vid)
        self.pattern = pattern
        self.rewrite = rewrite
        self.cache = {}
        self.subscriptions = {}

    def start(self) -> 'FileSpace':
        try:
            LOG.info("connected to {{y}}{}{{X}} file system", "local")
            self.subscribe(self.pattern,
                           lambda furi, obj: self.cache.__setitem__(furi, obj) if obj is not None else self.cache.pop(
                               furi) if furi in self.cache.keys() else None)
        except Exception as e:
            LOG.error("unable to connect with {{y}}{}{{X}} file system: {}", "local", e)
        return self

    def read(self, pattern) -> Obj:
        pattern = pattern if isinstance(pattern, fURI) else fURI(pattern)
        pattern = f(str(pattern).replace(self.rewrite[0], self.rewrite[1]))
        result = {} if pattern.send else []
        running = ""
        for segment in pattern.path:
            for file in os.listdir(running) if segment == "#" or segment == "+" else [segment] :
                if f(file).matches(segment) or f(file).matches(pattern):
                    result.__setitem__(running, file) if pattern.send else result.append(file)
            running += "/" + segment
        return None if 0 is len(result) else result

    def subscribe(self, furi, func):
        furi = furi if isinstance(furi, fURI) else fURI(furi)
        self.subscriptions[furi] = func
        LOG.info("subscribed to {{y}}{}{{X}}", furi)

    def unsubscribe(self, furi):
        if furi in self.subscriptions.keys():
            self.subscriptions.pop(furi)
            LOG.info("unsubscribed from {{y}}{}{{X}}", furi)

    def write(self, vid, obj):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        LOG.warn("todo")
        return obj

    def __repr__(self):
        return str(self.tid) + "::[" + os + "]" + (
            ("@" + str(self.vid)) if self.vid is not None else "")

    def __str__(self):
        return self.__repr__()
