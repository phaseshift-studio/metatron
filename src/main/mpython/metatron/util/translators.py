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

import json

from metatron.obj import Int, Rec, Bool, Lst, Real, Str, Uri, Obj


class PythonTranslator:
    @staticmethod
    def fromObj(obj: Obj) -> Any:
        if obj is None:
            return None
        return obj.pvm

    @staticmethod
    def toObj(py_obj: Any) -> Obj:
        if py_obj is None:
            return None
        if isinstance(py_obj, Obj):
            return py_obj
        if isinstance(py_obj, bool):
            return Bool(py_obj, py_obj)
        if isinstance(py_obj, int):
            return Int(py_obj, py_obj)
        if isinstance(py_obj, float):
            return Real(py_obj, py_obj)
        if isinstance(py_obj, str):
            return Str(py_obj, py_obj)
        if isinstance(py_obj, dict):
            return Rec(py_obj, py_obj)
        if isinstance(py_obj, list):
            return Lst(py_obj, py_obj)
        if isinstance(py_obj, bytes):
            return Uri(py_obj, py_obj)
        raise TypeError("unknown obj type: ", type(py_obj))


class JSONTranslator:

    @staticmethod
    def fromObj(obj: Obj) -> str:
        return json.dumps(PythonTranslator.fromObj(obj))

    @staticmethod
    def toObj(json_str: str) -> Obj:
        py_obj = json.loads(json_str)
        return PythonTranslator.toObj(py_obj)
