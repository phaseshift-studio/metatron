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

import _thread
import uasyncio as asyncio

from metatron.furi import fURI, f


class Router:

    def __init__(self):
        self.spaces = {}

    def add_space(self, space):
        self.spaces[space.pattern] = space

    def get_space(self, vid):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        for pattern, space in self.spaces.items():
            if vid.bimatches(pattern):
                return space
        raise Exception(f"no registered space supports {vid}")

    def read(self, vid):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        for pattern, space in self.spaces.items():
            if vid.matches(pattern):
                return space.read(vid)
        raise Exception(f"no registered space supports {vid}")

    def write(self, vid, obj):
        vid = vid if isinstance(vid, fURI) else f(str(vid))
        for pattern, space in self.spaces.items():
            if vid.matches(pattern):
                return space.write(vid, obj)
        raise Exception(f"no registered space supports {vid}")

    def subscribe(self, vid, func):
        vid = vid if isinstance(vid, fURI) else f(str(vid))
        for pattern, space in self.spaces.items():
            if vid.matches(pattern):
                return space.subscribe(vid, func)
        raise Exception(f"no registered space supports {vid}")

    def unsubscribe(self, vid):
        vid = vid if isinstance(vid, fURI) else f(str(vid))
        for pattern, space in self.spaces.items():
            if vid.matches(pattern):
                space.unsubscribe(vid)
                return
        raise Exception(f"no registered space supports {vid}")

    def loop(self):
        for space in self.spaces.values():
            if hasattr(space, "loop"):
                space.loop()

    def __repr__(self):
        return "router::[spaces:" + str(len(self.spaces)) + "]"

    def __str__(self):
        return self.__repr__()
