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


import _thread
import asyncio
from lib.microdot.microdot import Microdot, Request, Response
from metatron.util.graphitty import LOG
from lib.microdot.websocket import with_websocket

class MServer:
    def __init__(self):
        self.server = Microdot()
        
    def start(self):
        LOG.info("starting mserver")
        
        @self.server.route("/boot")
        async def index(request):
            return {"metatron":1.0}
            #return send_file('boot.py')
        
        @self.server.route('/test')
        @with_websocket
        async def echo(ws):
            try:
                LOG.info("echo connection from {}",ws)
                #while True:
                #    message = await ws.receive()
                #    await ws.send(message)
            except Exception as e:
                LOG.error("{}",e)
                
        _thread.start_new_thread(self.server.run,())


