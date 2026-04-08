#!/usr/bin/env python3

import asyncio
import websockets
import logging
import re
from argparse import ArgumentParser

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
handler = logging.StreamHandler()
formatter = logging.Formatter('%(message)s')
handler.setFormatter(formatter)

# Add handler to logger
logger.addHandler(handler)



async def submit(ws, code):
    async with ws as websocket:
        await websocket.send(code)
        result = await asyncio.wait_for(websocket.recv(), timeout=2000)
        return result


class mtronWebSocketClient:
    def __init__(self, host: str = "ws://127.0.0.1:8999"):
        self.host = host
        logger.info(f"connecting to {self.host}")
        self.ws = websockets.connect(self.host)
        logger.info(f"connected to {self.host}")

    def eval(self, code: str) -> list:
        doc_call: str = f"\"\"\"{code}\"\"\"./m/web/inst/doc()"
        logger.info(f"send: {doc_call}")
        future =asyncio.run(submit(self.ws, f"{doc_call}"))
        result = str(future, 'utf-8').strip()
        result = result.removeprefix("</m/str>::")[1:-1] # clip off type prefix and ' '.
        logger.info(f"number of lines received: {len(result)}")
        result2 = ""
        for a in str(result).split(sep="%%%"):
            a = a.strip()
            if a != "noobj" and a != "":
                result2 = result2 + f"==>{a}\n"
        return result2[0:-1]

    def close(self):
        logger.info(f"closing websocket connection to {self.host}")
        #self.ws.close()


def main(argv=None):
    parser = ArgumentParser(description="evaluate mtron expression over websocket")
    parser.add_argument("--host", default="ws://127.0.0.1:8999", help="the metatron websocket endpoint")
    parser.add_argument("-c", "--code", help="the mtron expression to evaluate")
    args = parser.parse_args(argv)
    client = mtronWebSocketClient(args.host)
    result = client.eval(args.code)
    #client.close()
    logger.info(result)
    return result

if __name__ == "__main__":
    main()