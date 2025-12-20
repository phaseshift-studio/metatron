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

import metatron.util.mach as args


class fURI:
    def __init__(self, uri: str):
        self.sstart = uri.startswith("/")
        self.send = uri.endswith("/")
        self.path = uri.split("/")

    def extend(self, segment: str) -> "fURI":
        new_path = self.path + [segment]
        new_furi = fURI("")
        new_furi.path = new_path
        new_furi.sstart = self.sstart
        new_furi.send = segment.endswith("/")
        return new_furi

    def matches(self, other: "fURI") -> bool:
        other_len = len(other.path)
        for i in range(len(self.path)):
            if other_len < i + 1:
                return False
            if other.path[i] == "#":
                return True
            if other.path[i] == "+":
                continue
            if self.path[i] != other.path[i]:
                return False
        return True

    def has_pattern(self) -> bool:
        return "#" in self.path or "+" in self.path

    def name(self) -> str:
        if len(self.path) == 0:
            return ""
        return self.path[-1]

    def __str__(self):
        return "/".join(self.path)

    def __repr__(self):
        return self.__str__()

    def __eq__(self, other):
        return self.__str__() == other.__str__()

    def __hash__(self):
        return hash(self.__str__())

    def __gt__(self, other):
        return args.mach["router"].write(self, other)

    def __invert__(self):
        return args.mach["router"].read(self)


def f(furi: str) -> fURI:
    return fURI(furi)
