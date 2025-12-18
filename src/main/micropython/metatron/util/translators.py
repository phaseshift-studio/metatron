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

from metatron.obj import Obj, Bool, Int, Real, Str, Uri, Rec, Lst
from metatron.util.furi import f


class PythonTranslator:
    @staticmethod
    def write(obj: Obj) -> Any:
        return obj.pvm

    @staticmethod
    def read(py_obj: Any) -> Obj:
        if (py_obj is None):
            return None
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
        return Obj(py_obj, f("/m/obj"))


class JSONTranslator:

    @staticmethod
    def write(obj: Obj) -> str:
        return json.dumps(PythonTranslator.write(obj))

    @staticmethod
    def read(json_str: str) -> Obj:
        py_obj = json.loads(json_str)
        return PythonTranslator.read(py_obj)
