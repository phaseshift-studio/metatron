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
from metatron.util.mach import mach

BYTES_TID = f("/m/bytes")
BOOL_TID = f("/m/bool")
INT_TID = f("/m/int")
REAL_TID = f("/m/real")
STR_TID = f("/m/str")
URI_TID = f("/m/uri")
LST_TID = f("/m/lst")
REC_TID = f("/m/rec")
INST_TID = f("/m/inst")
CODE_TID = f("/m/code")

BASE_TYPES = [BYTES_TID, BOOL_TID, INT_TID, REAL_TID, STR_TID, URI_TID, LST_TID, REC_TID]


class Obj:
    def __init__(self, pvm, tid, vid=None):
        self.pvm = pvm
        self.tid = tid if isinstance(tid, fURI) else f(tid)
        self.vid = None
        if vid is not None:
            self.vid = vid if isinstance(vid, fURI) else f(vid)
            mach['router'].write(self.vid, self)

    def pvm(self, pvm):
        return self.clone(pvm=pvm)

    def tid(self, tid):
        return self.clone(tid=tid)

    def vid(self, vid):
        return self.clone(vid=vid)

    def clone(self, pvm=None, tid=None, vid=None):
        return self.__class__(pvm if pvm is not None else self.pvm,
                              tid if tid is not None else self.tid,
                              vid if vid is not None else self.vid)

    def __repr__(self):
        ret = "" + str(self.pvm)
        if self.tid not in BASE_TYPES:
            ret = str(self.tid) + "::" + ret
        if self.vid is not None:
            ret = ret + "@" + str(self.vid)
        return ret

    def encode(self) -> str:
        return self.__repr__()

    def __str__(self):
        return self.__repr__()

    def __hash__(self):
        return hash(self.pvm)

    def __eq__(self, other):
        return isinstance(other, Obj) and self.tid == other.tid and self.pvm == other.pvm


class Bytes(Obj):
    def __init__(self, pvm: bytes, tid=BYTES_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Bool(Obj):
    def __init__(self, pvm: bool, tid=BOOL_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Int(Obj):
    def __init__(self, pvm: int, tid=INT_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Real(Obj):
    def __init__(self, pvm: float, tid=REAL_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Str(Obj):
    def __init__(self, pvm: str, tid=STR_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Uri(Obj):
    def __init__(self, pvm: fURI, tid=URI_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)


class Lst(Obj):
    def __init__(self, pvm: list, tid=LST_TID, vid=None):
        obj_list = []
        for item in pvm:
            obj_list.append(mach["translator"].to_obj(item))
        Obj.__init__(self, obj_list, tid, vid)

    def __getitem__(self, key):
        return self.pvm[key]

    def __setitem__(self, key, value):
        self.pvm[key] = value


class Rec(Obj):
    def __init__(self, pvm: dict, tid=REC_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)

    def __getitem__(self, key):
        key = mach["translator"].to_obj(key)
        return self.pvm[key]

    def __setitem__(self, key, value):
        key = mach["translator"].to_obj(key)
        self.pvm[key] = value
        if self.vid is not None:
            mach['router'].write(self.vid.extend(str(key)), mach['translator'].to_obj(value))

    # def encode(self) -> str:
    #    if len(self.pvm) == 0:
    #        return "[=>]"
    #    ret = "["
    #    for key, value in self.pvm.items():
    #        ret += f"{key} => {value},"
    #    ret = ret[:-1]
    #    ret += "]"
    #    return ret


class Objs(Obj):
    def __init__(self, pvm: list, vid=None):
        Obj.__init__(self, pvm, pvm[0].tid, vid)


class Inst(Obj):
    def __init__(self, pvm: list, tid, vid=None):
        Obj.__init__(self, pvm, tid, vid)

    def __repr__(self):
        a = ""
        for item in self.pvm:
            a += f"{item},"
        return f"{self.tid.name() if str(self.tid).startswith("/m/inst") else self.tid}({a[:-1]})"


class Code(Obj):
    def __init__(self, pvm: list, tid=CODE_TID, vid=None):
        Obj.__init__(self, pvm, tid, vid)

    def __repr__(self):
        if len(self.pvm) == 0:
            return ""
        ret = ""
        for inst in self.pvm:
            ret += f"{inst}."
        return ret[:-1]


def dytes(b):
    return Bytes(b)


def dool(b):
    return Bool(b)


def jnt(i):
    return Int(i)


def real(r):
    return Real(r)


def ztr(s):
    return Str(s)


def uri(u):
    return Uri(u)


def lst(*obj):
    return Lst(list(obj))


def rec(**obj):
    return Rec(obj)


def objs(*obj):
    return Objs(list(obj))


def inst(opcode, *obj):
    return Inst(list(obj), opcode)
