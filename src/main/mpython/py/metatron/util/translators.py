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

import json

from metatron.furi import fURI
from metatron.obj import Int, Rec, Bool, Lst, Real, Str, Uri, Obj
from metatron.util.graphitty import LOG


class PythonTranslator:
    @staticmethod
    def from_obj(obj: Obj) -> Any:
        if obj is None:
            return None
        if isinstance(obj, Obj):
            pvm = obj.pvm
            if isinstance(pvm, dict):
                new_pvm = {}
                for k, v in pvm.items():
                    new_pvm[PythonTranslator.from_obj(k)] = PythonTranslator.from_obj(v)
                return new_pvm
            elif isinstance(pvm, list):
                new_pvm = []
                for i in pvm.items():
                    new_pvm.append(PythonTranslator.from_obj(i))
                return new_pvm
            else:
                return pvm
        else:
            return obj

    @staticmethod
    def to_obj(py_obj: Any) -> Obj:
        if py_obj is None: # or py_obj == "":
            return None
        if isinstance(py_obj, Obj):
            return py_obj
        if isinstance(py_obj, bool):
            return Bool(py_obj)
        if isinstance(py_obj, int):
            return Int(py_obj)
        if isinstance(py_obj, float):
            return Real(py_obj)
        if isinstance(py_obj, str):
            return Str(py_obj)
        if isinstance(py_obj, dict):
            return Rec(py_obj)
        if isinstance(py_obj, list):
            return Lst(py_obj)
        if isinstance(py_obj, fURI):
            return Uri(py_obj)
        if isinstance(py_obj, bytes):
            return Uri(py_obj)
        raise TypeError("unknown obj type: ", type(py_obj))


class JSONTranslator:

    @staticmethod
    def from_obj(obj: Obj) -> str:
        if obj is None:
            return ""
        return json.dumps(PythonTranslator.from_obj(obj) if isinstance(obj, Obj) else obj)

    @staticmethod
    def to_obj(json_str: str) -> Obj:
        if json_str is None or json_str == "":
            return None
        try:
            py_obj = json.loads(json_str)
        except Exception as e:
            LOG.error("{}: {}", e, json_str)
            return Str(json_str)
        return py_obj
