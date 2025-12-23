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
import gc
import json
import machine
import ubinascii
from umqtt.simple import MQTTClient

from metatron.obj import *
from metatron.util.furi import fURI
from metatron.util.graphitty import LOG
from metatron.util.translators import JSONTranslator

class MqttSpace(Obj):
    def __init__(self, pattern: fURI, vid: fURI = None):
        Obj.__init__(self, f("/iot/space/mqtt"), vid)
        self.pattern = pattern
        self.cache = {}
        self.subscriptions = {}
        self.client = MQTTClient(ubinascii.hexlify(machine.unique_id()) if vid is None else str(self.vid),
                                 json.load(open("secrets.json"))['broker'])

    def start(self):
        self.client.set_callback(self._callback)
        self.client.connect()
        self.subscribe(self.pattern, lambda furi, obj: self.cache.__setitem__(furi, obj))

    def loop(self):
        self.client.check_msg()
        self._cache_flush()

    def _cache_flush(self):
        if len(self.cache) > 100:
            LOG.warn("flushing {{y}}{}{{X}} cache", self)
            self.cache = {}
            self.unsubscribe(str(self.pattern))
            self.subscribe(str(self.pattern), lambda furi, obj: self.cache.__setitem__(furi, obj))
            gc.collect()
            
    def read(self, vid) -> Obj:
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        if vid.has_pattern():
            for key, value in self.cache.items():
                if vid.matches(key):
                    return value
        return self.cache.get(vid)

    def subscribe(self, furi, func):
        self.client.subscribe(str(furi))
        self.subscriptions[furi] = func
        LOG.info("subscribed to {{y}}{}{{X}}", furi)

    def unsubscribe(self, furi):
        # self.client.(str(vid))
        self.subscriptions.pop(furi)
        LOG.info("unsubscribed to {{y}}{}{{X}}", furi)

    def write(self, vid, obj):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        if obj is None:
            self.client.publish(str(vid), "", True)
        else:
            obj = obj if isinstance(obj, Obj) else mach["translator"].to_obj(obj)
            if obj is not None:
                self.cache[vid] = obj
            elif vid in self.cache.keys():
                self.cache.pop(vid)
            self.client.publish(str(vid), JSONTranslator.from_obj(obj), True)

    def _callback(self, furi, obj):
        # LOG.debug("subscriptions: {}", self.subscriptions)
        furi2 = f(furi.decode())
        obj2 = JSONTranslator.to_obj(obj.decode())
        for pattern, func in self.subscriptions.items():
            if furi2.matches(pattern):
                #LOG.debug("using subscription {{y}}{}", pattern)
                func(furi2, obj2)

    def connect_esphome(self, merge, template: str = 'esphome.json'):
        profile = json.load(open(template)) | merge
        for k, v in profile.items():
            if v == 'XXX':
                raise ValueError("merged profile data must not contain XXX:", profile)
        self.client.publish("esphome/discover/" + profile['name'], json.dumps(profile), True)
        LOG.info("published esphome discovery: ", profile)

    def __repr__(self):
        return str(self.tid) + "::[" + self.client.server + "]" + (
            ("@" + str(self.vid)) if self.vid is not None else "")
