#  Metatron: A Distributed Computing Language and Virtual Machine
#  Copyright (C) 2025- PhaseShift Studio, LLC
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
import asyncio
import websockets
import logging
import re

logger = logging.getLogger(__name__)


async def submit(ws, code):
    async with ws as websocket:
        await websocket.send(code)
        result = await websocket.recv()
        return result


class Mytron:
    def __init__(self, host: str = "ws://127.0.0.1:8999"):
        self.host = host
        logger.info(f"connecting to {self.host}")
        self.ws = websockets.connect(self.host)
        logger.info(f"connected to {self.host}")

    def exec(self, code: str) -> list:
        doc_call: str = f"\"\"\"{code}\"\"\"./mtron/web/inst/doc()"
        print(f"send: {doc_call}")
        future = asyncio.get_event_loop().run_until_complete(submit(self.ws, f"{doc_call}"))
        result = str(future, 'utf-8').strip()
        result = result.removeprefix("</m/str>::")
        result = result[1:-1]
        print(f"recv: {result}")
        result2 = ""
        for a in str(result).split(sep="%%%"):
            a = a.strip()
            if a != "noobj" and a != "":
                result2 = result2 + f"==>{a}\n"
        return result2[0:-1]

    def close(self):
        logger.info(f"closing websocket connection to {self.host}")
