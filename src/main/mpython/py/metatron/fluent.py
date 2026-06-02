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

from metatron.obj import INST_TID
from metatron.obj import Inst, Code


class Fluent:
    def __init__(self):
        self.insts = []

    def _add_inst(self, opcode, *args) -> "Fluent":
        self.insts.extend([Inst(list(args), INST_TID.extend(opcode))])
        return self

    def start_(self, *args) -> "Fluent":
        return self._add_inst("start", *args)

    def split_(self, *args) -> "Fluent":
        return self._add_inst("split", *args)

    def merge_(self, *args) -> "Fluent":
        return self._add_inst("merge", *args)

    def plus_(self, *args) -> "Fluent":
        return self._add_inst("plus", *args)

    def minus_(self, *args) -> "Fluent":
        return self._add_inst("minus", *args)

    def mult_(self, *args) -> "Fluent":
        return self._add_inst("mult", *args)

    def from_(self, *args) -> "Fluent":
        return self._add_inst("from", *args)

    def to_(self, *args) -> "Fluent":
        return self._add_inst("to", *args)

    def id_(self, *args) -> "Fluent":
        return self._add_inst("id", *args)

    def print_(self, *args) -> "Fluent":
        return self._add_inst("print", *args)

    def _code(self) -> Code:
        return Code(self.insts)

    def __repr__(self):
        return Code(self.insts).__repr__()


def m(*args) -> Fluent:
    return Fluent().start_(*args)
