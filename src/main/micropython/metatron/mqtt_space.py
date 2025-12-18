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

import machine
import ubinascii
import umqtt.simple as MQTTClient
from simple import MQTTClient

from metatron.obj import Obj
from metatron.util.furi import fURI
from metatron.util.graphitty import LOG
from metatron.util.translators import JSONTranslator
from secrets import secrets


class MqttSpace:
    def __init__(self, pattern: fURI, vid: fURI = None):
        self.vid = vid
        self.tid = "/iot/space/mqtt"
        self.cache = {}
        self.pattern = pattern
        self.client = MQTTClient(ubinascii.hexlify(machine.unique_id()) if vid is None else str(self.vid),
                                 secrets['broker'])

    def start(self, callback):
        self.client.set_callback(callback)
        self.client.connect()
        self.client.subscribe(str(self.pattern), 0)
        LOG.info("subscribed to {{y}}{}{{X}}", self.pattern)

    def loop(self):
        self.client.check_msg()
        self._cache_flush()

    def _cache_flush(self):
        if len(self.cache) > 100:
            LOG.warn("flushing {{y}}{}{{X}} cache", self)
            self.cache = {}
            self.client.unsubscribe(str(self.pattern))
            self.client.subscribe(str(self.pattern), 0)

    def read(self, vid: fURI) -> Obj:
        return self.cache.get(vid)

    def write(self, vid: fURI, obj: Obj):
        self.cache[vid] = obj
        self.client.publish(str(vid), JSONTranslator.write(obj), True)

    def _callback(self, furi, obj):
        vid = fURI(furi.decode())
        self.cache[vid] = JSONTranslator.read(obj.decode())

    def __repr__(self):
        return str(self.tid) + "[" + secrets['broker'] + "]"
