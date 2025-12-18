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

from metatron.util.furi import f
from metatron.util.furi import fURI

BOOL_TID = f("/m/bool")
INT_TID = f("/m/int")
REAL_TID = f("/m/real")
STR_TID = f("/m/str")
URI_TID = f("/m/uri")
LST_TID = f("/m/lst")
REC_TID = f("/m/rec")


class Obj:
    def __init__(self, pvm, tid: fURI, vid: fURI = None):
        self.pvm = pvm
        self.tid = tid
        self.vid = vid

    def __repr__(self):
        return str(self.pvm)

    def encode(self) -> str:
        return self.__repr__()

    def __str__(self):
        return self.__repr__()


class Bool(Obj):
    def __init__(self, pvm: bool, tid: fURI = BOOL_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)

    def __repr__(self) -> str:
        return "true" if self.pvm else "false"


class Int(Obj):
    def __init__(self, pvm: int, tid: fURI = INT_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)


class Real(Obj):
    def __init__(self, pvm: float, tid: fURI = REAL_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)


class Str(Obj):
    def __init__(self, pvm: str, tid: fURI = STR_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)


class Uri(Obj):
    def __init__(self, pvm: fURI, tid: fURI = URI_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)


class Lst(Obj):
    def __init__(self, pvm: list, tid: fURI = LST_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)

    def __getitem__(self, key):
        return self.pvm[key]

    def __setitem__(self, key, value):
        self.pvm[key] = value


class Rec(Obj):
    def __init__(self, pvm: dict, tid: fURI = REC_TID, vid: fURI = None):
        Obj.__init__(self, pvm, tid, vid)

    def __getitem__(self, key):
        return self.pvm[key]

    def __setitem__(self, key, value):
        self.pvm[key] = value

    # def encode(self) -> str:
    #    if len(self.pvm) == 0:
    #        return "[=>]"
    #    ret = "["
    #    for key, value in self.pvm.items():
    #        ret += f"{key} => {value},"
    #    ret = ret[:-1]
    #    ret += "]"
    #    return ret
