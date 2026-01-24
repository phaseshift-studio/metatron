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
import os
import uasyncio as asyncio

from lib.ws import AsyncWebsocketClient as WSClient
from metatron.obj import *
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG

MTRON_SPACE = f("/iot/space/mtron")


class mtronSpace(Obj):
    def __init__(self, pattern: fURI, vid = f("/sys/space/mtron")):
        Obj.__init__(self,{}, MTRON_SPACE)
        self.pattern = pattern
        self.cache = {}
        self.subscriptions = {}
        self.client = WSClient(ms_delay_for_read=5)

    def start(self) -> 'mtronSpace':
        try:
            self.client.handshake(self.pattern.host())
            LOG.info("connected to {{y}}{}{{X}} mtron space", self.pattern.host())
        # self.subscribe(self.pattern,
        #                lambda furi, obj: self.cache.__setitem__(furi, obj) if obj is not None else self.cache.pop(
        #                    furi) if furi in self.cache.keys() else None)
        except Exception as e:
            LOG.error("unable to connect with {{y}}{}{{X}} mtron space: {}", self.pattern.host(), e)
        return self

    def read(self, vid) -> Obj:
        self.client.send(f("*{}".format(vid)))
        fin, opcode, data = self.client.read_frame()
        if data:
            return data
        else:
            return None;

    def subscribe(self, furi, func):
        furi = furi if isinstance(furi, fURI) else fURI(furi)
        self.subscriptions[furi] = func
        LOG.info("subscribed to {{y}}{}{{X}}", furi)

    def unsubscribe(self, furi):
        if furi in self.subscriptions.keys():
            self.subscriptions.pop(furi)
            LOG.info("unsubscribed from {{y}}{}{{X}}", furi)

    def write(self, vid, obj):
        self.client.send(f("{} -> {}".format(vid, obj)))
        #fin, opcode, data = self.client.read_frame()
        #LOG.info("post-write {}", data)
        return obj

    def close(self):
        self.client.close()

    def __repr__(self):
        return str(self.tid) + "::[" + os + "]" + (
            ("@" + str(self.vid)) if self.vid is not None else "")

    def __str__(self):
        return self.__repr__()
