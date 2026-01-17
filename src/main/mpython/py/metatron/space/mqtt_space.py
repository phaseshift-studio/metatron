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
from metatron.furi import fURI
from metatron.util.graphitty import LOG
from metatron.util.mach import translator
from metatron.util.translators import JSONTranslator

MQTT_SPACE_TID = f("/iot/space/mqtt")

class MqttSpace(Obj):
    def __init__(self, pattern: fURI, vid: fURI = f("/sys/space/mqtt")):
        Obj.__init__(self, MQTT_SPACE_TID, vid)
        self.pattern = pattern
        self.cache = {}
        self.subscriptions = {}
        self.broker = json.load(open("secrets.json"))['broker']
        self.port = json.load(open("secrets.json")).get('port', 1883)
        self.client = MQTTClient(
            client_id=ubinascii.hexlify(machine.unique_id()) if self.vid is None else str(self.vid),
            server=self.broker,
            port=self.port,
            keepalive=60)
        
    def disconnect(self):
        self.client.disconnect()

    def start(self) -> 'MqttSpace':
        try:
            self.client.set_callback(self._callback)
            self.client.connect()
            LOG.info("connected to {{y}}{}{{X}} broker", self.client.server)
            self.subscribe(self.pattern, lambda furi, obj: self.cache.__setitem__(furi, obj) if obj is not None else self.cache.pop(furi) if furi in self.cache.keys() else None)      
        except Exception as e:
            LOG.error("unable to connect with {{y}}{}{{X}}: {}", self.broker, e)
        return self

    
    def loop(self):
        try:
            self.client.check_msg()
            self._cache_flush()
        except Exception as e:
            try:
                LOG.error("broker {{y}}{}{{X}} error: {}", self.broker, e)
                self.disconnect()
                self.start()
            except Exception as e2:
                LOG.error("unable to reconnect with {{y}}{}{{X}}: {}", self.broker, e2)
                raise e2
            
    def _cache_flush(self):
        if len(self.cache) > 100:
            LOG.warn("flushing {{y}}{}{{X}} cache", self)
            self.cache = {}
            self.unsubscribe(str(self.pattern))
            self.subscribe(self.pattern, lambda furi, obj: self.cache.__setitem__(furi, obj) if obj is not None else self.cache.pop(furi) if furi in self.cache.keys() else None)
            gc.collect()

    def read(self, vid) -> Obj:
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        if vid.has_pattern():
            result = {} if vid.send else []
            for key, value in self.cache.items():
                if key.matches(vid):
                    result.__setitem__(key,value) if vid.send else result.append(value)
            return None if 0 is len(result) else result
        return self.cache.get(vid)

    def subscribe(self, furi, func):
        furi = furi if isinstance(furi, fURI) else fURI(furi)
        self.client.check_msg()
        self.client.subscribe(str(furi))
        self.subscriptions[furi] = func
        LOG.info("subscribed to {{y}}{}{{X}}", furi)

    def unsubscribe(self, furi):
        # self.client.(str(vid))
        self.client.check_msg()
        if furi in self.subscriptions.keys():
            self.client.unsubscribe(str(furi))
            self.subscriptions.pop(furi)
            LOG.info("unsubscribed from {{y}}{}{{X}}", furi)

    def write(self, vid, obj):
        vid = vid if isinstance(vid, fURI) else fURI(vid)
        if obj is None:
            self.client.publish(str(vid), "", True)
            if vid in self.cache.keys():
                self.cache.pop(vid)
        else:
            obj = translator().to_obj(obj)
            self.cache[vid] = obj
            self.client.publish(str(vid), JSONTranslator.from_obj(obj), True)
        return obj

    def _callback(self, furi, obj):
        #print("subscriptions: {}", self.subscriptions)
        furi2 = f(furi.decode())
        obj2 = JSONTranslator.to_obj(obj.decode())
        if obj2 is None or obj is None:
            if furi2 in self.cache.keys():
                self.cache.pop(furi2)
        else:
            for pattern, func in self.subscriptions.items():
                if furi2.matches(pattern):
                    # LOG.debug("using subscription {{y}}{}", pattern)
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
    
    def __str__(self):
        return self.__repr__()