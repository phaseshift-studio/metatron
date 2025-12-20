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
import machine
import ubinascii
from umqtt.simple import MQTTClient

import metatron.util.mach as args
from metatron.obj import *
from metatron.util.furi import fURI
from metatron.util.graphitty import LOG
from metatron.util.translators import JSONTranslator


class MqttSpace:
    def __init__(self, pattern: fURI, vid: fURI = None):
        self.vid = vid
        self.tid = "/iot/space/mqtt"
        self.cache = {}
        self.pattern = pattern
        self.subscriptions = {}
        self.client = MQTTClient(ubinascii.hexlify(machine.unique_id()) if vid is None else str(self.vid),
                                 json.load(open("secrets.json"))['broker'])

    def start(self, callback):
        self.client.set_callback(callback)
        self.client.connect()
        self.client.subscribe(str(self.pattern))
        self.cache[self.pattern] = lambda f,o: self.cache.__setitem__(f,o)

    def loop(self):
        self.client.check_msg()
        self._cache_flush()

    def _cache_flush(self):
        if len(self.cache) > 100:
            LOG.warn("flushing {{y}}{}{{X}} cache", self)
            self.cache = {}
            self.client.unsubscribe(str(self.pattern))
            self.client.subscribe(str(self.pattern), 0)

    def read(self, vid) -> Obj:
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        if vid.has_pattern():
            for key, value in self.cache.items():
                if vid.matches(key):
                    return value
        return self.cache.get(vid)

    def subscribe(self, vid, f):
        self.client.subscribe(str(vid))
        self.subscriptions[vid] = f
        LOG.info("subscribed to {{y}}{}{{X}}", self.pattern)

    def write(self, vid, obj):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        obj = obj if isinstance(obj, Obj) else mach["translator"].toObj(obj)
        self.cache[vid] = obj
        self.client.publish(str(vid), JSONTranslator.fromObj(obj), True)

    def _callback(self, furi, obj):
        furi2 = f(furi.decode())
        obj2 = JSONTranslator.toObj(obj.decode())
        for pattern, func in self.subscriptions.items():
            if pattern.matches(furi2) or furi2.matches(pattern):
                # LOG.debug("using subscription {{y}}{}{{X}} for {{y}}{}", str(key), str(vid))
                func(furi2, obj2)

    def __repr__(self):
        return str(self.tid) + "[" + self.client.server + "]"
